package edu.leicester.scrabble.view;

import edu.leicester.scrabble.controller.GameController;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class GameView extends BorderPane {
    private final GameController controller;
    private final BoardView boardView;
    private final RackView rackView;
    private final GameInfoView gameInfoView;
    private final ControlPanel controlPanel;

    public GameView(GameController controller) {
        this.controller = controller;
        this.boardView = new BoardView(controller);
        this.rackView = new RackView(controller);
        this.gameInfoView = new GameInfoView(controller);
        this.controlPanel = new ControlPanel(controller);

        setCenter(boardView);
        setRight(gameInfoView);
        setBottom(createBottomPane());
        setPadding(new Insets(10));
        setStyle("-fx-background-color: #f0f0f0;");
        setupListeners();
    }

    private VBox createBottomPane() {
        VBox bottomPane = new VBox(10);
        bottomPane.setPadding(new Insets(10, 0, 0, 0));

        // Add rack view
        bottomPane.getChildren().add(rackView);

        // Add control panel
        bottomPane.getChildren().add(controlPanel);

        return bottomPane;
    }

    private void setupListeners() {
        // Update board when board changes
        controller.setBoardUpdateListener(() -> {
            boardView.updateBoard();
        });

        // Update rack when rack changes
        controller.setRackUpdateListener(() -> {
            rackView.updateRack();
        });

        // Update player info when current player changes
        controller.setPlayerUpdateListener(() -> {
            gameInfoView.updatePlayerInfo();
            rackView.updateRack();
        });

        // Show game over dialog when game ends
        controller.setGameOverListener(() -> {
            showGameOverDialog();
        });
    }

    private void showGameOverDialog() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Game Over");
        alert.setHeaderText("The game has ended!");

        // Find the winner
        String winnerText = "Final scores:\n";
        int highestScore = -1;
        String winner = "";

        for (var player : controller.getPlayers()) {
            int score = player.getScore();
            winnerText += player.getName() + ": " + score + "\n";

            if (score > highestScore) {
                highestScore = score;
                winner = player.getName();
            }
        }

        winnerText += "\nWinner: " + winner + "!";
        alert.setContentText(winnerText);

        alert.showAndWait();
    }

    private class ControlPanel extends HBox {

        // In the ControlPanel class within GameView.java:

        public ControlPanel(GameController controller) {
            setSpacing(10);
            setPadding(new Insets(10));
            setStyle("-fx-background-color: #e0e0e0; -fx-border-color: #cccccc; -fx-border-radius: 5;");

            // Play button
            Button playButton = new Button("Play Word");
            playButton.setOnAction(e -> {
                boolean success = controller.commitPlacement();
                if (!success) {
                    showError("Invalid word placement. Please try again.");
                }
            });

            // Cancel button (new)
            Button cancelButton = new Button("Cancel Placement");
            cancelButton.setOnAction(e -> {
                controller.cancelPlacements();
            });

            // Exchange button
            Button exchangeButton = new Button("Exchange Tiles");
            exchangeButton.setOnAction(e -> {
                boolean success = controller.exchangeTiles();
                if (!success) {
                    showError("Cannot exchange tiles. Make sure you have selected tiles and there are enough tiles in the bag.");
                }
            });

            // Pass button
            Button passButton = new Button("Pass Turn");
            passButton.setOnAction(e -> {
                controller.passTurn();
            });

            // Add buttons to panel
            getChildren().addAll(playButton, cancelButton, exchangeButton, passButton);

            // Make buttons equal width
            for (var node : getChildren()) {
                HBox.setHgrow(node, Priority.ALWAYS);
                ((Button) node).setMaxWidth(Double.MAX_VALUE);
            }
        }
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
