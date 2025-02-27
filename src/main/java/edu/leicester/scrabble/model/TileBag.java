package edu.leicester.scrabble.model;

import java.util.*;

public class TileBag {
    private final List<Tile> tiles;
    private final Random random;

    public class LetterInfo {
        private final int count;
        private final int value;

        public LetterInfo(int count, int value) {
            this.count = count;
            this.value = value;
        }

        public int getCount() { return count; }
        public int getValue() { return value; }
    }

    private static final Map<Character, LetterInfo> LETTER_DATA = new HashMap<>();{
        LETTER_DATA.put('A',new LetterInfo(9,1));
        LETTER_DATA.put('B',new LetterInfo(2,3));
        LETTER_DATA.put('C',new LetterInfo(2,3));
        LETTER_DATA.put('D',new LetterInfo(4,2));
        LETTER_DATA.put('E',new LetterInfo(12,1));
        LETTER_DATA.put('F',new LetterInfo(2,4));
        LETTER_DATA.put('G',new LetterInfo(3,2));
        LETTER_DATA.put('H',new LetterInfo(2,4));
        LETTER_DATA.put('I',new LetterInfo(9,1));
        LETTER_DATA.put('J',new LetterInfo(1,8));
        LETTER_DATA.put('K',new LetterInfo(1,5));
        LETTER_DATA.put('L',new LetterInfo(4,1));
        LETTER_DATA.put('M',new LetterInfo(2,3));
        LETTER_DATA.put('N',new LetterInfo(6,1));
        LETTER_DATA.put('O',new LetterInfo(8,1));
        LETTER_DATA.put('P',new LetterInfo(2,3));
        LETTER_DATA.put('Q',new LetterInfo(1,10));
        LETTER_DATA.put('R',new LetterInfo(6,1));
        LETTER_DATA.put('S',new LetterInfo(4,1));
        LETTER_DATA.put('T',new LetterInfo(6,1));
        LETTER_DATA.put('U',new LetterInfo(4,1));
        LETTER_DATA.put('V',new LetterInfo(2,4));
        LETTER_DATA.put('W',new LetterInfo(2,4));
        LETTER_DATA.put('X',new LetterInfo(1,8));
        LETTER_DATA.put('Y',new LetterInfo(2,4));
        LETTER_DATA.put('Z',new LetterInfo(1,10));
        LETTER_DATA.put('*',new LetterInfo(2,0));
    }

    public TileBag() {
        this.tiles = new ArrayList<>();
        this.random = new Random();
        initialiseTiles();
        shuffle();
    }

    private void initialiseTiles() {
        for (Map.Entry<Character, LetterInfo> entry : LETTER_DATA.entrySet()) {
            char letter = entry.getKey();

            LetterInfo  info = LETTER_DATA.get(letter);
            int count = info.getCount();
            int value = info.getValue();

            for (int i = 0; i < count; i++) {
                tiles.add(new Tile(letter, value));
            }
        }
    }

    public void shuffle() {
        Collections.shuffle(tiles, random);
    }

    public void returnTiles(List<Tile> tilesToReturn) {
        if (tilesToReturn == null || tilesToReturn.isEmpty()) {
            System.out.println("WARNING: No tiles to return to bag");
            return;
        }

        int beforeCount = tiles.size();

        // Add all tiles back to the bag
        this.tiles.addAll(tilesToReturn);

        int afterCount = tiles.size();
        System.out.println("TileBag: Added " + (afterCount - beforeCount) + " tiles to bag");

        // Shuffle the bag to randomize the tiles
        shuffle();
    }

    public List<Tile> drawTiles(int count) {
        List<Tile> drawnTiles = new ArrayList<>();

        if (count <= 0) {
            System.out.println("WARNING: Tried to draw " + count + " tiles");
            return drawnTiles;
        }

        System.out.println("TileBag: Attempting to draw " + count + " tiles. Available: " + tiles.size());

        // Can't draw more tiles than available
        int tilesToDraw = Math.min(count, tiles.size());

        for (int i = 0; i < tilesToDraw; i++) {
            Tile tile = drawTile();
            if (tile != null) {
                drawnTiles.add(tile);
            }
        }

        System.out.println("TileBag: Drew " + drawnTiles.size() + " tiles. Remaining: " + tiles.size());

        return drawnTiles;
    }

    public Tile drawTile() {
        if (tiles.isEmpty()) {
            System.out.println("WARNING: Tile bag is empty");
            return null;
        }
        return tiles.remove(tiles.size() - 1);
    }

    public int getTileCount() {
        return tiles.size();
    }

    public boolean isEmpty() {
        return tiles.isEmpty();
    }

    public static int getPointValue(char letter) {
        LetterInfo info = LETTER_DATA.get(letter);
        return info.getValue();
    }

    public Map<Character, Integer> getRemainingDistribution() {
        Map<Character, Integer> distribution = new HashMap<>();
        for (Tile tile : tiles) {
            char letter = tile.getLetter();
            distribution.put(letter, distribution.getOrDefault(letter, 0) + 1);
        }
        return distribution;
    }
}
