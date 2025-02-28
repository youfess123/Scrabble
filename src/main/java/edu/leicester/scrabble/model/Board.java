package edu.leicester.scrabble.model;

import java.util.ArrayList;
import java.util.List;

public class Board {
    public static final int SIZE = 15;
    private final Square[][] board;

    public Board() {
        board = new Square[SIZE][SIZE];
        initialiseBoard();
    }

    private void initialiseBoard(){
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                board[row][col] =new Square(row,col, getSquareTypeFor(row,col));
            }
        }
    }

    private Square.SquareType getSquareTypeFor(int row, int col){
        if (row == 7 && col == 7){
            return Square.SquareType.CENTER;
        }

        if ((row == 0 || row == 14) && (col == 0 || col == 7 || col == 14) ||
                (row == 7 && (col == 0 || col == 14))) {
            return Square.SquareType.TRIPLE_WORD;
        }

        if (row == col || row + col == 14) {
            if (row > 0 && row < 14 && col > 0 && col < 14) {
                if (row != 7 || col != 7) {
                    return Square.SquareType.DOUBLE_WORD;
                }
            }
        }

        if ((row == 1 || row == 13) && (col == 5 || col == 9) ||
                (row == 5 || row == 9) && (col == 1 || col == 5 || col == 9 || col == 13)) {
            return Square.SquareType.TRIPLE_LETTER;
        }

        if ((row == 0 || row == 14) && (col == 3 || col == 11) ||
                (row == 2 || row == 12) && (col == 6 || col == 8) ||
                (row == 3 || row == 11) && (col == 0 || col == 7 || col == 14) ||
                (row == 6 || row == 8) && (col == 2 || col == 6 || col == 8 || col == 12) ||
                (row == 7) && (col == 3 || col == 11)) {
            return Square.SquareType.DOUBLE_LETTER;
        }

        return Square.SquareType.NONE;
    }
    public Square getSquare(int row, int col) {
        if (row < 0 || row >= SIZE || col < 0 || col >= SIZE) {
            throw new IndexOutOfBoundsException("Invalid position: (" + row + ", " + col + ")");
        }
        return board[row][col];
    }

    public boolean placeTile(int row, int col, Tile tile) {
        Square square = getSquare(row, col);
        if(square.hasTile()){
            return false;
        }
        square.setTile(tile);
        return true;
    }

    public Tile removeTile(int row, int col) { //rewind button potential
        return getSquare(row, col).removeTile();
    }

    public List<Square> getAdjacentOccupiedSquares(int row, int col) {
        List<Square> adjacent = new ArrayList<>();

        if (row > 0 && getSquare(row - 1, col).hasTile()) {
            adjacent.add(getSquare(row - 1, col));
        }

        if (row < SIZE - 1 && getSquare(row + 1, col).hasTile()) {
            adjacent.add(getSquare(row + 1, col));
        }

        if (col > 0 && getSquare(row, col - 1).hasTile()) {
            adjacent.add(getSquare(row, col - 1));
        }

        if (col < SIZE - 1 && getSquare(row, col + 1).hasTile()) {
            adjacent.add(getSquare(row, col + 1));
        }

        return adjacent;
    }

    public boolean hasAdjacentTile(int row, int col) {
        return !getAdjacentOccupiedSquares(row, col).isEmpty();
    }

    public List<Square> getHorizontalWord(int row, int col) {
        List<Square> word = new ArrayList<>();

        int startCol = col;
        while(startCol > 0 && getSquare(row, startCol-1).hasTile()){
            startCol--;
        }

        int currentCol = col;
        while (currentCol < SIZE && getSquare(row, currentCol).hasTile()) {
            word.add(getSquare(row, currentCol));
            currentCol++;
        }

        return word.size() >= 2 ? word : new ArrayList<>();
    }

    public List<Square> getVerticalWord(int row, int col) {
        List<Square> word = new ArrayList<>();

        int startRow = row;
        while (startRow > 0 && getSquare(startRow - 1, col).hasTile()) {
            startRow--;
        }

        int currentRow = startRow;
        while (currentRow < SIZE && getSquare(currentRow, col).hasTile()) {
            word.add(getSquare(currentRow, col));
            currentRow++;
        }

        return word.size() >= 2 ? word : new ArrayList<>();
    }

    public static String getWordString(List<Square> squares) {
        StringBuilder word = new StringBuilder();
        for (Square square : squares) {
            word.append(square.getTile().getLetter());
        }
        return word.toString();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("   ");
        for (int col = 0; col < SIZE; col++) {
            sb.append(String.format("%2d ", col + 1));
        }
        sb.append("\n");

        for (int row = 0; row < SIZE; row++) {
            sb.append(String.format("%2d ", row + 1));
            for (int col = 0; col < SIZE; col++) {
                Square square = board[row][col];
                if (square.hasTile()) {
                    sb.append(" ").append(square.getTile().getLetter()).append(" ");
                } else {
                    sb.append(" ").append(square.getSquareType().getLabel().isEmpty() ? "·" : square.getSquareType().getLabel()).append(" ");
                }
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    public boolean isEmpty() {
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                if (board[row][col].hasTile()) {
                    return false;
                }
            }
        }
        return true;
    }
}
