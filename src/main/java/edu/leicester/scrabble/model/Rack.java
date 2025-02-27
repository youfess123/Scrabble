package edu.leicester.scrabble.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Rack {
    public static final int RACK_SIZE = 7; //hahahahahahahhahah
    private final List<Tile> tiles;

    public Rack() {
        this.tiles = new ArrayList<>(RACK_SIZE);
    }

    public boolean addTile(Tile tile) {
        if (tiles.size() >= RACK_SIZE) {
            return false;
        }
        tiles.add(tile);
        return true;
    }

    public int addTiles(List<Tile> tilesToAdd) {
        int count = 0;
        for (Tile tile : tilesToAdd) {
            if (addTile(tile)) {
                count++;
            } else {
                break;
            }
        }
        return count;
    }

    public Tile removeTile(int index) {
        if (index < 0 || index >= tiles.size()) {
            return null;
        }
        return tiles.remove(index);
    }

    public boolean removeTile(Tile tile) {
        return tiles.remove(tile);
    }

    public boolean removeTiles(List<Tile> tilesToRemove) {
        return tiles.removeAll(tilesToRemove);
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
