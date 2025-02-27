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

                // Place the tile temporarily instead of permanently
                success = gameController.placeTileTemporarily(tileIndex, row, col);
            } catch (NumberFormatException e) {
                // Invalid drag data
                System.out.println("Invalid drag data: " + e.getMessage());
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