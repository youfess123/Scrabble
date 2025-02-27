package edu.leicester.scrabble.util;

import edu.leicester.scrabble.model.Square;
import edu.leicester.scrabble.model.Tile;

import java.util.List;

public class WordScoreCalculator {
    private static final int BINGO_BONUS = 50;

    public static int calculateScore(List<Square> squares) {
        if (squares == null || squares.isEmpty()) {
            return 0;
        }

        int baseScore = 0;
        int wordMultiplier = 1;

        // Calculate base score and collect word multipliers
        for (Square square : squares) {
            Tile tile = square.getTile();
            if (tile == null) {
                continue;
            }

            // Apply letter multiplier if the premium hasn't been used
            int letterScore = tile.getValue() * square.getLetterMultiplier();
            baseScore += letterScore;

            // Collect word multipliers
            wordMultiplier *= square.getWordMultiplier();
        }

        // Apply word multipliers
        return baseScore * wordMultiplier;
    }

    public static int calculateMoveScore(List<Square> primaryWord, List<List<Square>> secondaryWords, boolean usedAllTiles) {
        int score = calculateScore(primaryWord);

        // Add scores for secondary words
        for (List<Square> word : secondaryWords) {
            score += calculateScore(word);
        }

        // Add bingo bonus if all tiles were used
        if (usedAllTiles) {
            score += BINGO_BONUS;
        }

        return score;
    }

    public static int estimateWordScore(String word, int startRow, int startCol, boolean isHorizontal, edu.leicester.scrabble.model.Board board) {
        if (word == null || word.isEmpty()) {
            return 0;
        }

        int baseScore = 0;
        int wordMultiplier = 1;
        int row = startRow;
        int col = startCol;

        for (int i = 0; i < word.length(); i++) {
            char letter = word.charAt(i);
            Square square = board.getSquare(row, col);

            // If there's already a tile on this square, use it
            if (square.hasTile()) {
                baseScore += square.getTile().getValue();
            } else {
                // Otherwise, calculate score for the new letter
                int letterValue = edu.leicester.scrabble.model.TileBag.getPointValue(letter);
                baseScore += letterValue * square.getLetterMultiplier();
                wordMultiplier *= square.getWordMultiplier();
            }

            // Move to the next position
            if (isHorizontal) {
                col++;
            } else {
                row++;
            }
        }

        return baseScore * wordMultiplier;
    }
}
