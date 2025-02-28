package edu.leicester.scrabble;

import com.sun.tools.javac.Main;
import edu.leicester.scrabble.controller.GameController;
import edu.leicester.scrabble.model.Game;
import edu.leicester.scrabble.model.Player;
import edu.leicester.scrabble.util.FileLoader;
import edu.leicester.scrabble.view.GameView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.io.IOException;
import java.io.InputStream;

/**
 * Main application class for the Scrabble game.
 */
public class ScrabbleApp extends Application {

    private Game game;
    private GameController gameController;

    @Override
    public void start(Stage primaryStage) {
        try {
            initGame();
            gameController = new GameController(game);
            GameView gameView = new GameView(gameController);
            Scene scene = new Scene(gameView, 1024, 768);

            String cssResource = "/edu/leicester/scrabble/styles/style.css";
            scene.getStylesheets().add(getClass().getResource(cssResource).toExternalForm());

            primaryStage.setTitle("Scrabble");
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(800);
            primaryStage.setMinHeight(600);
            primaryStage.show();

            gameController.startGame();

        } catch (Exception e) {
            e.printStackTrace();
            showErrorAndExit("Failed to start the game: " + e.getMessage());
        }
    }

    private void initGame() throws IOException {
        InputStream dictionaryStream = FileLoader.loadDefaultDictionary();

        game = new Game(dictionaryStream, "Dictionary.txt");

        game.addPlayer(new Player("Player 1"));
        game.addPlayer(new Player("Computer", true));
    }

    private void showErrorAndExit(String message) {
        try {
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                    javafx.scene.control.Alert.AlertType.ERROR
            );
            alert.setTitle("Error");
            alert.setHeaderText("Application Error");
            alert.setContentText(message);
            alert.showAndWait();
        } catch (Exception e) {
            System.err.println("ERROR: " + message);
        }
        System.exit(1);
    }

    public static void main(String[] args) {
        launch(args);
    }
}