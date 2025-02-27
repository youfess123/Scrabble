package edu.leicester.scrabble;

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
            // Initialize the game
            initGame();

            // Create the controller
            gameController = new GameController(game);

            // Create the view
            GameView gameView = new GameView(gameController);

            // Create the scene
            Scene scene = new Scene(gameView, 1024, 768);

            // Try to load the CSS stylesheet if it exists
            try {
                String cssResource = "/styles/style.css";
                if (getClass().getResource(cssResource) != null) {
                    scene.getStylesheets().add(getClass().getResource(cssResource).toExternalForm());
                } else {
                    System.out.println("CSS stylesheet not found. Using default styles.");
                    // Application will use default JavaFX styling
                }
            } catch (Exception e) {
                System.out.println("Warning: Failed to load CSS: " + e.getMessage());
            }

            // Set up the stage
            primaryStage.setTitle("Scrabble");
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(800);
            primaryStage.setMinHeight(600);
            primaryStage.show();

            // Start the game
            gameController.startGame();

        } catch (Exception e) {
            e.printStackTrace();
            showErrorAndExit("Failed to start the game: " + e.getMessage());
        }
    }

    /**
     * Initializes the game model.
     *
     * @throws IOException If the dictionary cannot be loaded
     */
    private void initGame() throws IOException {
        // Load the dictionary
        InputStream dictionaryStream = FileLoader.loadDefaultDictionary();

        // Create the game
        game = new Game(dictionaryStream, "Default");

        // Add players
        game.addPlayer(new Player("Player 1"));
        game.addPlayer(new Player("Computer", true));
    }

    /**
     * Shows an error message and exits the application.
     *
     * @param message The error message
     */
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
            // If JavaFX platform isn't running yet, fall back to console error
            System.err.println("ERROR: " + message);
        }
        System.exit(1);
    }

    /**
     * Main method.
     *
     * @param args Command line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }
}