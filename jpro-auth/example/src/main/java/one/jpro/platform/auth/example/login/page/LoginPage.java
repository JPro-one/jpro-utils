package one.jpro.platform.auth.example.login.page;

import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import one.jpro.platform.auth.core.oauth2.provider.GoogleAuthenticationProvider;
import one.jpro.platform.auth.example.login.LoginApp;
import one.jpro.platform.auth.routing.OAuth2Filter;

import java.util.Optional;

/**
 * Login page.
 *
 * @author Besmir Beqiri
 */
public class LoginPage extends Page {

    public LoginPage(LoginApp app, GoogleAuthenticationProvider authProvider) {
        getStyleClass().add("simple-page");

        final var googleProviderButton = createAuthProviderButton("Google");
        googleProviderButton.setDefaultButton(true);
        googleProviderButton.setOnAction(event -> OAuth2Filter.authorize(googleProviderButton, authProvider));

        getChildren().add(googleProviderButton);
    }

    /**
     * Create a button for the given provider.
     *
     * @param text the provider name
     * @return a button node
     */
    public Button createAuthProviderButton(String text) {
        ImageView iconView = new ImageView();
        iconView.setFitWidth(56);
        iconView.setFitHeight(56);
        Optional.ofNullable(getClass().getResourceAsStream("/images/" + text + "_Logo.png"))
                .map(inputStream -> new Image(inputStream, 0, 0, true, true))
                .ifPresent(iconView::setImage);

        Button loginButton = new Button("Login with\n" + text, iconView);
        loginButton.getStyleClass().addAll("login-button");
        return loginButton;
    }
}
