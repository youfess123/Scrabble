package edu.leicester.scrabble.controller;


import edu.leicester.scrabble.model.Move;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;

import java.util.ArrayList;
import java.util.List;

public class DragDropController {

    private final GameController gameController;
    private List<Integer> draggedTileIndices;
    private boolean isHorizontalPlacement;

    public DragDropController(GameController gameController) {
        this.gameController = gameController;
        this.draggedTileIndices = new ArrayList<>();
        this.isHorizontalPlacement = true;
    }

    public void startDrag(int tileIndex) {
        draggedTileIndices.clear();
        draggedTileIndices.add(tileIndex);
    }

    public void setPlacementDirection(boolean isHorizontal) {
        this.isHorizontalPlacement = isHorizontal;
    }

    public boolean handleDrop(DragEvent event, int row, int col) {
        Dragboard db = event.getDragboard();
        boolean success = false;

        if (db.hasString()) {
            try {
                int tileIndex = Integer.parseInt(db.getString());

                // Select the tile if not already selected
                if (!gameController.isTileSelected(tileIndex)) {
                    gameController.selectTileFromRack(tileIndex);
                }

                // Place the selected tiles
                Move.Direction direction = isHorizontalPlacement ?
                        Move.Direction.HORIZONTAL : Move.Direction.VERTICAL;

                success = gameController.placeTiles(row, col, direction);
            } catch (NumberFormatException e) {
                // Invalid drag data
            }
        }

        return success;
    }

    public void addTileIndex(int index) {
        if (!draggedTileIndices.contains(index)) {
            draggedTileIndices.add(index);
        }
    }

    public List<Integer> getDraggedTileIndices() {
        return new ArrayList<>(draggedTileIndices);
    }

    public void reset() {
        draggedTileIndices.clear();
    }
}