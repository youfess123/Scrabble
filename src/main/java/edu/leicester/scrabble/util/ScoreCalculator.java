package edu.leicester.scrabble.util;

import edu.leicester.scrabble.model.*;
import java.awt.Point;
import java.util.*;

public class ScoreCalculator {

    /**
     * Calculates score for a word
     */
    public static int calculateWordScore(String word, int startRow, int startCol,
                                         boolean isHorizontal, Board board,
                                         Set<Point> newTilePositions) {
        int score = 0;
        int wordMultiplier = 1;
        int row = startRow;
        int col = startCol;

        for (int i = 0; i < word.length(); i++) {
            Square square = board.getSquare(row, col);
            Point currentPoint = new Point(row, col);

            int letterValue = square.getTile().getValue();

            // Apply letter multipliers for new tiles only
            if (newTilePositions.contains(currentPoint) && !square.isSquareTypeUsed()) {
                if (square.getSquareType() == Square.SquareType.DOUBLE_LETTER) {
                    letterValue *= 2;
                } else if (square.getSquareType() == Square.SquareType.TRIPLE_LETTER) {
                    letterValue *= 3;
                }

                // Collect word multipliers for new tiles only
                if (square.getSquareType() == Square.SquareType.DOUBLE_WORD ||
                        square.getSquareType() == Square.SquareType.CENTER) {
                    wordMultiplier *= 2;
                } else if (square.getSquareType() == Square.SquareType.TRIPLE_WORD) {
                    wordMultiplier *= 3;
                }
            }

            score += letterValue;

            if (isHorizontal) {
                col++;
            } else {
                row++;
            }
        }

        // Apply word multiplier
        score *= wordMultiplier;

        return score;
    }

    /**
     * Calculates total score for a move
     */
    public static int calculateMoveScore(Move move, Board board, List<String> formedWords,
                                         Set<Point> newTilePositions) {
        int totalScore = 0;

        for (String word : formedWords) {
            Point wordPos = findWordPosition(board, word);
            if (wordPos == null) continue;

            boolean isHorizontal = isWordHorizontal(board, word, wordPos);
            int wordScore = calculateWordScore(word, wordPos.x, wordPos.y, isHorizontal, board, newTilePositions);
            totalScore += wordScore;
        }

        // Add bingo bonus
        if (move.getTiles().size() == 7) {
            totalScore += ScrabbleConstants.BINGO_BONUS;
        }

        return totalScore;
    }

    private static Point findWordPosition(Board board, String word) {
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
        for (int col = 0; col < Board.SIZE; col++) {
            for (int row = 0; row < Board.SIZE; row++) {
                String foundWord = BoardUtils.getWordAt(board, row, col, Move.Direction.VERTICAL);
                if (foundWord.equals(word)) {
                    return new Point(row, col);
                }
            }
        }

        return null;
    }

    private static boolean isWordHorizontal(Board board, String word, Point position) {
        String horizontalWord = BoardUtils.getWordAt(board, position.x, position.y, Move.Direction.HORIZONTAL);
        return horizontalWord.equals(word);
    }
}