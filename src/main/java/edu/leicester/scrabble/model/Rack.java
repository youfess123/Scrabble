package edu.leicester.scrabble.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Rack {
    public static final int RACK_SIZE = 7; //hahahahahahah
    private final List<Tile> tiles;

    public Rack() {
        this.tiles = new ArrayList<>(RACK_SIZE);
    }

    public boolean addTile(Tile tile) {
        if (tile == null) {
            System.out.println("WARNING: Attempted to add null tile to rack");
            return false;
        }

        if (tiles.size() >= RACK_SIZE) {
            System.out.println("WARNING: Cannot add tile, rack is full");
            return false;
        }

        boolean result = tiles.add(tile);
        System.out.println("Rack: Added tile " + tile.getLetter() + ". Rack size now: " + tiles.size());
        return result;
    }

    public int addTiles(List<Tile> tilesToAdd) {
        if (tilesToAdd == null || tilesToAdd.isEmpty()) {
            System.out.println("WARNING: No tiles to add to rack");
            return 0;
        }

        System.out.println("Rack: Attempting to add " + tilesToAdd.size() + " tiles. Current size: " + tiles.size());

        int count = 0;
        for (Tile tile : tilesToAdd) {
            if (tile == null) {
                System.out.println("WARNING: Skipping null tile");
                continue;
            }

            if (addTile(tile)) {
                count++;
            } else {
                System.out.println("WARNING: Failed to add tile " + tile.getLetter());
                break;  // Rack is full
            }
        }

        System.out.println("Rack: Added " + count + " tiles. New size: " + tiles.size());
        return count;
    }

    public Tile removeTile(int index) {
        if (index < 0 || index >= tiles.size()) {
            System.out.println("WARNING: Invalid tile index to remove: " + index);
            return null;
        }

        Tile removed = tiles.remove(index);
        System.out.println("Rack: Removed tile at index " + index + ". Rack size now: " + tiles.size());
        return removed;
    }

    public boolean removeTile(Tile tile) {
        if (tile == null) {
            System.out.println("WARNING: Attempting to remove null tile");
            return false;
        }

        boolean result = tiles.remove(tile);
        if (result) {
            System.out.println("Rack: Removed tile " + tile.getLetter() + ". Rack size now: " + tiles.size());
        } else {
            System.out.println("WARNING: Tile " + tile.getLetter() + " not found in rack");
        }
        return result;
    }

    public boolean removeTiles(List<Tile> tilesToRemove) {
        if (tilesToRemove == null || tilesToRemove.isEmpty()) {
            System.out.println("WARNING: No tiles to remove");
            return false;
        }

        System.out.println("Rack: Attempting to remove " + tilesToRemove.size() + " tiles. Current size: " + tiles.size());

        // FIX: Create a copy of tilesToRemove to avoid concurrent modification
        List<Tile> tilesToRemoveCopy = new ArrayList<>(tilesToRemove);
        int initialSize = tiles.size();
        int removedCount = 0;

        // FIX: Iterate over the rack tiles by index and letter to find matching tiles
        for (Tile tileToRemove : tilesToRemoveCopy) {
            // Find a tile in the rack with the same letter
            boolean found = false;
            for (int i = 0; i < tiles.size(); i++) {
                Tile rackTile = tiles.get(i);
                if (rackTile.getLetter() == tileToRemove.getLetter() &&
                        rackTile.getValue() == tileToRemove.getValue() &&
                        rackTile.isBlank() == tileToRemove.isBlank()) {
                    tiles.remove(i);
                    removedCount++;
                    found = true;
                    break;
                }
            }

            if (!found) {
                System.out.println("WARNING: Could not find matching tile for " + tileToRemove.getLetter());
            }
        }

        System.out.println("Rack: Removed " + removedCount + " tiles. New size: " + tiles.size());
        return (removedCount == tilesToRemoveCopy.size());
    }

    public Tile getTile(int index) {
        if (index < 0 || index >= tiles.size()) {
            return null;
        }
        return tiles.get(index);
    }

    public List<Tile> getTiles() {
        return Collections.unmodifiableList(tiles);
    }

    public int size() {
        return tiles.size();
    }

    public boolean isEmpty() {
        return tiles.isEmpty();
    }

    public boolean isFull() {
        return tiles.size() >= RACK_SIZE;
    }

    public int getEmptySlots() {
        return RACK_SIZE - tiles.size();
    }

    public boolean containsLetter(char letter) {
        letter = Character.toUpperCase(letter);
        for (Tile tile : tiles) {
            if (tile.getLetter() == letter) {
                return true;
            }
        }
        return false;
    }

    public int getIndexOfLetter(char letter) {
        letter = Character.toUpperCase(letter);
        for (int i = 0; i < tiles.size(); i++) {
            if (tiles.get(i).getLetter() == letter) {
                return i;
            }
        }
        return -1;
    }

    public boolean containsBlank() {
        for (Tile tile : tiles) {
            if (tile.isBlank()) {
                return true;
            }
        }
        return false;
    }

    public int getIndexOfBlank() {
        for (int i = 0; i < tiles.size(); i++) {
            if (tiles.get(i).isBlank()) {
                return i;
            }
        }
        return -1;
    }

    public boolean swapTiles(int index1, int index2) {
        if (index1 < 0 || index1 >= tiles.size() || index2 < 0 || index2 >= tiles.size()) {
            return false;
        }
        Collections.swap(tiles, index1, index2);
        return true;
    }

    public void shuffle() {
        Collections.shuffle(tiles);
    }

    public int getTotalValue() {
        int total = 0;
        for (Tile tile : tiles) {
            total += tile.getValue();
        }
        return total;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Rack: ");
        for (Tile tile : tiles) {
            sb.append(tile.getLetter());
            if (tile.isBlank()) {
                sb.append("(*)");
            }
            sb.append(" ");
        }
        return sb.toString().trim();
    }
}