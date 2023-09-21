package one.jpro.platform.htmlscrollpane.example;

import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import one.jpro.platform.htmlscrollpane.HTMLScrollPaneSkin;

public class HTMLScrollPaneSample extends Application {

        @Override
        public void start(Stage primaryStage) {
            Scene scene = new Scene(createRoot());
            primaryStage.setScene(scene);
            primaryStage.show();
        }

        public Parent createRoot() {
            VBox content = new VBox();
            for (int i = 0; i < 100; i++) {
                // add a label
                javafx.scene.control.Label label = new javafx.scene.control.Label("Label " + i);
                content.getChildren().add(label);
            }
            ScrollPane scrollPane = new ScrollPane();
            scrollPane.setContent(content);
            scrollPane.setSkin(new HTMLScrollPaneSkin(scrollPane));

            return scrollPane;
        }
}
