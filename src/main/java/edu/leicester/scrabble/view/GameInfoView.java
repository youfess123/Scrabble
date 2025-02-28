package edu.leicester.scrabble.view;

import edu.leicester.scrabble.controller.GameController;
import edu.leicester.scrabble.model.Move;
import edu.leicester.scrabble.model.Player;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Separator;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.List;

public class GameInfoView extends VBox {

    private final GameController controller;
    private final Label currentPlayerLabel;
    private final Label tilesRemainingLabel;
    private final VBox playerScoresBox;
    private final ListView<String> moveHistoryList;

    public GameInfoView(GameController controller) {
        this.controller = controller;

        setPrefWidth(200);
        setPadding(new Insets(10));
        setSpacing(15);
        setBorder(new Border(new BorderStroke(Color.DARKGRAY, BorderStrokeStyle.SOLID, new CornerRadii(5), BorderWidths.DEFAULT)));
        setBackground(new Background(new BackgroundFill(Color.WHITESMOKE, CornerRadii.EMPTY, Insets.EMPTY)));

        // Current player section
        currentPlayerLabel = new Label();
        currentPlayerLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        currentPlayerLabel.setWrapText(true);

        // Tiles remaining section
        tilesRemainingLabel = new Label();
        tilesRemainingLabel.setFont(Font.font("Arial", 12));

        // Player scores section
        Label scoresHeader = new Label("Scores");
        scoresHeader.setFont(Font.font("Arial", FontWeight.BOLD, 14));

        playerScoresBox = new VBox(5);

        // Move history section
        Label historyHeader = new Label("Move History");
        historyHeader.setFont(Font.font("Arial", FontWeight.BOLD, 14));

        moveHistoryList = new ListView<>();
        moveHistoryList.setPrefHeight(200);

        Separator separator1 = new Separator();
        Separator separator2 = new Separator();

        getChildren().addAll(
                currentPlayerLabel,
                tilesRemainingLabel,
                separator1,
                scoresHeader,
                playerScoresBox,
                separator2,
                historyHeader,
                moveHistoryList
        );

        updatePlayerInfo();
    }

    public void updatePlayerInfo() {
        Player currentPlayer = controller.getCurrentPlayer();
        currentPlayerLabel.setText("Current Player: " + currentPlayer.getName());

        int remainingTiles = controller.getRemainingTileCount();
        tilesRemainingLabel.setText("Tiles Remaining: " + remainingTiles);

        updatePlayerScores();
        updateMoveHistory();
    }

    private void updatePlayerScores() {
        playerScoresBox.getChildren().clear();

        for (Player player : controller.getPlayers()) {
            Label scoreLabel = new Label(player.getName() + ": " + player.getScore());

            if (player == controller.getCurrentPlayer()) {
                scoreLabel.setTextFill(Color.BLUE);
                scoreLabel.setFont(Font.font("Arial", FontWeight.BOLD, 12));
            } else {
                scoreLabel.setFont(Font.font("Arial", 12));
            }

            playerScoresBox.getChildren().add(scoreLabel);
        }
    }


    private void updateMoveHistory() {
        // Clear the current items in the list
        moveHistoryList.getItems().clear();

        // Add the initial "Game started" entry
        moveHistoryList.getItems().add("Game started");

        // Get the move history from the game model
        List<Move> moves = controller.getMoveHistory();

        // Add each move to the list view
        for (Move move : moves) {
            moveHistoryList.getItems().add(move.toString());
        }
    }

}