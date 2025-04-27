package edu.leicester.scrabble.util;

import edu.leicester.scrabble.model.Board;
import edu.leicester.scrabble.model.Square;
import edu.leicester.scrabble.model.Tile;
import edu.leicester.scrabble.model.Move;

import java.awt.Point;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
     * Checks if a position is diagonally adjacent to any tile
     */
    public static boolean hasDiagonalAdjacentTile(Board board, int row, int col) {
        if (row > 0 && col > 0 && board.getSquare(row - 1, col - 1).hasTile()) return true;
        if (row > 0 && col < Board.SIZE - 1 && board.getSquare(row - 1, col + 1).hasTile()) return true;
        if (row < Board.SIZE - 1 && col > 0 && board.getSquare(row + 1, col - 1).hasTile()) return true;
        if (row < Board.SIZE - 1 && col < Board.SIZE - 1 && board.getSquare(row + 1, col + 1).hasTile()) return true;
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
        } else {
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

    /**
     * Gets all squares for a word at a position
     */
    public static List<Square> getWordSquares(Board board, int row, int col, Move.Direction direction) {
        List<Square> squares = new ArrayList<>();
        int currentRow = row;
        int currentCol = col;

        while (currentRow < Board.SIZE && currentCol < Board.SIZE) {
            Square square = board.getSquare(currentRow, currentCol);
            if (!square.hasTile()) {
                break;
            }

            squares.add(square);

            if (direction == Move.Direction.HORIZONTAL) {
                currentCol++;
            } else {
                currentRow++;
            }
        }

        return squares;
    }

    /**
     * Check if a move placement touches the center square
     */
    public static boolean touchesCenterSquare(Move move) {
        int startRow = move.getStartRow();
        int startCol = move.getStartCol();
        Move.Direction direction = move.getDirection();
        List<Tile> tiles = move.getTiles();

        if (direction == Move.Direction.HORIZONTAL) {
            if (startRow == ScrabbleConstants.CENTER_SQUARE) {
                int endCol = startCol + tiles.size() - 1;
                return startCol <= ScrabbleConstants.CENTER_SQUARE &&
                        endCol >= ScrabbleConstants.CENTER_SQUARE;
            }
        } else {
            if (startCol == ScrabbleConstants.CENTER_SQUARE) {
                int endRow = startRow + tiles.size() - 1;
                return startRow <= ScrabbleConstants.CENTER_SQUARE &&
                        endRow >= ScrabbleConstants.CENTER_SQUARE;
            }
        }

        return false;
    }

    /**
     * Get all tiles at specific positions on a board
     */
    public static List<Tile> getTilesAtPositions(Board board, Set<Point> positions) {
        List<Tile> tiles = new ArrayList<>();

        for (Point p : positions) {
            if (p.x >= 0 && p.x < Board.SIZE && p.y >= 0 && p.y < Board.SIZE) {
                Square square = board.getSquare(p.x, p.y);
                if (square.hasTile()) {
                    tiles.add(square.getTile());
                }
            }
        }

        return tiles;
    }

    /**
     * Find all tiles involved in a word placement
     */
    public static Set<Point> getTilePositionsForWord(Board board, int startRow, int startCol,
                                                     String word, Move.Direction direction) {
        Set<Point> positions = new HashSet<>();
        int row = startRow;
        int col = startCol;

        for (int i = 0; i < word.length(); i++) {
            if (row < Board.SIZE && col < Board.SIZE) {
                positions.add(new Point(row, col));

                if (direction == Move.Direction.HORIZONTAL) {
                    col++;
                } else {
                    row++;
                }
            }
        }

        return positions;
    }

    /**
     * Convert a list of squares to a string
     */
    public static String squaresToString(List<Square> squares) {
        StringBuilder word = new StringBuilder();
        for (Square square : squares) {
            if (square.hasTile()) {
                word.append(square.getTile().getLetter());
            }
        }
        return word.toString();
    }
}