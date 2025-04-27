package edu.leicester.scrabble.util;

import edu.leicester.scrabble.model.Dictionary;
import edu.leicester.scrabble.model.*;
import java.awt.Point;
import java.util.*;

public class WordValidator {
    /**
     * Validates all words formed by a move
     */
    public static List<String> validateWords(Board board, Move move, List<Point> newTilePositions, Dictionary dictionary) {
        List<String> formedWords = new ArrayList<>();

        // Find the main word
        String mainWord = findMainWord(board, move);

        if (mainWord.length() < 2 || !dictionary.isValidWord(mainWord)) {
            return formedWords; // Empty list indicates invalid placement
        }

        formedWords.add(mainWord);

        // Check all crossing words
        for (Point p : newTilePositions) {
            String crossWord = findCrossWord(board, move.getDirection(), p);

            if (crossWord.length() >= 2) {
                if (!dictionary.isValidWord(crossWord)) {
                    return new ArrayList<>(); // Invalid crossing word
                }
                formedWords.add(crossWord);
            }
        }

        return formedWords;
    }

    /**
     * Find the main word formed by a move
     */
    private static String findMainWord(Board board, Move move) {
        int row = move.getStartRow();
        int col = move.getStartCol();
        Move.Direction direction = move.getDirection();

        if (direction == Move.Direction.HORIZONTAL) {
            int startCol = BoardUtils.findWordStart(board, row, col, true);
            return BoardUtils.getWordAt(board, row, startCol, Move.Direction.HORIZONTAL);
        } else {
            int startRow = BoardUtils.findWordStart(board, row, col, false);
            return BoardUtils.getWordAt(board, startRow, col, Move.Direction.VERTICAL);
        }
    }

    /**
     * Find crossing word at a specific position
     */
    private static String findCrossWord(Board board, Move.Direction direction, Point position) {
        if (direction == Move.Direction.HORIZONTAL) {
            int startRow = BoardUtils.findWordStart(board, position.x, position.y, false);
            return BoardUtils.getWordAt(board, startRow, position.y, Move.Direction.VERTICAL);
        } else {
            int startCol = BoardUtils.findWordStart(board, position.x, position.y, true);
            return BoardUtils.getWordAt(board, position.x, startCol, Move.Direction.HORIZONTAL);
        }
    }

    /**
     * Check if a move placement is valid (connects to existing tiles, etc.)
     */
    public static boolean isValidPlaceMove(Move move, Board board, Dictionary dictionary) {
        int startRow = move.getStartRow();
        int startCol = move.getStartCol();
        Move.Direction direction = move.getDirection();
        List<Tile> tiles = move.getTiles();

        // Basic validations
        if (tiles.isEmpty() ||
                startRow < 0 || startRow >= Board.SIZE ||
                startCol < 0 || startCol >= Board.SIZE) {
            return false;
        }

        // First move must cover center square
        if (board.isEmpty()) {
            boolean touchesCenter = checkTouchesCenter(move);
            return touchesCenter;
        }

        // Place tiles temporarily on a board copy to validate
        Board tempBoard = BoardUtils.copyBoard(board);
        List<Point> newTilePositions = placeTilesTemporarily(tempBoard, move);

        if (newTilePositions.isEmpty()) {
            return false; // Couldn't place all tiles
        }

        // Check if connected to existing tiles
        boolean connectsToExisting = checkConnectsToExisting(board, newTilePositions);

        // Validate formed words
        List<String> formedWords = validateWords(tempBoard, move, newTilePositions, dictionary);

        return !formedWords.isEmpty() && (connectsToExisting || hasConnectionThroughWords(board, tempBoard, formedWords, newTilePositions));
    }

    /**
     * Check if the move touches the center square (required for first move)
     */
    private static boolean checkTouchesCenter(Move move) {
        int startRow = move.getStartRow();
        int startCol = move.getStartCol();
        Move.Direction direction = move.getDirection();
        List<Tile> tiles = move.getTiles();

        if (direction == Move.Direction.HORIZONTAL) {
            return startRow == ScrabbleConstants.CENTER_SQUARE &&
                    startCol <= ScrabbleConstants.CENTER_SQUARE &&
                    startCol + tiles.size() > ScrabbleConstants.CENTER_SQUARE;
        } else {
            return startCol == ScrabbleConstants.CENTER_SQUARE &&
                    startRow <= ScrabbleConstants.CENTER_SQUARE &&
                    startRow + tiles.size() > ScrabbleConstants.CENTER_SQUARE;
        }
    }

    /**
     * Place tiles temporarily on the board for validation
     */
    private static List<Point> placeTilesTemporarily(Board tempBoard, Move move) {
        int currentRow = move.getStartRow();
        int currentCol = move.getStartCol();
        Move.Direction direction = move.getDirection();
        List<Tile> tiles = move.getTiles();
        List<Point> newTilePositions = new ArrayList<>();

        for (Tile tile : tiles) {
            // Skip occupied squares
            while (currentRow < Board.SIZE && currentCol < Board.SIZE &&
                    tempBoard.getSquare(currentRow, currentCol).hasTile()) {
                if (direction == Move.Direction.HORIZONTAL) {
                    currentCol++;
                } else {
                    currentRow++;
                }
            }

            // Check if we went out of bounds
            if (currentRow >= Board.SIZE || currentCol >= Board.SIZE) {
                return new ArrayList<>(); // Invalid placement
            }

            // Place the tile
            tempBoard.placeTile(currentRow, currentCol, tile);
            newTilePositions.add(new Point(currentRow, currentCol));

            // Move to next position
            if (direction == Move.Direction.HORIZONTAL) {
                currentCol++;
            } else {
                currentRow++;
            }
        }

        return newTilePositions;
    }

    /**
     * Check if any of the new tiles is adjacent to existing tiles
     */
    private static boolean checkConnectsToExisting(Board board, List<Point> newTilePositions) {
        for (Point p : newTilePositions) {
            if (BoardUtils.hasAdjacentTile(board, p.x, p.y)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if the move connects through formed words
     */
    private static boolean hasConnectionThroughWords(Board board, Board tempBoard,
                                                     List<String> formedWords, List<Point> newTilePositions) {
        for (String word : formedWords) {
            Point wordPos = findWordPosition(tempBoard, word);
            if (wordPos == null) continue;

            boolean isHorizontal = isWordHorizontal(tempBoard, word, wordPos);

            // Check if any tile in the word is on the original board
            if (wordContainsExistingTile(board, tempBoard, wordPos, isHorizontal, word.length(), newTilePositions)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if a word contains any existing tile from the original board
     */
    private static boolean wordContainsExistingTile(Board board, Board tempBoard, Point wordPos,
                                                    boolean isHorizontal, int wordLength, List<Point> newTilePositions) {
        int row = wordPos.x;
        int col = wordPos.y;

        for (int i = 0; i < wordLength; i++) {
            Point p = new Point(row, col);

            // If square has a tile in original board and is not one of our newly placed tiles
            if (board.getSquare(row, col).hasTile() && !containsPoint(newTilePositions, p)) {
                return true;
            }

            if (isHorizontal) {
                col++;
            } else {
                row++;
            }
        }
        return false;
    }

    /**
     * Find position of a word on the board
     */
    public static Point findWordPosition(Board board, String word) {
        // Check horizontal words
        for (int row = 0; row < Board.SIZE; row++) {
            for (int col = 0; col < Board.SIZE; col++) {
                String foundWord = BoardUtils.getWordAt(board, row, col, Move.Direction.HORIZONTAL);
                if (foundWord.equals(word)) {
                    return new Point(row, col);
                }
            }
        }

        // Check vertical words
        for (int row = 0; row < Board.SIZE; row++) {
            for (int col = 0; col < Board.SIZE; col++) {
                String foundWord = BoardUtils.getWordAt(board, row, col, Move.Direction.VERTICAL);
                if (foundWord.equals(word)) {
                    return new Point(row, col);
                }
            }
        }

        return null;
    }

    /**
     * Check if a word at a position is horizontal
     */
    public static boolean isWordHorizontal(Board board, String word, Point position) {
        String horizontalWord = BoardUtils.getWordAt(board, position.x, position.y, Move.Direction.HORIZONTAL);
        return horizontalWord.equals(word);
    }

    /**
     * Check if a list of points contains a specific point
     */
    private static boolean containsPoint(List<Point> points, Point target) {
        for (Point p : points) {
            if (p.x == target.x && p.y == target.y) {
                return true;
            }
        }
        return false;
    }
}