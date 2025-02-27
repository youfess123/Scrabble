package edu.leicester.scrabble.view;

import edu.leicester.scrabble.controller.GameController;
import edu.leicester.scrabble.model.Player;
import edu.leicester.scrabble.model.Rack;
import edu.leicester.scrabble.model.Tile;
import edu.leicester.scrabble.util.ScrabbleConstants;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

import java.util.ArrayList;
import java.util.List;

public class RackView extends HBox {

    private final GameController controller;
    private List<TileView> tileViews;

    public RackView(GameController controller) {
        this.controller = controller;
        this.tileViews = new ArrayList<>();

        setSpacing(10);
        setPadding(new Insets(10));
        setAlignment(Pos.CENTER);

        setBorder(new Border(new BorderStroke(Color.DARKGRAY, BorderStrokeStyle.SOLID, new CornerRadii(5), BorderWidths.DEFAULT)));
        setBackground(new Background(new BackgroundFill(Color.rgb(240, 240, 240), CornerRadii.EMPTY, Insets.EMPTY)));

        // Initial update
        updateRack();
    }

    public void updateRack() {
        getChildren().clear();
        tileViews.clear();

        Player currentPlayer = controller.getCurrentPlayer();


        Rack rack = currentPlayer.getRack();

        Label playerLabel = new Label("Current Player: " + currentPlayer.getName());
        playerLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        playerLabel.setPadding(new Insets(0, 20, 0, 0));
        getChildren().add(playerLabel);

        // Add tiles
        for (int i = 0; i < rack.size(); i++) {
            Tile tile = rack.getTile(i);
            TileView tileView = new TileView(tile, i);

            // Apply selection styling if tile is selected
            if (controller.isTileSelected(i)) {
                tileView.select();
            }

            tileViews.add(tileView);
            getChildren().add(tileView);
        }

        // Add empty slots
        for (int i = 0; i < rack.getEmptySlots(); i++) {
            EmptySlotView emptySlot = new EmptySlotView();
            getChildren().add(emptySlot);
        }
    }

    private class TileView extends StackPane {
        private final Tile tile;
        private final int index;
        private final Label letterLabel;
        private final Label valueLabel;
        private boolean isSelected = false;
        private boolean isDragging = false;

        public TileView(Tile tile, int index) {
            this.tile = tile;
            this.index = index;

            setPrefSize(ScrabbleConstants.TILE_SIZE, ScrabbleConstants.TILE_SIZE);
            setBorder(new Border(new BorderStroke(Color.BLACK, BorderStrokeStyle.SOLID, new CornerRadii(3), BorderWidths.DEFAULT)));
            setBackground(new Background(new BackgroundFill(Color.BURLYWOOD, CornerRadii.EMPTY, Insets.EMPTY)));

            letterLabel = new Label(String.valueOf(tile.getLetter()));
            letterLabel.setFont(Font.font("Arial", FontWeight.BOLD, 18));
            letterLabel.setAlignment(Pos.CENTER);

            valueLabel = new Label(String.valueOf(tile.getValue()));
            valueLabel.setFont(Font.font("Arial", 10));
            valueLabel.setAlignment(Pos.BOTTOM_RIGHT);
            valueLabel.setTranslateX(10);
            valueLabel.setTranslateY(10);

            getChildren().addAll(letterLabel, valueLabel);

            setupEvents();
        }

        private void setupEvents() {
            setOnMouseClicked(event -> {
                controller.selectTileFromRack(index);
                updateRack(); // Update the entire rack to reflect selection changes
            });

            setOnDragDetected(event -> {
                // Only allow dragging if the tile is not already placed on the board
                if (controller.isTileSelected(index)) {
                    Dragboard db = startDragAndDrop(TransferMode.MOVE);
                    ClipboardContent content = new ClipboardContent();
                    content.putString(String.valueOf(index));
                    db.setContent(content);
                    isDragging = true;
                    event.consume();
                } else {
                    // First select the tile, then allow dragging on next attempt
                    controller.selectTileFromRack(index);
                    updateRack();
                }
            });

            setOnDragDone(event -> {
                isDragging = false;
                // The tile may have been placed on the board or dropped elsewhere
                // We'll let the controller handle the selection state
                event.consume();
            });
        }

        public void select() {
            isSelected = true;
            setBackground(new Background(new BackgroundFill(Color.GOLD, CornerRadii.EMPTY, Insets.EMPTY)));
            setBorder(new Border(new BorderStroke(Color.ORANGE, BorderStrokeStyle.SOLID, new CornerRadii(3), new BorderWidths(2))));
        }

        public void deselect() {
            isSelected = false;
            setBackground(new Background(new BackgroundFill(Color.BURLYWOOD, CornerRadii.EMPTY, Insets.EMPTY)));
            setBorder(new Border(new BorderStroke(Color.BLACK, BorderStrokeStyle.SOLID, new CornerRadii(3), BorderWidths.DEFAULT)));
        }
    }

    private class EmptySlotView extends StackPane {
        public EmptySlotView() {
            setPrefSize(ScrabbleConstants.TILE_SIZE, ScrabbleConstants.TILE_SIZE);
            setBorder(new Border(new BorderStroke(Color.LIGHTGRAY, BorderStrokeStyle.DASHED, new CornerRadii(3), BorderWidths.DEFAULT)));
            setBackground(new Background(new BackgroundFill(Color.WHITE, CornerRadii.EMPTY, Insets.EMPTY)));
        }
    }
}