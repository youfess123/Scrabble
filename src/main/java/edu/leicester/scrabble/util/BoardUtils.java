package edu.leicester.scrabble.util;

import edu.leicester.scrabble.model.Board;
import edu.leicester.scrabble.model.Square;
import edu.leicester.scrabble.model.Tile;
import edu.leicester.scrabble.model.Move;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

public class BoardUtils {

    /**
     * Creates a deep copy of the given board
     */
    public static Board copyBoard(Board originalBoard) {
        Board copy = new Board();
        for (int r = 0; r < Board.SIZE; r++) {
            for (int c = 0; c < Board.SIZE; c++) {
                Square square = originalBoard.getSquare(r, c);
                if (square.hasTile()) {
                    copy.placeTile(r, c, square.getTile());
                }
            }
        }
        return copy;
    }

    /**
     * Checks if a position has an adjacent tile
     */
    public static boolean hasAdjacentTile(Board board, int row, int col) {
        if (row > 0 && board.getSquare(row - 1, col).hasTile()) return true;
        if (row < Board.SIZE - 1 && board.getSquare(row + 1, col).hasTile()) return true;
        if (col > 0 && board.getSquare(row, col - 1).hasTile()) return true;
        if (col < Board.SIZE - 1 && board.getSquare(row, col + 1).hasTile()) return true;
        return false;
    }

    /**
     * Finds all anchor points (empty squares adjacent to placed tiles)
     */
    public static List<Point> findAnchorPoints(Board board) {
        List<Point> anchors = new ArrayList<>();
        for (int row = 0; row < Board.SIZE; row++) {
            for (int col = 0; col < Board.SIZE; col++) {
                if (board.getSquare(row, col).hasTile()) {
                    continue;
                }
                if (hasAdjacentTile(board, row, col)) {
                    anchors.add(new Point(row, col));
                }
            }
        }
        return anchors;
    }

    /**
     * Gets partial words from a board position
     */
    public static String[] getPartialWordsAt(Board board, int row, int col, Move.Direction direction) {
        StringBuilder prefix = new StringBuilder();
        StringBuilder suffix = new StringBuilder();

        if (direction == Move.Direction.HORIZONTAL) {
            int c = col - 1;
            while (c >= 0 && board.getSquare(row, c).hasTile()) {
                prefix.insert(0, board.getSquare(row, c).getTile().getLetter());
                c--;
            }
            c = col + 1;
            while (c < Board.SIZE && board.getSquare(row, c).hasTile()) {
                suffix.append(board.getSquare(row, c).getTile().getLetter());
                c++;
            }
        } else {
            int r = row - 1;
            while (r >= 0 && board.getSquare(r, col).hasTile()) {
                prefix.insert(0, board.getSquare(r, col).getTile().getLetter());
                r--;
            }
            r = row + 1;
            while (r < Board.SIZE && board.getSquare(r, col).hasTile()) {
                suffix.append(board.getSquare(r, col).getTile().getLetter());
                r++;
            }
        }

        return new String[] { prefix.toString(), suffix.toString() };
    }

    /**
     * Gets word at position in direction
     */
    public static String getWordAt(Board board, int row, int col, Move.Direction direction) {
        StringBuilder word = new StringBuilder();
        int currentRow = row;
        int currentCol = col;

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
     * Finds the start position of a word
     */
    public static int findWordStart(Board board, int row, int col, boolean isHorizontal) {
        int position = isHorizontal ? col : row;
        while (position > 0) {
            int prevPos = position - 1;
            Square square = isHorizontal ?
                    board.getSquare(row, prevPos) :
                    board.getSquare(prevPos, col);
            if (!square.hasTile()) {
                break;
            }
            position = prevPos;
        }
        return position;
    }
}