package one.jpro.platform.file;

import javafx.beans.property.ReadOnlyLongProperty;
import javafx.beans.property.ReadOnlyLongWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;

/**
 * File source.
 *
 * @author Besmir Beqiri
 */
public sealed abstract class FileSource<T> permits NativeFileSource, WebFileSource {

    private final T platformBlob;

    public FileSource(T platformBlob) {
        this.platformBlob = platformBlob;
    }

    public final T getPlatformFile() {
        return platformBlob;
    }

    abstract String _getName();

    // name property
    private ReadOnlyStringWrapper nameProperty;

    public final String getName() {
        return nameProperty == null ? _getName() : nameProperty.get();
    }

    public final ReadOnlyStringProperty nameProperty() {
        if (nameProperty == null) {
            nameProperty = new ReadOnlyStringWrapper(this, "name", _getName());
        }
        return nameProperty.getReadOnlyProperty();
    }

    abstract long _getSize();

    // size property
    private ReadOnlyLongWrapper sizeProperty;

    public final long getSize() {
        return sizeProperty == null ? _getSize() : sizeProperty.get();
    }

    public final ReadOnlyLongProperty sizeProperty() {
        if (sizeProperty == null) {
            sizeProperty = new ReadOnlyLongWrapper(this, "size", _getSize());
        }
        return sizeProperty.getReadOnlyProperty();
    }

    abstract String _getObjectURL();

    // objectURL property
    private ReadOnlyStringWrapper objectURLProperty;

    public final String getObjectURL() {
        return objectURLProperty == null ? _getObjectURL() : objectURLProperty.get();
    }

    public final ReadOnlyStringProperty objectURLProperty() {
        if (objectURLProperty == null) {
            objectURLProperty = new ReadOnlyStringWrapper(this, "objectURL", _getObjectURL());
        }
        return objectURLProperty.getReadOnlyProperty();
    }
}
