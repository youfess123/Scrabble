package edu.leicester.scrabble.model;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.awt.Point;

/**
 * Dictionary class for Scrabble game.
 *
 * This class manages the dictionary of valid words and provides methods for word validation
 * and lookup using the GADDAG data structure. It handles loading words from a file or
 * input stream and provides an interface for all word-related operations needed by the game.
 */
public class Dictionary {
    // The GADDAG data structure for efficient word lookup
    private final GADDAG gaddag;

    // A set of valid words for quick validation
    private final Set<String> wordSet;

    // The name of the loaded dictionary
    private final String dictionaryName;

    // Cache of word scores for improved performance
    private final Map<String, Integer> wordScoreCache;

    /**
     * Creates a Dictionary from a file path.
     *
     * @param filePath Path to the dictionary file
     * @throws IOException If the file cannot be read
     */
    public Dictionary(String filePath) throws IOException {
        this.gaddag = new GADDAG();
        this.wordSet = new HashSet<>();
        this.wordScoreCache = new HashMap<>();
        this.dictionaryName = extractDictionaryName(filePath);

        loadFromFile(filePath);
        System.out.println("Loaded dictionary '" + dictionaryName + "' with " + wordSet.size() + " words");
    }

    /**
     * Creates a Dictionary from an input stream.
     *
     * @param inputStream The input stream containing the dictionary
     * @param name The name to assign to this dictionary
     * @throws IOException If the stream cannot be read
     */
    public Dictionary(InputStream inputStream, String name) throws IOException {
        this.gaddag = new GADDAG();
        this.wordSet = new HashSet<>();
        this.wordScoreCache = new HashMap<>();
        this.dictionaryName = name;

        loadFromInputStream(inputStream);
        System.out.println("Loaded dictionary '" + dictionaryName + "' with " + wordSet.size() + " words");
    }

    /**
     * Extracts a dictionary name from a file path.
     *
     * @param filePath The file path
     * @return The dictionary name (filename without extension)
     */
    private String extractDictionaryName(String filePath) {
        // Extract the filename without the extension
        String filename = filePath.substring(filePath.lastIndexOf('/') + 1);
        if (filename.contains(".")) {
            filename = filename.substring(0, filename.lastIndexOf('.'));
        }
        return filename;
    }

    /**
     * Loads words from a file.
     *
     * @param filePath Path to the dictionary file
     * @throws IOException If the file cannot be read
     */
    private void loadFromFile(String filePath) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                addWord(line);
            }
        }
    }

    /**
     * Loads words from an input stream.
     *
     * @param inputStream The input stream containing the dictionary
     * @throws IOException If the stream cannot be read
     */
    private void loadFromInputStream(InputStream inputStream) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                addWord(line);
            }
        }
    }

    /**
     * Adds a word to the dictionary.
     *
     * @param word The word to add
     */
    public void addWord(String word) {
        word = word.trim().toUpperCase();

        // Skip empty words or words with non-letter characters
        if (word.isEmpty() || !word.matches("[A-Z]+")) {
            return;
        }

        wordSet.add(word);
        gaddag.insert(word);
    }

    /**
     * Validates if a word exists in the dictionary.
     *
     * @param word The word to check
     * @return true if the word is valid, false otherwise
     */
    public boolean isValidWord(String word) {
        if (word == null || word.isEmpty()) {
            return false;
        }

        word = word.trim().toUpperCase();
        return wordSet.contains(word);
    }

    /**
     * Gets the GADDAG data structure.
     *
     * @return The GADDAG
     */
    public GADDAG getGaddag() {
        return gaddag;
    }

    /**
     * Gets the dictionary name.
     *
     * @return The dictionary name
     */
    public String getName() {
        return dictionaryName;
    }

    /**
     * Gets the number of words in the dictionary.
     *
     * @return The word count
     */
    public int getWordCount() {
        return wordSet.size();
    }

    /**
     * Checks if a string is a valid prefix for any word in the dictionary.
     *
     * @param prefix The prefix to check
     * @return true if the prefix could form a valid word
     */
    public boolean isValidPrefix(String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return false;
        }

        prefix = prefix.trim().toUpperCase();

        for (String word : wordSet) {
            if (word.startsWith(prefix)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Gets all valid words that can be formed from the given rack and anchor letter.
     *
     * @param rack The available letters
     * @param anchor The anchor letter on the board
     * @param allowLeft Whether to allow extending to the left
     * @param allowRight Whether to allow extending to the right
     * @return A set of valid words
     */
    public Set<String> getWordsFrom(String rack, char anchor, boolean allowLeft, boolean allowRight) {
        return gaddag.getWordsFrom(rack, anchor, allowLeft, allowRight);
    }

    /**
     * Finds all valid words that can be formed by extending a partial word with rack tiles.
     *
     * @param partialWord The partial word on the board
     * @param rack The available rack tiles
     * @param isPrefix Whether the partial word is a prefix (true) or suffix (false)
     * @return A set of valid complete words
     */
    public Set<String> getWordsFromPartial(String partialWord, String rack, boolean isPrefix) {
        return gaddag.getWordsFromPartial(partialWord, rack, isPrefix);
    }

    /**
     * Finds valid words that can be formed by placing a tile at the specified position.
     *
     * @param board The game board
     * @param row The row
     * @param col The column
     * @param rack The available rack tiles
     * @param direction The word direction
     * @return Map of valid words to their starting positions
     */
    public Map<String, Point> findValidWordsAt(Board board, int row, int col, String rack, Move.Direction direction) {
        return gaddag.findValidWordsAt(board, row, col, rack, direction);
    }

    /**
     * Validates a move and returns all words formed.
     *
     * @param board The game board
     * @param move The move to validate
     * @return List of formed words if valid, empty list if invalid
     */
    public List<String> validateMove(Board board, Move move) {
        return gaddag.validateMove(board, move);
    }

    /**
     * Calculates and caches the score for a word.
     *
     * @param word The word
     * @param board The game board
     * @param row The starting row
     * @param col The starting column
     * @param direction The word direction
     * @param newTilePositions Positions of newly placed tiles
     * @return The score for the word
     */
    public int getWordScore(String word, Board board, int row, int col,
                            Move.Direction direction, Set<Point> newTilePositions) {
        // Check cache first
        String cacheKey = word + "|" + row + "|" + col + "|" + direction;
        if (wordScoreCache.containsKey(cacheKey)) {
            return wordScoreCache.get(cacheKey);
        }

        int wordScore = 0;
        int wordMultiplier = 1;

        int currentRow = row;
        int currentCol = col;

        for (int i = 0; i < word.length(); i++) {
            Square square = board.getSquare(currentRow, currentCol);
            Point currentPoint = new Point(currentRow, currentCol);
            boolean isNewTile = newTilePositions.contains(currentPoint);

            int letterValue = square.getTile().getValue();
            int letterScore = letterValue;

            // Apply letter multipliers for new tiles only
            if (isNewTile && !square.isSquareTypeUsed()) {
                if (square.getSquareType() == Square.SquareType.DOUBLE_LETTER) {
                    letterScore = letterValue * 2;
                } else if (square.getSquareType() == Square.SquareType.TRIPLE_LETTER) {
                    letterScore = letterValue * 3;
                }

                // Collect word multipliers for new tiles
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
        int finalScore = wordScore * wordMultiplier;

        // Cache the result
        wordScoreCache.put(cacheKey, finalScore);

        return finalScore;
    }

    /**
     * Calculates the total score for a move.
     *
     * @param board The game board
     * @param move The move
     * @param formedWords List of words formed by the move
     * @return The total score
     */
    public int calculateMoveScore(Board board, Move move, List<String> formedWords) {
        int totalScore = 0;

        // Track positions of new tiles
        Set<Point> newTilePositions = new HashSet<>();
        int currentRow = move.getStartRow();
        int currentCol = move.getStartCol();

        for (int i = 0; i < move.getTiles().size(); i++) {
            // Skip existing tiles
            while (currentRow < Board.SIZE && currentCol < Board.SIZE &&
                    board.getSquare(currentRow, currentCol).hasTile()) {
                if (move.getDirection() == Move.Direction.HORIZONTAL) {
                    currentCol++;
                } else {
                    currentRow++;
                }
            }

            if (currentRow < Board.SIZE && currentCol < Board.SIZE) {
                newTilePositions.add(new Point(currentRow, currentCol));

                if (move.getDirection() == Move.Direction.HORIZONTAL) {
                    currentCol++;
                } else {
                    currentRow++;
                }
            }
        }

        // Calculate score for each formed word
        for (String word : formedWords) {
            // Find the starting position of the word
            int startRow, startCol;
            Move.Direction wordDirection;

            if (move.getDirection() == Move.Direction.HORIZONTAL) {
                // The main word is horizontal
                if (word.equals(formedWords.get(0))) {
                    startRow = move.getStartRow();
                    startCol = findWordStartCol(board, startRow, move.getStartCol());
                    wordDirection = Move.Direction.HORIZONTAL;
                } else {
                    // This is a vertical cross-word
                    // Find which new tile formed it
                    for (Point p : newTilePositions) {
                        if (isPartOfVerticalWord(board, p, word)) {
                            startRow = findWordStartRow(board, p.x, p.y);
                            startCol = p.y;
                            wordDirection = Move.Direction.VERTICAL;

                            // Calculate word score
                            totalScore += getWordScore(word, board, startRow, startCol,
                                    wordDirection, newTilePositions);
                            break;
                        }
                    }
                    continue; // Processed this cross-word
                }
            } else {
                // The main word is vertical
                if (word.equals(formedWords.get(0))) {
                    startRow = findWordStartRow(board, move.getStartRow(), move.getStartCol());
                    startCol = move.getStartCol();
                    wordDirection = Move.Direction.VERTICAL;
                } else {
                    // This is a horizontal cross-word
                    // Find which new tile formed it
                    for (Point p : newTilePositions) {
                        if (isPartOfHorizontalWord(board, p, word)) {
                            startRow = p.x;
                            startCol = findWordStartCol(board, p.x, p.y);
                            wordDirection = Move.Direction.HORIZONTAL;

                            // Calculate word score
                            totalScore += getWordScore(word, board, startRow, startCol,
                                    wordDirection, newTilePositions);
                            break;
                        }
                    }
                    continue; // Processed this cross-word
                }
            }

            // Calculate score for main word
            totalScore += getWordScore(word, board, startRow, startCol,
                    wordDirection, newTilePositions);
        }

        // Add bonus for using all tiles (Bingo)
        if (move.getTiles().size() == 7) {
            totalScore += 50; // Bingo bonus
        }

        return totalScore;
    }

    /**
     * Finds the starting row of a vertical word.
     *
     * @param board The game board
     * @param row The row to start searching from
     * @param col The column
     * @return The starting row
     */
    private int findWordStartRow(Board board, int row, int col) {
        while (row > 0 && board.getSquare(row - 1, col).hasTile()) {
            row--;
        }
        return row;
    }

    /**
     * Finds the starting column of a horizontal word.
     *
     * @param board The game board
     * @param row The row
     * @param col The column to start searching from
     * @return The starting column
     */
    private int findWordStartCol(Board board, int row, int col) {
        while (col > 0 && board.getSquare(row, col - 1).hasTile()) {
            col--;
        }
        return col;
    }

    /**
     * Checks if a point is part of a vertical word.
     *
     * @param board The game board
     * @param p The point
     * @param word The word to check
     * @return true if the point is part of the vertical word
     */
    private boolean isPartOfVerticalWord(Board board, Point p, String word) {
        // Find start of vertical word at this point
        int startRow = findWordStartRow(board, p.x, p.y);

        // Get the vertical word at this position
        StringBuilder verticalWord = new StringBuilder();
        for (int r = startRow; r < Board.SIZE && board.getSquare(r, p.y).hasTile(); r++) {
            verticalWord.append(board.getSquare(r, p.y).getTile().getLetter());
        }

        return verticalWord.toString().equalsIgnoreCase(word);
    }

    /**
     * Checks if a point is part of a horizontal word.
     *
     * @param board The game board
     * @param p The point
     * @param word The word to check
     * @return true if the point is part of the horizontal word
     */
    private boolean isPartOfHorizontalWord(Board board, Point p, String word) {
        // Find start of horizontal word at this point
        int startCol = findWordStartCol(board, p.x, p.y);

        // Get the horizontal word at this position
        StringBuilder horizontalWord = new StringBuilder();
        for (int c = startCol; c < Board.SIZE && board.getSquare(p.x, c).hasTile(); c++) {
            horizontalWord.append(board.getSquare(p.x, c).getTile().getLetter());
        }

        return horizontalWord.toString().equalsIgnoreCase(word);
    }

    /**
     * Clears the word score cache.
     */
    public void clearCache() {
        wordScoreCache.clear();
    }
}