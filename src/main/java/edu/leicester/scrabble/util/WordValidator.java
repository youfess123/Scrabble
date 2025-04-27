package edu.leicester.scrabble.util;

import edu.leicester.scrabble.model.*;
import edu.leicester.scrabble.model.Dictionary;

import java.awt.Point;
import java.util.*;

public class WordValidator {

    /**
     * Validates words formed by a move
     */
    public static List<String> validateWords(Board board, Move move, List<Point> newTilePositions, Dictionary dictionary) {
        List<String> formedWords = new ArrayList<>();

        int row = move.getStartRow();
        int col = move.getStartCol();
        Move.Direction direction = move.getDirection();

        // Find main word
        String mainWord;
        if (direction == Move.Direction.HORIZONTAL) {
            int startCol = BoardUtils.findWordStart(board, row, col, true);
            mainWord = BoardUtils.getWordAt(board, row, startCol, Move.Direction.HORIZONTAL);
        } else {
            int startRow = BoardUtils.findWordStart(board, row, col, false);
            mainWord = BoardUtils.getWordAt(board, startRow, col, Move.Direction.VERTICAL);
        }

        if (mainWord.length() < 2) {
            return formedWords; // Word too short
        }

        if (!dictionary.isValidWord(mainWord)) {
            return formedWords; // Invalid word
        }

        formedWords.add(mainWord);

        // Check crossing words
        for (Point p : newTilePositions) {
            String crossWord;
            if (direction == Move.Direction.HORIZONTAL) {
                int startRow = BoardUtils.findWordStart(board, p.x, p.y, false);
                crossWord = BoardUtils.getWordAt(board, startRow, p.y, Move.Direction.VERTICAL);
            } else {
                int startCol = BoardUtils.findWordStart(board, p.x, p.y, true);
                crossWord = BoardUtils.getWordAt(board, p.x, startCol, Move.Direction.HORIZONTAL);
            }

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
     * Check if a move placement is valid
     */
    public static boolean isValidPlaceMove(Move move, Board board) {
        int startRow = move.getStartRow();
        int startCol = move.getStartCol();
        Move.Direction direction = move.getDirection();
        List<Tile> tiles = move.getTiles();

        if (tiles.isEmpty()) {
            return false;
        }

        if (startRow < 0 || startRow >= Board.SIZE || startCol < 0 || startCol >= Board.SIZE) {
            return false;
        }

        // First move must cover center square
        if (board.isEmpty()) {
            boolean touchesCenter = false;

            if (direction == Move.Direction.HORIZONTAL) {
                if (startRow == ScrabbleConstants.CENTER_SQUARE &&
                        startCol <= ScrabbleConstants.CENTER_SQUARE &&
                        startCol + tiles.size() > ScrabbleConstants.CENTER_SQUARE) {
                    touchesCenter = true;
                }
            } else {
                if (startCol == ScrabbleConstants.CENTER_SQUARE &&
                        startRow <= ScrabbleConstants.CENTER_SQUARE &&
                        startRow + tiles.size() > ScrabbleConstants.CENTER_SQUARE) {
                    touchesCenter = true;
                }
            }

            return touchesCenter;
        }

        // Subsequent moves must connect to existing tiles
        Board tempBoard = BoardUtils.copyBoard(board);

        int currentRow = startRow;
        int currentCol = startCol;
        boolean connectsToExisting = false;

        for (Tile tile : tiles) {
            while (currentRow < Board.SIZE && currentCol < Board.SIZE &&
                    tempBoard.getSquare(currentRow, currentCol).hasTile()) {
                if (direction == Move.Direction.HORIZONTAL) {
                    currentCol++;
                } else {
                    currentRow++;
                }
            }

            if (currentRow >= Board.SIZE || currentCol >= Board.SIZE) {
                return false; // Out of bounds
            }

            tempBoard.placeTile(currentRow, currentCol, tile);

            if (!connectsToExisting) {
                connectsToExisting = BoardUtils.hasAdjacentTile(board, currentRow, currentCol);
            }

            if (direction == Move.Direction.HORIZONTAL) {
                currentCol++;
            } else {
                currentRow++;
            }
        }

        return connectsToExisting;
    }
}