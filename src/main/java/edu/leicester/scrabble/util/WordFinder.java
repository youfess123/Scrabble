package edu.leicester.scrabble.util;

import edu.leicester.scrabble.model.*;
import edu.leicester.scrabble.model.Dictionary;

import java.awt.Point;
import java.util.*;

public class WordFinder {
    private final Dictionary dictionary;
    private final Board board;

    public WordFinder(Dictionary dictionary, Board board) {
        this.dictionary = dictionary;
        this.board = board;
    }

    public static class WordPlacement {
        private final String word;
        private final int row;
        private final int col;
        private final Move.Direction direction;
        private final List<Tile> tilesNeeded;
        private final int score;
        private final List<String> crossWords;

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
        public int getScore() { return score; }

        @Override
        public String toString() {
            return String.format("%s at (%d,%d) %s for %d points",
                    word, row + 1, col + 1,
                    direction == Move.Direction.HORIZONTAL ? "horizontal" : "vertical",
                    score);
        }
    }

    public List<WordPlacement> findAllPlacements(Rack rack) {
        List<WordPlacement> placements = new ArrayList<>();

        StringBuilder rackLetters = new StringBuilder();
        for (int i = 0; i < rack.size(); i++) {
            rackLetters.append(rack.getTile(i).getLetter());
        }

        if (board.isEmpty()) {
            findPlacementsForFirstMove(rackLetters.toString(), rack, placements);

        } else {
            List<Point> anchorPoints = findAnchorPoints();

            // For each anchor point, try to place words
            for (Point anchor : anchorPoints) {
                findPlacementsAtAnchor(anchor.x, anchor.y, Move.Direction.HORIZONTAL,
                        rackLetters.toString(), rack, placements);

                findPlacementsAtAnchor(anchor.x, anchor.y, Move.Direction.VERTICAL,
                        rackLetters.toString(), rack, placements);
            }
        }
        placements.sort((p1, p2) -> Integer.compare(p2.getScore(), p1.getScore()));
        return placements;
    }

    private List<Point> findAnchorPoints() {
        List<Point> anchors = new ArrayList<>();

        for (int row = 0; row < Board.SIZE; row++) {
            for (int col = 0; col < Board.SIZE; col++) {
                if (board.getSquare(row, col).hasTile()) {
                    continue;
                }

                if (hasAdjacentTile(row, col)) {
                    anchors.add(new Point(row, col));
                }
            }
        }

        return anchors;
    }

    private boolean hasAdjacentTile(int row, int col) {
        if (row > 0 && board.getSquare(row - 1, col).hasTile()) return true;
        if (row < Board.SIZE - 1 && board.getSquare(row + 1, col).hasTile()) return true;
        if (col > 0 && board.getSquare(row, col - 1).hasTile()) return true;
        if (col < Board.SIZE - 1 && board.getSquare(row, col + 1).hasTile()) return true;

        return false;
    }
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

    private void findPlacementsAtAnchor(int row, int col, Move.Direction direction,
                                        String rackLetters, Rack rack, List<WordPlacement> placements) {
        // For each letter in the rack
        for (char letter : getUniqueLetters(rackLetters)) {
            // Find words that can be formed with this letter at the anchor position
            findWordsWithLetterAt(row, col, letter, direction, rackLetters, rack, placements);
        }
    }

    private void findWordsWithLetterAt(int row, int col, char letter, Move.Direction direction,
                                       String rackLetters, Rack rack, List<WordPlacement> placements) {
        Board tempBoard = new Board();

        for (int r = 0; r < Board.SIZE; r++) {
            for (int c = 0; c < Board.SIZE; c++) {
                Square square = board.getSquare(r, c);
                if (square.hasTile()) {
                    tempBoard.placeTile(r, c, square.getTile());
                }
            }
        }

        String[] partialWords = getPartialWordsAt(row, col, direction);
        String prefix = partialWords[0];
        String suffix = partialWords[1];

        int letterIndex = -1;
        for (int i = 0; i < rack.size(); i++) {
            if (rack.getTile(i).getLetter() == letter) {
                letterIndex = i;
                break;
            }
        }

        if (letterIndex == -1) {
            return;
        }


        String potentialWord = prefix + letter + suffix;

        if (potentialWord.length() < 2) {
            return;
        }

        int startRow, startCol;
        if (direction == Move.Direction.HORIZONTAL) {
            startRow = row;
            startCol = col - prefix.length();
        } else {
            startRow = row - prefix.length();
            startCol = col;
        }

        if (startRow < 0 || startCol < 0) {
            return;
        }

        boolean validPlacement = true;
        List<Tile> tilesNeeded = new ArrayList<>();
        Set<Point> newTilePositions = new HashSet<>();

        Tile anchorTile = rack.getTile(letterIndex);
        tilesNeeded.add(anchorTile);
        newTilePositions.add(new Point(row, col));
        tempBoard.placeTile(row, col, anchorTile);

        for (int i = 0; i < prefix.length(); i++) {
            int r = direction == Move.Direction.HORIZONTAL ? startRow : startRow + i;
            int c = direction == Move.Direction.HORIZONTAL ? startCol + i : startCol;

            if (board.getSquare(r, c).hasTile()) {
                if (board.getSquare(r, c).getTile().getLetter() != prefix.charAt(i)) {
                    validPlacement = false;
                    break;
                }
            } else {
                char neededLetter = prefix.charAt(i);
                boolean found = false;

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

        for (int i = 0; i < suffix.length(); i++) {
            int r = direction == Move.Direction.HORIZONTAL ? row : row + i + 1;
            int c = direction == Move.Direction.HORIZONTAL ? col + i + 1 : col;

            if (board.getSquare(r, c).hasTile()) {
                if (board.getSquare(r, c).getTile().getLetter() != suffix.charAt(i)) {
                    validPlacement = false;
                    break;
                }
            } else {
                char neededLetter = suffix.charAt(i);
                boolean found = false;

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

        if (validPlacement) {
            if (!dictionary.isValidWord(potentialWord)) {
                return;
            }

            List<String> crossWords = new ArrayList<>();

            for (Point p : newTilePositions) {
                String crossWord;
                if (direction == Move.Direction.HORIZONTAL) {
                    crossWord = getWordAt(tempBoard, p.x, p.y, Move.Direction.VERTICAL);
                } else {
                    crossWord = getWordAt(tempBoard, p.x, p.y, Move.Direction.HORIZONTAL);
                }

                if (crossWord.length() >= 2) {
                    if (!dictionary.isValidWord(crossWord)) {
                        return;
                    }
                    crossWords.add(crossWord);
                }
            }

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

    // In WordFinder.java, replace the calculateScore method with this implementation:
    private int calculateScore(Board board, int startRow, int startCol, String word,
                               Move.Direction direction, Set<Point> newTilePositions,
                               List<String> crossWords) {
        int totalScore = 0;

        int wordScore = 0;
        int wordMultiplier = 1;

        int currentRow = startRow;
        int currentCol = startCol;

        for (int i = 0; i < word.length(); i++) {
            Square square = board.getSquare(currentRow, currentCol);
            Point currentPoint = new Point(currentRow, currentCol);

            Tile tile = square.getTile();
            // Set letterValue to 0 for blank tiles regardless of the letter
            int letterValue = tile.isBlank() ? 0 : tile.getValue();
            int letterScore = letterValue;

            if (newTilePositions.contains(currentPoint)) {
                if (square.getSquareType() == Square.SquareType.DOUBLE_LETTER) {
                    letterScore = letterValue * 2;
                } else if (square.getSquareType() == Square.SquareType.TRIPLE_LETTER) {
                    letterScore = letterValue * 3;
                }

                if (square.getSquareType() == Square.SquareType.DOUBLE_WORD ||
                        square.getSquareType() == Square.SquareType.CENTER) {
                    wordMultiplier *= 2;
                } else if (square.getSquareType() == Square.SquareType.TRIPLE_WORD) {
                    wordMultiplier *= 3;
                }
            }

            wordScore += letterScore;

            if (direction == Move.Direction.HORIZONTAL) {
                currentCol++;
            } else {
                currentRow++;
            }
        }

        totalScore += wordScore * wordMultiplier;

        // Add scores for crossing words
        for (String crossWord : crossWords) {
            int crossWordScore = calculateCrossWordScore(board, crossWord);
            totalScore += crossWordScore;
        }

        if (newTilePositions.size() == 7) {
            totalScore += ScrabbleConstants.BINGO_BONUS; // 50 points bingo bonus
        }

        return totalScore;
    }

    // Add this helper method to calculate cross word scores
    private int calculateCrossWordScore(Board board, String word) {
        // Find the word on the board
        Point wordPosition = findWordPosition(board, word);
        if (wordPosition == null) return 0;

        int row = wordPosition.x;
        int col = wordPosition.y;
        boolean isHorizontal = wordOrientation(board, row, col, word);

        int score = 0;
        int wordMultiplier = 1;

        // Calculate the score for the cross word
        for (int i = 0; i < word.length(); i++) {
            Square square = board.getSquare(row, col);
            Tile tile = square.getTile();

            // Ensure blank tiles have a value of 0
            int letterValue = tile.isBlank() ? 0 : tile.getValue();
            int letterScore = letterValue;

            if (!square.isSquareTypeUsed()) {
                if (square.getSquareType() == Square.SquareType.DOUBLE_LETTER) {
                    letterScore = letterValue * 2;
                } else if (square.getSquareType() == Square.SquareType.TRIPLE_LETTER) {
                    letterScore = letterValue * 3;
                }

                if (square.getSquareType() == Square.SquareType.DOUBLE_WORD ||
                        square.getSquareType() == Square.SquareType.CENTER) {
                    wordMultiplier *= 2;
                } else if (square.getSquareType() == Square.SquareType.TRIPLE_WORD) {
                    wordMultiplier *= 3;
                }
            }

            score += letterScore;

            if (isHorizontal) {
                col++;
            } else {
                row++;
            }
        }

        return score * wordMultiplier;
    }

    // Add helper method to find word position on board
    private Point findWordPosition(Board board, String word) {
        // Check horizontal words
        for (int r = 0; r < Board.SIZE; r++) {
            for (int c = 0; c < Board.SIZE; c++) {
                if (getWordAt(board, r, c, Move.Direction.HORIZONTAL).equals(word)) {
                    return new Point(r, c);
                }
            }
        }

        // Check vertical words
        for (int r = 0; r < Board.SIZE; r++) {
            for (int c = 0; c < Board.SIZE; c++) {
                if (getWordAt(board, r, c, Move.Direction.VERTICAL).equals(word)) {
                    return new Point(r, c);
                }
            }
        }

        return null;
    }

    // Helper method to determine word orientation
    private boolean wordOrientation(Board board, int row, int col, String word) {
        return getWordAt(board, row, col, Move.Direction.HORIZONTAL).equals(word);
    }


    private Set<Character> getUniqueLetters(String letters) {
        Set<Character> unique = new HashSet<>();
        for (char c : letters.toCharArray()) {
            unique.add(c);
        }
        return unique;
    }
}