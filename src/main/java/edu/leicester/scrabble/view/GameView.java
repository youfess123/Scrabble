package edu.leicester.scrabble.view;

import edu.leicester.scrabble.controller.GameController;
import edu.leicester.scrabble.model.Player;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
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
        bottomPane.getChildren().addAll(rackView, controlPanel);
        return bottomPane;
    }

    private void setupListeners() {
        controller.setBoardUpdateListener(() -> boardView.updateBoard());
        controller.setRackUpdateListener(() -> rackView.updateRack());
        controller.setPlayerUpdateListener(() -> {
            gameInfoView.updatePlayerInfo();
            rackView.updateRack();
        });
        controller.setGameOverListener(this::showGameOverDialog);
    }

    private void showGameOverDialog() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Game Over");
        alert.setHeaderText("The game has ended!");

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
        private final Button playButton;
        private final Button cancelButton;
        private final Button exchangeButton;
        private final Button passButton;

        public ControlPanel(GameController controller) {
            setSpacing(10);
            setPadding(new Insets(10));
            setStyle("-fx-background-color: #e0e0e0; -fx-border-color: #cccccc; -fx-border-radius: 5;");

            playButton = new Button("Play Word");
            playButton.setOnAction(e -> {
                boolean success = controller.commitPlacement();
                if (!success) {
                    if (controller.getTemporaryPlacements().isEmpty()) {
                        showError("No tiles placed. Please place tiles on the board first.");
                    } else {
                        showError("Invalid word placement. Ensure your tiles form valid English words.");
                    }
                }
            });

            cancelButton = new Button("Cancel Placement");
            cancelButton.setOnAction(e -> {
                if (!controller.getTemporaryPlacements().isEmpty()) {
                    controller.cancelPlacements();
                    showInfo("Placement cancelled. Tiles have been returned to your rack.");
                } else {
                    showInfo("No tiles to cancel.");
                }
            });

            exchangeButton = new Button("Exchange Tiles");
            exchangeButton.setOnAction(e -> {
                if (controller.getTemporaryPlacements().isEmpty()) {
                    if (controller.getSelectedTiles().isEmpty()) {
                        showError("No tiles selected. Please select tiles from your rack to exchange.");
                    } else {
                        boolean success = controller.exchangeTiles();
                        if (!success) {
                            showError("Exchange failed. There may not be enough tiles in the bag.");
                        }
                    }
                } else {
                    showError("Please cancel your current placement before exchanging tiles.");
                }
            });

            passButton = new Button("Pass Turn");
            passButton.setOnAction(e -> {
                if (controller.getTemporaryPlacements().isEmpty()) {
                    boolean success = controller.passTurn();
                    if (success) {
                        showInfo("Turn passed to " + controller.getCurrentPlayer().getName() + ".");
                    }
                } else {
                    showError("Please cancel your current placement before passing.");
                }
            });

            getChildren().addAll(playButton, cancelButton, exchangeButton, passButton);
            for (var node : getChildren()) {
                HBox.setHgrow(node, Priority.ALWAYS);
                ((Button) node).setMaxWidth(Double.MAX_VALUE);
            }
            playButton.setTooltip(new Tooltip("Confirm and play the word"));
            cancelButton.setTooltip(new Tooltip("Cancel tile placement"));
            exchangeButton.setTooltip(new Tooltip("Exchange selected tiles for new ones"));
            passButton.setTooltip(new Tooltip("Pass your turn"));
        }

        public void updateButtonStates() {
            Player currentPlayer = controller.getCurrentPlayer();
            boolean isPlayerTurn = !currentPlayer.isComputer();
            boolean hasTemporaryPlacements = !controller.getTemporaryPlacements().isEmpty();
            boolean hasSelectedTiles = !controller.getSelectedTiles().isEmpty();

            playButton.setDisable(!isPlayerTurn || !hasTemporaryPlacements);
            cancelButton.setDisable(!isPlayerTurn || !hasTemporaryPlacements);
            exchangeButton.setDisable(!isPlayerTurn || hasTemporaryPlacements || !hasSelectedTiles);
            passButton.setDisable(!isPlayerTurn || hasTemporaryPlacements);
        }
    }

    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Information");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
