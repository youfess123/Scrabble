package edu.leicester.scrabble.util;

import edu.leicester.scrabble.model.*;
import edu.leicester.scrabble.model.Dictionary;

import java.awt.Point;
import java.util.*;

/**
 * Utility class for finding valid word placements on a Scrabble board.
 *
 * This class leverages the GADDAG data structure to efficiently find all
 * possible valid word placements from a player's rack of tiles. It is used
 * primarily by the computer player AI, but can also be used for hint systems
 * and move validation.
 */
public class WordFinder {
    private final Dictionary dictionary;
    private final Board board;

    /**
     * Creates a new WordFinder for the specified game state.
     *
     * @param dictionary The game dictionary
     * @param board The current game board
     */
    public WordFinder(Dictionary dictionary, Board board) {
        this.dictionary = dictionary;
        this.board = board;
    }

    /**
     * Represents a potential move found by the word finder.
     */
    public static class WordPlacement {
        private final String word;
        private final int row;
        private final int col;
        private final Move.Direction direction;
        private final List<Tile> tilesNeeded;
        private final int score;
        private final List<String> crossWords;

        /**
         * Creates a new WordPlacement.
         *
         * @param word The word to be placed
         * @param row The starting row
         * @param col The starting column
         * @param direction The direction (horizontal or vertical)
         * @param tilesNeeded The tiles needed from the rack
         * @param score The estimated score
         * @param crossWords Additional cross-words formed
         */
        public WordPlacement(String word, int row, int col, Move.Direction direction,
                             List<Tile> tilesNeeded, int score, List<String> crossWords) {
            this.word = word;
            this.row = row;
            this.col = col;
            this.direction = direction;
            this.tilesNeeded = new ArrayList<>(tilesNeeded);
            this.score = score;
            this.crossWords = new ArrayList<>(crossWords);
        }

        /**
         * Creates a Move object from this WordPlacement.
         *
         * @param player The player making the move
         * @return A Move object
         */
        public Move toMove(Player player) {
            Move move = Move.createPlaceMove(player, row, col, direction);
            move.addTiles(tilesNeeded);

            List<String> allWords = new ArrayList<>(crossWords);
            allWords.add(0, word); // Main word first
            move.setFormedWords(allWords);

            move.setScore(score);
            return move;
        }

        // Getters
        public String getWord() { return word; }
        public int getRow() { return row; }
        public int getCol() { return col; }
        public Move.Direction getDirection() { return direction; }
        public List<Tile> getTilesNeeded() { return tilesNeeded; }
        public int getScore() { return score; }
        public List<String> getCrossWords() { return crossWords; }

        @Override
        public String toString() {
            return String.format("%s at (%d,%d) %s for %d points",
                    word, row + 1, col + 1,
                    direction == Move.Direction.HORIZONTAL ? "horizontal" : "vertical",
                    score);
        }
    }

    /**
     * Finds all possible word placements for a rack of tiles.
     *
     * @param rack The player's rack
     * @return List of possible word placements, sorted by score
     */
    public List<WordPlacement> findAllPlacements(Rack rack) {
        List<WordPlacement> placements = new ArrayList<>();

        // Convert rack to a string of letters for GADDAG lookup
        StringBuilder rackLetters = new StringBuilder();
        for (int i = 0; i < rack.size(); i++) {
            rackLetters.append(rack.getTile(i).getLetter());
        }

        // Special case for empty board - only consider center square
        if (board.isEmpty()) {
            findPlacementsForFirstMove(rackLetters.toString(), rack, placements);

        } else {
            // Find all anchor squares (empty squares adjacent to placed tiles)
            List<Point> anchorPoints = findAnchorPoints();

            // For each anchor point, try to place words
            for (Point anchor : anchorPoints) {
                // Try horizontal placements
                findPlacementsAtAnchor(anchor.x, anchor.y, Move.Direction.HORIZONTAL,
                        rackLetters.toString(), rack, placements);

                // Try vertical placements
                findPlacementsAtAnchor(anchor.x, anchor.y, Move.Direction.VERTICAL,
                        rackLetters.toString(), rack, placements);
            }
        }

        // Sort placements by score (highest first)
        placements.sort((p1, p2) -> Integer.compare(p2.getScore(), p1.getScore()));

        return placements;
    }

    /**
     * Finds anchor points (empty squares adjacent to placed tiles).
     *
     * @return List of anchor points
     */
    private List<Point> findAnchorPoints() {
        List<Point> anchors = new ArrayList<>();

        for (int row = 0; row < Board.SIZE; row++) {
            for (int col = 0; col < Board.SIZE; col++) {
                // Skip squares that already have tiles
                if (board.getSquare(row, col).hasTile()) {
                    continue;
                }

                // Check if this square is adjacent to an existing tile
                if (hasAdjacentTile(row, col)) {
                    anchors.add(new Point(row, col));
                }
            }
        }

        return anchors;
    }

    /**
     * Checks if a square has any adjacent tiles.
     *
     * @param row The row
     * @param col The column
     * @return true if there are adjacent tiles
     */
    private boolean hasAdjacentTile(int row, int col) {
        // Check all four adjacent directions
        if (row > 0 && board.getSquare(row - 1, col).hasTile()) return true;
        if (row < Board.SIZE - 1 && board.getSquare(row + 1, col).hasTile()) return true;
        if (col > 0 && board.getSquare(row, col - 1).hasTile()) return true;
        if (col < Board.SIZE - 1 && board.getSquare(row, col + 1).hasTile()) return true;

        return false;
    }

    /**
     * Finds placements for the first move (must go through center).
     *
     * @param rackLetters The available letters
     * @param rack The player's rack
     * @param placements Output list of placements
     */
    private void findPlacementsForFirstMove(String rackLetters, Rack rack, List<WordPlacement> placements) {
        // For each letter in the rack
        for (char letter : getUniqueLetters(rackLetters)) {
            // Try horizontal words through center
            findWordsWithLetterAt(ScrabbleConstants.CENTER_SQUARE, ScrabbleConstants.CENTER_SQUARE,
                    letter, Move.Direction.HORIZONTAL, rackLetters, rack, placements);

            // Try vertical words through center
            findWordsWithLetterAt(ScrabbleConstants.CENTER_SQUARE, ScrabbleConstants.CENTER_SQUARE,
                    letter, Move.Direction.VERTICAL, rackLetters, rack, placements);
        }
    }

    /**
     * Finds placements at a specific anchor point.
     *
     * @param row The anchor row
     * @param col The anchor column
     * @param direction The direction to try
     * @param rackLetters The available letters
     * @param rack The player's rack
     * @param placements Output list of placements
     */
    private void findPlacementsAtAnchor(int row, int col, Move.Direction direction,
                                        String rackLetters, Rack rack, List<WordPlacement> placements) {
        // For each letter in the rack
        for (char letter : getUniqueLetters(rackLetters)) {
            // Find words that can be formed with this letter at the anchor position
            findWordsWithLetterAt(row, col, letter, direction, rackLetters, rack, placements);
        }
    }

    /**
     * Finds words that can be formed with a specific letter at a position.
     *
     * @param row The row
     * @param col The column
     * @param letter The letter to place
     * @param direction The direction
     * @param rackLetters The available letters
     * @param rack The player's rack
     * @param placements Output list of placements
     */
    private void findWordsWithLetterAt(int row, int col, char letter, Move.Direction direction,
                                       String rackLetters, Rack rack, List<WordPlacement> placements) {
        // Create a temporary board to test placements
        Board tempBoard = new Board();

        // Copy existing board state
        for (int r = 0; r < Board.SIZE; r++) {
            for (int c = 0; c < Board.SIZE; c++) {
                Square square = board.getSquare(r, c);
                if (square.hasTile()) {
                    tempBoard.placeTile(r, c, square.getTile());
                }
            }
        }

        // Get partial words already on the board
        String[] partialWords = getPartialWordsAt(row, col, direction);
        String prefix = partialWords[0];
        String suffix = partialWords[1];

        // Find the index of letter in the rack
        int letterIndex = -1;
        for (int i = 0; i < rack.size(); i++) {
            if (rack.getTile(i).getLetter() == letter) {
                letterIndex = i;
                break;
            }
        }

        if (letterIndex == -1) {
            return; // Letter not found in rack
        }

        // Update rack letters to remove the used letter
        String updatedRack = rackLetters.replaceFirst(String.valueOf(letter), "");

        // Get potential words from GADDAG
        String potentialWord = prefix + letter + suffix;

        // Skip if too short
        if (potentialWord.length() < 2) {
            return;
        }

        // Find the starting position of the word
        int startRow, startCol;
        if (direction == Move.Direction.HORIZONTAL) {
            startRow = row;
            startCol = col - prefix.length();
        } else {
            startRow = row - prefix.length();
            startCol = col;
        }

        // Validate bounds
        if (startRow < 0 || startCol < 0) {
            return;
        }

        // Place the potential word on the temp board
        boolean validPlacement = true;
        List<Tile> tilesNeeded = new ArrayList<>();
        Set<Point> newTilePositions = new HashSet<>();

        // Add the tile at the anchor position
        Tile anchorTile = rack.getTile(letterIndex);
        tilesNeeded.add(anchorTile);
        newTilePositions.add(new Point(row, col));
        tempBoard.placeTile(row, col, anchorTile);

        // Place prefix tiles
        for (int i = 0; i < prefix.length(); i++) {
            int r = direction == Move.Direction.HORIZONTAL ? startRow : startRow + i;
            int c = direction == Move.Direction.HORIZONTAL ? startCol + i : startCol;

            // If this position already has a tile, make sure it matches
            if (board.getSquare(r, c).hasTile()) {
                if (board.getSquare(r, c).getTile().getLetter() != prefix.charAt(i)) {
                    validPlacement = false;
                    break;
                }
            } else {
                // Need to place a tile from rack
                char neededLetter = prefix.charAt(i);
                boolean found = false;

                // Find the tile in the remaining rack
                for (int j = 0; j < rack.size(); j++) {
                    if (rack.getTile(j).getLetter() == neededLetter && !tilesNeeded.contains(rack.getTile(j))) {
                        tilesNeeded.add(rack.getTile(j));
                        tempBoard.placeTile(r, c, rack.getTile(j));
                        newTilePositions.add(new Point(r, c));
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    validPlacement = false;
                    break;
                }
            }
        }

        // Place suffix tiles
        for (int i = 0; i < suffix.length(); i++) {
            int r = direction == Move.Direction.HORIZONTAL ? row : row + i + 1;
            int c = direction == Move.Direction.HORIZONTAL ? col + i + 1 : col;

            // If this position already has a tile, make sure it matches
            if (board.getSquare(r, c).hasTile()) {
                if (board.getSquare(r, c).getTile().getLetter() != suffix.charAt(i)) {
                    validPlacement = false;
                    break;
                }
            } else {
                // Need to place a tile from rack
                char neededLetter = suffix.charAt(i);
                boolean found = false;

                // Find the tile in the remaining rack
                for (int j = 0; j < rack.size(); j++) {
                    if (rack.getTile(j).getLetter() == neededLetter && !tilesNeeded.contains(rack.getTile(j))) {
                        tilesNeeded.add(rack.getTile(j));
                        tempBoard.placeTile(r, c, rack.getTile(j));
                        newTilePositions.add(new Point(r, c));
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    validPlacement = false;
                    break;
                }
            }
        }

        // If the placement is valid, check for valid words
        if (validPlacement) {
            // Ensure the main word is valid
            if (!dictionary.isValidWord(potentialWord)) {
                return;
            }

            // Find all cross-words formed
            List<String> crossWords = new ArrayList<>();

            for (Point p : newTilePositions) {
                String crossWord;
                if (direction == Move.Direction.HORIZONTAL) {
                    // Check for vertical cross-word
                    crossWord = getWordAt(tempBoard, p.x, p.y, Move.Direction.VERTICAL);
                } else {
                    // Check for horizontal cross-word
                    crossWord = getWordAt(tempBoard, p.x, p.y, Move.Direction.HORIZONTAL);
                }

                if (crossWord.length() >= 2) {
                    if (!dictionary.isValidWord(crossWord)) {
                        return; // Invalid cross-word
                    }
                    crossWords.add(crossWord);
                }
            }

            // Calculate score
            int score = calculateScore(tempBoard, startRow, startCol, potentialWord,
                    direction, newTilePositions, crossWords);

            // Create and add the word placement
            WordPlacement placement = new WordPlacement(
                    potentialWord, startRow, startCol, direction,
                    tilesNeeded, score, crossWords
            );

            placements.add(placement);
        }
    }

    /**
     * Gets the partial words already on the board at a position.
     *
     * @param row The row
     * @param col The column
     * @param direction The direction
     * @return Array with [prefix, suffix]
     */
    private String[] getPartialWordsAt(int row, int col, Move.Direction direction) {
        StringBuilder prefix = new StringBuilder();
        StringBuilder suffix = new StringBuilder();

        if (direction == Move.Direction.HORIZONTAL) {
            // Get prefix (letters to the left)
            int c = col - 1;
            while (c >= 0 && board.getSquare(row, c).hasTile()) {
                prefix.insert(0, board.getSquare(row, c).getTile().getLetter());
                c--;
            }

            // Get suffix (letters to the right)
            c = col + 1;
            while (c < Board.SIZE && board.getSquare(row, c).hasTile()) {
                suffix.append(board.getSquare(row, c).getTile().getLetter());
                c++;
            }
        } else {  // VERTICAL
            // Get prefix (letters above)
            int r = row - 1;
            while (r >= 0 && board.getSquare(r, col).hasTile()) {
                prefix.insert(0, board.getSquare(r, col).getTile().getLetter());
                r--;
            }

            // Get suffix (letters below)
            r = row + 1;
            while (r < Board.SIZE && board.getSquare(r, col).hasTile()) {
                suffix.append(board.getSquare(r, col).getTile().getLetter());
                r++;
            }
        }

        return new String[] {prefix.toString(), suffix.toString()};
    }

    /**
     * Gets the word at a position in a given direction.
     *
     * @param board The board
     * @param row The starting row
     * @param col The starting column
     * @param direction The direction
     * @return The word string
     */
    private String getWordAt(Board board, int row, int col, Move.Direction direction) {
        // Find the starting position of the word
        int startRow, startCol;

        if (direction == Move.Direction.HORIZONTAL) {
            startRow = row;
            startCol = col;

            // Move to the leftmost letter
            while (startCol > 0 && board.getSquare(startRow, startCol - 1).hasTile()) {
                startCol--;
            }
        } else {
            startRow = row;
            startCol = col;

            // Move to the topmost letter
            while (startRow > 0 && board.getSquare(startRow - 1, startCol).hasTile()) {
                startRow--;
            }
        }

        // Build the word
        StringBuilder word = new StringBuilder();
        int currentRow = startRow;
        int currentCol = startCol;

        while (currentRow < Board.SIZE && currentCol < Board.SIZE) {
            Square square = board.getSquare(currentRow, currentCol);

            if (!square.hasTile()) {
                break;
            }

            word.append(square.getTile().getLetter());

            if (direction == Move.Direction.HORIZONTAL) {
                currentCol++;
            } else {
                currentRow++;
            }
        }

        return word.toString();
    }

    /**
     * Calculates the score for a word placement.
     *
     * @param board The board
     * @param startRow The starting row
     * @param startCol The starting column
     * @param word The word
     * @param direction The direction
     * @param newTilePositions Positions of new tiles
     * @param crossWords Cross-words formed
     * @return The total score
     */
    private int calculateScore(Board board, int startRow, int startCol, String word,
                               Move.Direction direction, Set<Point> newTilePositions,
                               List<String> crossWords) {
        int totalScore = 0;

        // Calculate score for main word
        int wordScore = 0;
        int wordMultiplier = 1;

        int currentRow = startRow;
        int currentCol = startCol;

        for (int i = 0; i < word.length(); i++) {
            Square square = board.getSquare(currentRow, currentCol);
            Point currentPoint = new Point(currentRow, currentCol);

            int letterValue = square.getTile().getValue();
            int letterScore = letterValue;

            // Apply premium squares for new tiles only
            if (newTilePositions.contains(currentPoint)) {
                if (square.getSquareType() == Square.SquareType.DOUBLE_LETTER) {
                    letterScore = letterValue * 2;
                } else if (square.getSquareType() == Square.SquareType.TRIPLE_LETTER) {
                    letterScore = letterValue * 3;
                }

                // Collect word multipliers
                if (square.getSquareType() == Square.SquareType.DOUBLE_WORD ||
                        square.getSquareType() == Square.SquareType.CENTER) {
                    wordMultiplier *= 2;
                } else if (square.getSquareType() == Square.SquareType.TRIPLE_WORD) {
                    wordMultiplier *= 3;
                }
            }

            wordScore += letterScore;

            // Move to next position
            if (direction == Move.Direction.HORIZONTAL) {
                currentCol++;
            } else {
                currentRow++;
            }
        }

        // Apply word multiplier
        totalScore += wordScore * wordMultiplier;

        // Calculate scores for cross-words
        for (String crossWord : crossWords) {
            // Find the score for each cross-word
            // This is a simplified calculation - in a real implementation
            // you would need to calculate this precisely
            totalScore += crossWord.length() * 2; // Simple estimate
        }

        // Add bonus for using all tiles (Bingo)
        if (newTilePositions.size() == 7) {
            totalScore += 50; // Bingo bonus
        }

        return totalScore;
    }

    /**
     * Gets unique letters from a string.
     *
     * @param letters The input string
     * @return Set of unique letters
     */
    private Set<Character> getUniqueLetters(String letters) {
        Set<Character> unique = new HashSet<>();
        for (char c : letters.toCharArray()) {
            unique.add(c);
        }
        return unique;
    }
}