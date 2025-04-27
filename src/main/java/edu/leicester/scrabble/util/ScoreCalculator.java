package edu.leicester.scrabble.util;

import edu.leicester.scrabble.model.*;
import java.awt.Point;
import java.util.*;

public class ScoreCalculator {

    /**
     * Calculates the total score for a move
     */
    public static int calculateMoveScore(Move move, Board board, List<String> formedWords, Set<Point> newTilePositions) {
        int totalScore = 0;

        // Calculate score for each formed word
        for (String word : formedWords) {
            Point wordPos = WordValidator.findWordPosition(board, word);
            if (wordPos == null) continue;

            boolean isHorizontal = WordValidator.isWordHorizontal(board, word, wordPos);
            int wordScore = calculateWordScore(word, wordPos.x, wordPos.y, isHorizontal, board, newTilePositions);
            totalScore += wordScore;
        }

        // Add bingo bonus if used all 7 tiles
        if (move.getTiles().size() == 7) {
            totalScore += ScrabbleConstants.BINGO_BONUS;
        }

        return totalScore;
    }

    /**
     * Calculates the score for a single word
     */
    public static int calculateWordScore(String word, int startRow, int startCol,
                                         boolean isHorizontal, Board board, Set<Point> newTilePositions) {
        int score = 0;
        int wordMultiplier = 1;
        int row = startRow;
        int col = startCol;

        for (int i = 0; i < word.length(); i++) {
            Square square = board.getSquare(row, col);
            Point currentPoint = new Point(row, col);
            Tile tile = square.getTile();

            // Get letter value, accounting for blank tiles
            int letterValue = tile.isBlank() ? 0 : tile.getValue();
            int effectiveValue = letterValue;

            // Apply square multipliers only for newly placed tiles
            if (newTilePositions.contains(currentPoint) && !square.isSquareTypeUsed()) {
                // Apply letter multipliers
                if (square.getSquareType() == Square.SquareType.DOUBLE_LETTER) {
                    effectiveValue = letterValue * 2;
                } else if (square.getSquareType() == Square.SquareType.TRIPLE_LETTER) {
                    effectiveValue = letterValue * 3;
                }

                // Collect word multipliers
                if (square.getSquareType() == Square.SquareType.DOUBLE_WORD ||
                        square.getSquareType() == Square.SquareType.CENTER) {
                    wordMultiplier *= 2;
                } else if (square.getSquareType() == Square.SquareType.TRIPLE_WORD) {
                    wordMultiplier *= 3;
                }
            }

            score += effectiveValue;

            // Move to next position
            if (isHorizontal) {
                col++;
            } else {
                row++;
            }
        }

        // Apply word multiplier
        return score * wordMultiplier;
    }

    /**
     * Calculate score for word placement and create a detailed score breakdown
     */
    public static String getScoreBreakdown(String word, int startRow, int startCol,
                                           boolean isHorizontal, Board board, Set<Point> newTilePositions) {
        StringBuilder breakdown = new StringBuilder();
        int score = 0;
        int wordMultiplier = 1;
        int row = startRow;
        int col = startCol;

        breakdown.append("Score for '").append(word).append("': ");

        for (int i = 0; i < word.length(); i++) {
            Square square = board.getSquare(row, col);
            Point currentPoint = new Point(row, col);
            Tile tile = square.getTile();

            int letterValue = tile.isBlank() ? 0 : tile.getValue();
            int effectiveValue = letterValue;

            // Apply multipliers only for newly placed tiles
            if (newTilePositions.contains(currentPoint) && !square.isSquareTypeUsed()) {
                if (square.getSquareType() == Square.SquareType.DOUBLE_LETTER) {
                    effectiveValue = letterValue * 2;
                    breakdown.append(tile.getLetter()).append("(").append(letterValue).append("×2) + ");
                } else if (square.getSquareType() == Square.SquareType.TRIPLE_LETTER) {
                    effectiveValue = letterValue * 3;
                    breakdown.append(tile.getLetter()).append("(").append(letterValue).append("×3) + ");
                } else {
                    breakdown.append(tile.getLetter()).append("(").append(letterValue).append(") + ");
                }

                if (square.getSquareType() == Square.SquareType.DOUBLE_WORD ||
                        square.getSquareType() == Square.SquareType.CENTER) {
                    wordMultiplier *= 2;
                } else if (square.getSquareType() == Square.SquareType.TRIPLE_WORD) {
                    wordMultiplier *= 3;
                }
            } else {
                breakdown.append(tile.getLetter()).append("(").append(letterValue).append(") + ");
            }

            score += effectiveValue;

            if (isHorizontal) {
                col++;
            } else {
                row++;
            }
        }

        // Remove the trailing " + "
        if (breakdown.length() > 3) {
            breakdown.setLength(breakdown.length() - 3);
        }

        int finalScore = score * wordMultiplier;

        if (wordMultiplier > 1) {
            breakdown.append(" = ").append(score).append(" × ").append(wordMultiplier);
        }

        breakdown.append(" = ").append(finalScore);

        return breakdown.toString();
    }
}