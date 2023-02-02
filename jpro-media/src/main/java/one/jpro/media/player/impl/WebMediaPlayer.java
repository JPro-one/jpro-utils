package one.jpro.media.player.impl;

import com.jpro.webapi.JSVariable;
import com.jpro.webapi.WebAPI;
import com.jpro.webapi.WebCallback;
import javafx.beans.property.*;
import javafx.event.Event;
import javafx.scene.media.MediaPlayer.Status;
import javafx.util.Duration;
import one.jpro.media.MediaSource;
import one.jpro.media.WebMediaEngine;
import one.jpro.media.event.MediaPlayerEvent;
import one.jpro.media.player.MediaPlayer;
import one.jpro.media.player.MediaPlayerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * {@link MediaPlayer} implementation for the web.
 *
 * @author Besmir Beqiri
 */
public final class WebMediaPlayer extends BaseMediaPlayer implements WebMediaEngine {

    private final Logger log = LoggerFactory.getLogger(WebMediaPlayer.class);

    private final WebAPI webAPI;
    private final String mediaPlayerId;
    private final JSVariable playerVideoElement;

    private boolean playerReady = false;

    public WebMediaPlayer(WebAPI webAPI, MediaSource mediaSource) {
        this.webAPI = Objects.requireNonNull(webAPI, "WebAPI cannot be null.");
        mediaPlayerId = webAPI.createUniqueJSName("media_player_");
        playerVideoElement = createPlayerVideoElement("video_elem_" + mediaPlayerId);
        setMediaSource(Objects.requireNonNull(mediaSource, "Media source cannot be null."));

        // check if the media data is loaded
        handleWebEvent("loadeddata", """
                    console.log("$mediaPlayerId => ready state: " + elem.readyState);
                    java_fun(elem.readyState);
                """, readyState -> WebReadyState.fromCode(Integer.parseInt(readyState)).ifPresent(this::setReadyState));

        // handle current time change
        handleWebEvent("timeupdate", """
                console.log("$mediaPlayerId => current time: " + elem.currentTime);
                java_fun(elem.currentTime);
                """, currentTime -> setCurrentTime(Duration.seconds(Double.parseDouble(currentTime))));

        // handle duration change
        handleWebEvent("durationchange", """
                console.log("$mediaPlayerId => media duration: " + elem.duration + " seconds");
                java_fun(elem.duration);
                """, duration -> {
            if (duration != null && !duration.contains("null")) {
                setDuration(Duration.seconds(Double.parseDouble(duration)));
            } else {
                setDuration(Duration.UNKNOWN);
            }
        });

        // handle volume change
        handleWebEvent("volumechange", """
                console.log("$mediaPlayerId => volume change: " + elem.volume);
                java_fun(elem.volume);
                """, volume -> volumeProperty().set(Double.parseDouble(volume)));

        // handle play event
        handleWebEvent("play", """
                    console.log("$mediaPlayerId => playing...");
                    java_fun(elem.currentTime);
                """, currentTime -> {
            // Set status to playing
            setStatus(Status.PLAYING);

            // Fire play event
            Event.fireEvent(WebMediaPlayer.this,
                    new MediaPlayerEvent(WebMediaPlayer.this,
                            MediaPlayerEvent.MEDIA_PLAYER_PLAY));
        });

        // handle pause event
        handleWebEvent("pause", """
                    console.log("$mediaPlayerId => paused...");
                    java_fun(elem.paused);
                """, paused -> {
            if (Boolean.parseBoolean(paused)) {
                // Set status to paused
                setStatus(Status.PAUSED);

                // Fire pause event
                Event.fireEvent(WebMediaPlayer.this,
                        new MediaPlayerEvent(WebMediaPlayer.this,
                                MediaPlayerEvent.MEDIA_PLAYER_PAUSE));
            }
        });

        // handle stalled event
        handleWebEvent("stalled", """
                    console.log("$mediaPlayerId => stalled...");
                    java_fun(elem.readyState);
                """, readyState -> {
            log.trace("Media player stalled: {}", readyState);

            // Set status to stalled
            setStatus(Status.STALLED);

            // Fire stalled event
            Event.fireEvent(WebMediaPlayer.this,
                    new MediaPlayerEvent(WebMediaPlayer.this,
                            MediaPlayerEvent.MEDIA_PLAYER_STALLED));
        });

        // handle ended event
        handleWebEvent("ended", """
                    console.log("$mediaPlayerId => ended...");
                    java_fun(elem.ended);
                """, ended -> {
            if (Boolean.parseBoolean(ended)) {
                stop();

                // Fire end of media event
                Event.fireEvent(WebMediaPlayer.this,
                        new MediaPlayerEvent(WebMediaPlayer.this,
                                MediaPlayerEvent.MEDIA_PLAYER_END_OF_MEDIA));
            }
        });

        // handle error event
        handleWebEvent("error", """
                    console.log("$mediaPlayerId => error occurred with code: " + elem.error.code);
                    java_fun(elem.error.code);
                """, errorCode -> {
            // Set error
            WebMediaError.fromCode(Integer.parseInt(errorCode)).ifPresent(webErrorCode ->
                    setError(new MediaPlayerException(webErrorCode.getDescription())));

            // Set status to halted
            setStatus(Status.HALTED);

            // Fire error event
            Event.fireEvent(WebMediaPlayer.this,
                    new MediaPlayerEvent(WebMediaPlayer.this,
                            MediaPlayerEvent.MEDIA_PLAYER_ERROR));
        });
    }

    public WebAPI getWebAPI() {
        return webAPI;
    }

    public JSVariable getVideoElement() {
        return playerVideoElement;
    }

    @Override
    ReadOnlyObjectWrapper<MediaSource> mediaSourcePropertyImpl() {
        if (mediaSource == null) {
            mediaSource = new ReadOnlyObjectWrapper<>(this, "source") {
                @Override
                protected void invalidated() {
                    if (getStatus() != Status.DISPOSED) {
                        webAPI.executeScript("""
                                %s.src = "$source";
                                """.formatted(playerVideoElement.getName())
                                .replace("$source", get().source())
                                .replace("\"\"", "\""));
                    }
                }
            };
        }
        return mediaSource;
    }

    @Override
    public BooleanProperty autoPlayProperty() {
        if (autoPlay == null) {
            autoPlay = new SimpleBooleanProperty(this, "autoPlay") {
                @Override
                protected void invalidated() {
                    if (getStatus() != Status.DISPOSED) {
                        webAPI.executeScript("""
                                %s.autoplay = $autoplay;
                                """.formatted(playerVideoElement.getName())
                                .replace("$autoplay", String.valueOf(get())));
                    }
                }
            };
        }
        return autoPlay;
    }

    // ready state property
    private ReadOnlyObjectWrapper<WebReadyState> readyState;

    public WebReadyState getReadyState() {
        return readyState == null ? WebReadyState.HAVE_NOTHING : readyState.get();
    }

    private void setReadyState(WebReadyState value) {
        readyStatePropertyImpl().set(value);
    }

    public ReadOnlyObjectProperty<WebReadyState> readyStateProperty() {
        return readyStatePropertyImpl().getReadOnlyProperty();
    }

    private ReadOnlyObjectWrapper<WebReadyState> readyStatePropertyImpl() {
        if (readyState == null) {
            readyState = new ReadOnlyObjectWrapper<>(this, "readyState") {

                @Override
                protected void invalidated() {
                    if (get().getCode() >= WebReadyState.HAVE_METADATA.getCode()) {
                        playerReady = true;

                        // Set state to ready
                        setStatus(Status.READY);

                        // Fire ready event
                        Event.fireEvent(WebMediaPlayer.this,
                                new MediaPlayerEvent(WebMediaPlayer.this,
                                        MediaPlayerEvent.MEDIA_PLAYER_READY));
                    }
                    log.trace("Ready state changed: {}", get());
                }
            };
        }
        return readyState;
    }

    // volume property
    private DoubleProperty volume;

    @Override
    public double getVolume() {
        return (volume == null) ? 1.0 : volume.get();
    }

    @Override
    public void setVolume(double value) {
        value = clamp(value, 0.0, 1.0);
        if (getStatus() != Status.DISPOSED) {
            webAPI.executeScript("""
                    %s.volume = %s;
                    """.formatted(playerVideoElement.getName(), value));
        }
        volumeProperty().set(value);
    }

    @Override
    public DoubleProperty volumeProperty() {
        if (volume == null) {
            volume = new SimpleDoubleProperty(this, "volume", 1.0) {

                @Override
                protected void invalidated() {
                    log.trace("Volume changed: {}", get());
                }
            };
        }
        return volume;
    }

    // muted property
    private BooleanProperty muted;

    @Override
    public boolean isMute() {
        return muted != null && muted.get();
    }

    @Override
    public void setMute(boolean value) {
        muteProperty().set(value);
    }

    @Override
    public BooleanProperty muteProperty() {
        if (muted == null) {
            muted = new SimpleBooleanProperty(this, "muted") {
                @Override
                protected void invalidated() {
                    if (getStatus() != Status.DISPOSED) {
                        webAPI.executeScript("""
                                %s.muted = $muted;
                                """.formatted(playerVideoElement.getName())
                                .replace("$muted", String.valueOf(get())));
                    }
                }
            };
        }
        return muted;
    }

    @Override
    public void play() {
        if (playerReady && getStatus() != Status.DISPOSED) {
            webAPI.executeScript("""
                    %s.play();
                    """.formatted(playerVideoElement.getName()));
        }
    }

    @Override
    public void pause() {
        if (playerReady && getStatus() != Status.DISPOSED) {
            webAPI.executeScript("""
                    %s.pause();
                    """.formatted(playerVideoElement.getName()));
        }
    }

    @Override
    public void stop() {
        if (playerReady && getStatus() != Status.DISPOSED) {
            webAPI.executeScript("""
                    $playerVideoElem.pause();
                    $playerVideoElem.currentTime = 0;
                    """.replace("$playerVideoElem", playerVideoElement.getName()));
        }

        setStatus(Status.STOPPED);
    }

    @Override
    public void seek(Duration seekTime) {
        if (getStatus() == Status.DISPOSED) {
            return;
        }

        if (playerReady && seekTime != null && !seekTime.isUnknown()) {
            webAPI.executeScript("""
                    %s.currentTime=%s;
                    """.formatted(playerVideoElement.getName(), seekTime.toSeconds()));
        }
    }

    private void handleWebEvent(String eventName, String eventHandler, WebCallback webCallback) {
        webAPI.registerJavaFunction(mediaPlayerId + "_" + eventName, webCallback);
        webAPI.executeScript("""
                let elem = $playerVideoElem;
                elem.on$eventName = (event) => {
                     $eventHandler
                };
                """
                .replace("$playerVideoElem", playerVideoElement.getName())
                .replace("$eventHandler", eventHandler)
                .replace("java_fun", "jpro.$mediaPlayerId_$eventName")
                .replace("$mediaPlayerId", mediaPlayerId)
                .replace("$eventName", eventName));
    }

    /**
     * Simple utility function which clamps the given value to be strictly
     * between the min and max values.
     */
    private double clamp(double min, double value, double max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    private JSVariable createPlayerVideoElement(String videoElement) {
        webAPI.executeScript("""
                $playerVideoElem = document.createElement("video");
                $playerVideoElem.controls = false;
                $playerVideoElem.muted = false;
                $playerVideoElem.setAttribute("webkit-playsinline", 'webkit-playsinline');
                $playerVideoElem.setAttribute("playsinline", 'playsinline');
                """.replace("$playerVideoElem", videoElement));
        return new JSVariable(webAPI, videoElement);
    }
}
