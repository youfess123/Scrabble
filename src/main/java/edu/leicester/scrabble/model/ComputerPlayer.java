package edu.leicester.scrabble.model;

import edu.leicester.scrabble.util.ScrabbleConstants;
import java.awt.Point;
import java.util.*;
import java.util.List;

/**
 * Computer player AI that uses the GADDAG data structure for efficient move generation.
 * This enhanced version is much better at finding word extensions and optimal moves.
 */
public class ComputerPlayer {

    private final Player player;
    private final Random random;
    private int difficultyLevel;

    public ComputerPlayer(Player player, int difficultyLevel) {
        this.player = player;
        this.random = new Random();
        this.difficultyLevel = Math.max(1, Math.min(3, difficultyLevel));
        player.setComputer(true);
    }

    public Player getPlayer() {
        return player;
    }

    public void setDifficultyLevel(int difficultyLevel) {
        this.difficultyLevel = Math.max(1, Math.min(3, difficultyLevel));
    }

    public int getDifficultyLevel() {
        return difficultyLevel;
    }

    /**
     * Generates the best move for the computer player based on difficulty level.
     *
     * @param game The current game state
     * @return The move to execute
     */
    public Move generateMove(Game game) {
        try {
            System.out.println("Computer player generating move at difficulty " + difficultyLevel);

            // Safety check - if no tiles in rack, pass turn
            if (player.getRack().size() == 0) {
                System.out.println("Computer has no tiles, passing");
                return Move.createPassMove(player);
            }

            // Get possible moves with a timeout protection
            List<Move> possibleMoves = new ArrayList<>();
            try {
                // Find possible moves using GADDAG
                possibleMoves = findPossibleMoves(game);
                System.out.println("Found " + possibleMoves.size() + " possible moves");
            } catch (Exception e) {
                System.err.println("Error finding possible moves: " + e.getMessage());
                e.printStackTrace();
                return generateFallbackMove(game);
            }

            // If there are no possible moves, either exchange tiles or pass
            if (possibleMoves.isEmpty()) {
                System.out.println("Computer: No possible word placements found, trying fallback");
                return generateFallbackMove(game);
            }

            // Sort moves by score (highest first)
            possibleMoves.sort(Comparator.comparing(Move::getScore).reversed());

            // Select a move based on difficulty level
            Move selectedMove;
            switch (difficultyLevel) {
                case 1: // Easy - Randomly select from all moves
                    selectedMove = possibleMoves.get(random.nextInt(possibleMoves.size()));
                    break;

                case 2: // Medium - Select from the top half of moves
                    int mediumCutoff = Math.max(1, possibleMoves.size() / 2);
                    selectedMove = possibleMoves.get(random.nextInt(mediumCutoff));
                    break;

                case 3: // Hard - Select from the top few moves
                    int hardCutoff = Math.min(3, possibleMoves.size());
                    selectedMove = possibleMoves.get(random.nextInt(hardCutoff));
                    break;

                default: // Default to medium
                    int defaultCutoff = Math.max(1, possibleMoves.size() / 2);
                    selectedMove = possibleMoves.get(random.nextInt(defaultCutoff));
            }

            System.out.println("Computer selected move with score: " + selectedMove.getScore());
            System.out.println("Words formed: " + String.join(", ", selectedMove.getFormedWords()));

            return selectedMove;

        } catch (Exception e) {
            System.err.println("Error generating computer move: " + e.getMessage());
            e.printStackTrace();
            return Move.createPassMove(player);
        }
    }

    /**
     * Generates a fallback move (exchange or pass) when no word placements are possible.
     *
     * @param game The current game state
     * @return An exchange or pass move
     */
    private Move generateFallbackMove(Game game) {
        try {
            // If there are enough tiles in the bag, exchange some tiles
            if (game.getTileBag().getTileCount() >= 7) {
                System.out.println("Computer: Generating exchange move");

                // Select tiles to exchange based on letter quality
                List<Tile> tilesToExchange = selectOptimalTilesToExchange(game);

                if (!tilesToExchange.isEmpty()) {
                    System.out.println("Computer exchanging " + tilesToExchange.size() + " tiles");
                    // Print out the letters being exchanged for debugging
                    System.out.print("Exchanging tiles: ");
                    for (Tile t : tilesToExchange) {
                        System.out.print(t.getLetter() + " ");
                    }
                    System.out.println();

                    return Move.createExchangeMove(player, tilesToExchange);
                }
            }

            // Not enough tiles to exchange or something went wrong, so pass
            System.out.println("Computer: Generating pass move");
            return Move.createPassMove(player);

        } catch (Exception e) {
            System.err.println("Error generating fallback move: " + e.getMessage());
            e.printStackTrace();
            return Move.createPassMove(player);
        }
    }

    /**
     * Selects the optimal tiles to exchange based on letter quality and difficulty level.
     *
     * @param game The current game state
     * @return List of tiles to exchange
     */
    private List<Tile> selectOptimalTilesToExchange(Game game) {
        Rack rack = player.getRack();
        List<Tile> availableTiles = new ArrayList<>(rack.getTiles());
        List<Tile> tilesToExchange = new ArrayList<>();

        // Score each tile based on its value in Scrabble
        Map<Tile, Double> tileScores = new HashMap<>();
        Map<Character, Integer> letterCounts = new HashMap<>();

        // Count letters in rack
        for (Tile tile : availableTiles) {
            char letter = tile.getLetter();
            letterCounts.put(letter, letterCounts.getOrDefault(letter, 0) + 1);
        }

        // Evaluate each tile
        for (Tile tile : availableTiles) {
            char letter = tile.getLetter();
            double score = 0;

            // Lower score means more likely to exchange

            // High value tiles are less useful
            if (tile.getValue() >= 8) {
                score -= 10;  // Very likely to exchange
            } else if (tile.getValue() >= 4) {
                score -= 5;   // Somewhat likely to exchange
            }

            // Penalize duplicate consonants beyond 2
            if (!isVowel(letter) && letterCounts.get(letter) > 2) {
                score -= 8;
            }

            // Penalize having too few vowels
            if (isVowel(letter)) {
                int vowelCount = countVowels(availableTiles);
                if (vowelCount <= 2) {
                    score += 10;  // Don't exchange if we have few vowels
                } else if (vowelCount > 3) {
                    score -= 5;   // Exchange if we have too many vowels
                }
            }

            // Penalize uncommon letters that are hard to play
            if (letter == 'Q' || letter == 'Z' || letter == 'X' || letter == 'J') {
                score -= 7;
            }

            tileScores.put(tile, score);
        }

        // Sort tiles by score (lowest first - most likely to exchange)
        availableTiles.sort(Comparator.comparing(tile -> tileScores.getOrDefault(tile, 0.0)));

        // Different exchange strategies based on difficulty
        int numToExchange;
        switch (difficultyLevel) {
            case 1: // Easy - exchange more tiles
                numToExchange = Math.min(4, availableTiles.size());
                break;
            case 2: // Medium - exchange a moderate number
                numToExchange = Math.min(3, availableTiles.size());
                break;
            case 3: // Hard - be more selective
                numToExchange = Math.min(2, availableTiles.size());
                break;
            default:
                numToExchange = Math.min(3, availableTiles.size());
        }

        // Take the tiles with the lowest scores
        for (int i = 0; i < numToExchange; i++) {
            if (i < availableTiles.size() && tileScores.getOrDefault(availableTiles.get(i), 0.0) < 0) {
                tilesToExchange.add(availableTiles.get(i));
            }
        }

        return tilesToExchange;
    }

    /**
     * Finds all possible valid moves using the GADDAG data structure.
     *
     * @param game The current game state
     * @return List of possible valid moves
     */
    private List<Move> findPossibleMoves(Game game) {
        List<Move> possibleMoves = new ArrayList<>();
        Board board = game.getBoard();
        Dictionary dictionary = game.getDictionary();
        Rack rack = player.getRack();

        // Convert rack to string for GADDAG lookup
        String rackLetters = getTilesAsString(rack.getTiles());

        // Special case for the first move (center square)
        if (board.isEmpty()) {
            findMovesForEmptyBoard(game, possibleMoves);
            return possibleMoves;
        }

        // Find anchor squares (empty squares adjacent to placed tiles)
        List<Point> anchorPoints = findAnchorPoints(board);
        System.out.println("Found " + anchorPoints.size() + " anchor points");

        // For each anchor point, try to find valid placements
        for (Point anchor : anchorPoints) {
            int row = anchor.x;
            int col = anchor.y;

            // Try horizontal placements
            findPlacementsAt(game, row, col, Move.Direction.HORIZONTAL, possibleMoves);

            // Try vertical placements
            findPlacementsAt(game, row, col, Move.Direction.VERTICAL, possibleMoves);
        }

        return possibleMoves;
    }

    /**
     * Finds all possible moves for an empty board (first move).
     *
     * @param game The current game state
     * @param possibleMoves Output list of possible moves
     */
    private void findMovesForEmptyBoard(Game game, List<Move> possibleMoves) {
        Dictionary dictionary = game.getDictionary();
        Rack rack = player.getRack();

        // Get rack as string for word generation
        String rackLetters = getTilesAsString(rack.getTiles());

        // Find all valid words that can be formed with rack letters
        Set<String> validWords = findValidWordsFromRack(rackLetters, dictionary);

        // Try placing each word through the center square
        for (String word : validWords) {
            // Only consider words of length 2 or more
            if (word.length() < 2) continue;

            // Try horizontal placements
            for (int offset = 0; offset < word.length(); offset++) {
                // Calculate starting column so that the word covers center square
                int startCol = ScrabbleConstants.CENTER_SQUARE - offset;

                // Check if the word fits on the board and goes through center
                if (startCol >= 0 && startCol + word.length() <= Board.SIZE) {
                    // Create the move
                    Move move = Move.createPlaceMove(player, ScrabbleConstants.CENTER_SQUARE,
                            startCol, Move.Direction.HORIZONTAL);

                    // Get the tiles needed for this word
                    List<Tile> tilesForWord = getTilesForWord(word, rack.getTiles());

                    if (tilesForWord != null) {
                        move.addTiles(tilesForWord);

                        // Calculate score
                        int score = calculateWordScore(word, ScrabbleConstants.CENTER_SQUARE,
                                startCol, true, game.getBoard());
                        move.setScore(score);

                        // Set formed words
                        List<String> formedWords = new ArrayList<>();
                        formedWords.add(word);
                        move.setFormedWords(formedWords);

                        possibleMoves.add(move);
                    }
                }
            }

            // Try vertical placements
            for (int offset = 0; offset < word.length(); offset++) {
                // Calculate starting row so that the word covers center square
                int startRow = ScrabbleConstants.CENTER_SQUARE - offset;

                // Check if the word fits on the board and goes through center
                if (startRow >= 0 && startRow + word.length() <= Board.SIZE) {
                    // Create the move
                    Move move = Move.createPlaceMove(player, startRow,
                            ScrabbleConstants.CENTER_SQUARE,
                            Move.Direction.VERTICAL);

                    // Get the tiles needed for this word
                    List<Tile> tilesForWord = getTilesForWord(word, rack.getTiles());

                    if (tilesForWord != null) {
                        move.addTiles(tilesForWord);

                        // Calculate score
                        int score = calculateWordScore(word, startRow,
                                ScrabbleConstants.CENTER_SQUARE,
                                false, game.getBoard());
                        move.setScore(score);

                        // Set formed words
                        List<String> formedWords = new ArrayList<>();
                        formedWords.add(word);
                        move.setFormedWords(formedWords);

                        possibleMoves.add(move);
                    }
                }
            }
        }
    }

    /**
     * Finds valid word placements at a specific board position and direction.
     *
     * @param game The current game state
     * @param row The row position
     * @param col The column position
     * @param direction The direction to place words
     * @param possibleMoves Output list of possible moves
     */
    private void findPlacementsAt(Game game, int row, int col,
                                  Move.Direction direction, List<Move> possibleMoves) {
        Board board = game.getBoard();
        Dictionary dictionary = game.getDictionary();
        Rack rack = player.getRack();

        // Get rack letters as string
        String rackLetters = getTilesAsString(rack.getTiles());

        // Create a temporary board to test placements
        Board tempBoard = new Board();

        // Copy current board state
        for (int r = 0; r < Board.SIZE; r++) {
            for (int c = 0; c < Board.SIZE; c++) {
                if (board.getSquare(r, c).hasTile()) {
                    tempBoard.placeTile(r, c, board.getSquare(r, c).getTile());
                }
            }
        }

        // Get current partial words at this position
        String[] partialWords = getPartialWords(board, row, col, direction);
        String prefix = partialWords[0];  // Letters before the position
        String suffix = partialWords[1];  // Letters after the position

        // For each letter in the rack, try placing it at this position
        for (char letter : getUniqueLetters(rackLetters)) {
            // Find words that can be formed by placing this letter here
            findWordsWithLetterAt(game, row, col, letter, direction, possibleMoves);
        }
    }

    /**
     * Gets partial words already on the board at a position.
     *
     * @param board The game board
     * @param row The row position
     * @param col The column position
     * @param direction The direction
     * @return Array with [prefix, suffix]
     */
    private String[] getPartialWords(Board board, int row, int col, Move.Direction direction) {
        StringBuilder prefix = new StringBuilder();
        StringBuilder suffix = new StringBuilder();

        if (direction == Move.Direction.HORIZONTAL) {
            // Get letters to the left (prefix)
            int c = col - 1;
            while (c >= 0 && board.getSquare(row, c).hasTile()) {
                prefix.insert(0, board.getSquare(row, c).getTile().getLetter());
                c--;
            }

            // Get letters to the right (suffix)
            c = col + 1;
            while (c < Board.SIZE && board.getSquare(row, c).hasTile()) {
                suffix.append(board.getSquare(row, c).getTile().getLetter());
                c++;
            }
        } else { // VERTICAL
            // Get letters above (prefix)
            int r = row - 1;
            while (r >= 0 && board.getSquare(r, col).hasTile()) {
                prefix.insert(0, board.getSquare(r, col).getTile().getLetter());
                r--;
            }

            // Get letters below (suffix)
            r = row + 1;
            while (r < Board.SIZE && board.getSquare(r, col).hasTile()) {
                suffix.append(board.getSquare(r, col).getTile().getLetter());
                r++;
            }
        }

        return new String[] {prefix.toString(), suffix.toString()};
    }

    /**
     * Finds words that can be formed by placing a specific letter at a position.
     *
     * @param game The current game state
     * @param row The row position
     * @param col The column position
     * @param letter The letter to place
     * @param direction The direction
     * @param possibleMoves Output list of possible moves
     */
    private void findWordsWithLetterAt(Game game, int row, int col, char letter,
                                       Move.Direction direction, List<Move> possibleMoves) {
        Board board = game.getBoard();
        Dictionary dictionary = game.getDictionary();
        Rack rack = player.getRack();

        // Get partial words at this position
        String[] partialWords = getPartialWords(board, row, col, direction);
        String prefix = partialWords[0];
        String suffix = partialWords[1];

        // Complete word with the letter in the middle
        String completeWord = prefix + letter + suffix;

        // Only consider valid words of at least 2 letters
        if (completeWord.length() >= 2 && dictionary.isValidWord(completeWord)) {
            // Find where the word would start on the board
            int startRow, startCol;
            if (direction == Move.Direction.HORIZONTAL) {
                startRow = row;
                startCol = col - prefix.length();
            } else {
                startRow = row - prefix.length();
                startCol = col;
            }

            // Create a move
            Move move = Move.createPlaceMove(player, startRow, startCol, direction);

            // Find tiles needed from rack
            List<Tile> tilesNeeded = new ArrayList<>();

            // Find the tile for this letter in the rack
            Tile tilePlaced = null;
            for (Tile tile : rack.getTiles()) {
                if (tile.getLetter() == letter && !tilesNeeded.contains(tile)) {
                    tilePlaced = tile;
                    tilesNeeded.add(tile);
                    break;
                }
            }

            if (tilePlaced != null) {
                // Create a temporary board with the tile placed
                Board tempBoard = new Board();

                // Copy current board state
                for (int r = 0; r < Board.SIZE; r++) {
                    for (int c = 0; c < Board.SIZE; c++) {
                        if (board.getSquare(r, c).hasTile()) {
                            tempBoard.placeTile(r, c, board.getSquare(r, c).getTile());
                        }
                    }
                }

                // Place the new tile
                tempBoard.placeTile(row, col, tilePlaced);

                // Check for cross-words
                List<String> formedWords = new ArrayList<>();
                formedWords.add(completeWord);

                // Check for a cross-word
                String crossWord;
                if (direction == Move.Direction.HORIZONTAL) {
                    // Check for vertical cross-word
                    crossWord = getWordAt(tempBoard, row, col, false);
                } else {
                    // Check for horizontal cross-word
                    crossWord = getWordAt(tempBoard, row, col, true);
                }

                // Add valid cross-words
                if (crossWord.length() >= 2) {
                    if (dictionary.isValidWord(crossWord)) {
                        formedWords.add(crossWord);
                    } else {
                        // If cross-word is invalid, skip this move
                        return;
                    }
                }

                // Set formed words and tiles
                move.setFormedWords(formedWords);
                move.addTiles(tilesNeeded);

                // Calculate score
                int score = calculateMoveScore(move, board, tilePlaced, row, col);
                move.setScore(score);

                // Add to possible moves
                possibleMoves.add(move);

                // Now try to extend this placement with additional tiles
                // from the rack in the same direction
                extendPlacement(game, move, tempBoard, row, col, direction, possibleMoves);
            }
        }
    }

    /**
     * Extends a placement with additional tiles in the same direction.
     *
     * @param game The current game state
     * @param baseMove The base move to extend
     * @param tempBoard Temporary board with the base move applied
     * @param row The row of the last placed tile
     * @param col The column of the last placed tile
     * @param direction The direction
     * @param possibleMoves Output list of possible moves
     */
    private void extendPlacement(Game game, Move baseMove, Board tempBoard,
                                 int row, int col, Move.Direction direction,
                                 List<Move> possibleMoves) {
        Board board = game.getBoard();
        Dictionary dictionary = game.getDictionary();
        Rack rack = player.getRack();

        // Get remaining letters in rack after base move
        List<Tile> remainingTiles = new ArrayList<>(rack.getTiles());
        for (Tile t : baseMove.getTiles()) {
            remainingTiles.remove(t);
        }

        if (remainingTiles.isEmpty()) {
            return; // No more tiles to place
        }

        // Calculate next position
        int nextRow = row;
        int nextCol = col;
        if (direction == Move.Direction.HORIZONTAL) {
            nextCol = col + 1;
        } else {
            nextRow = row + 1;
        }

        // Stop if next position is off board or already has a tile
        if (nextRow >= Board.SIZE || nextCol >= Board.SIZE ||
                tempBoard.getSquare(nextRow, nextCol).hasTile()) {
            return;
        }

        // Try each remaining letter at the next position
        for (Tile tile : remainingTiles) {
            // Create a new temporary board with this additional tile
            Board extendedBoard = new Board();

            // Copy the state from the current temp board
            for (int r = 0; r < Board.SIZE; r++) {
                for (int c = 0; c < Board.SIZE; c++) {
                    if (tempBoard.getSquare(r, c).hasTile()) {
                        extendedBoard.placeTile(r, c, tempBoard.getSquare(r, c).getTile());
                    }
                }
            }

            // Place this tile at the next position
            extendedBoard.placeTile(nextRow, nextCol, tile);

            // Check if this forms valid words
            List<String> formedWords = new ArrayList<>();

            // Check main word
            String mainWord;
            if (direction == Move.Direction.HORIZONTAL) {
                mainWord = getWordAt(extendedBoard, baseMove.getStartRow(),
                        baseMove.getStartCol(), true);
            } else {
                mainWord = getWordAt(extendedBoard, baseMove.getStartRow(),
                        baseMove.getStartCol(), false);
            }

            // Validate main word
            if (mainWord.length() < 2 || !dictionary.isValidWord(mainWord)) {
                continue; // Invalid main word
            }

            formedWords.add(mainWord);

            // Check cross-word at the new tile position
            String crossWord;
            if (direction == Move.Direction.HORIZONTAL) {
                crossWord = getWordAt(extendedBoard, nextRow, nextCol, false);
            } else {
                crossWord = getWordAt(extendedBoard, nextRow, nextCol, true);
            }

            // Validate any cross-word
            if (crossWord.length() >= 2) {
                if (!dictionary.isValidWord(crossWord)) {
                    continue; // Invalid cross-word
                }
                formedWords.add(crossWord);
            }

            // Create an extended move
            Move extendedMove = Move.createPlaceMove(player, baseMove.getStartRow(),
                    baseMove.getStartCol(), direction);

            // Add all tiles (base tiles + this new tile)
            List<Tile> allTiles = new ArrayList<>(baseMove.getTiles());
            allTiles.add(tile);
            extendedMove.addTiles(allTiles);

            // Set formed words
            extendedMove.setFormedWords(formedWords);

            // Calculate score
            int score = calculateMoveScore(extendedMove, board,
                    new Point(nextRow, nextCol));
            extendedMove.setScore(score);

            // Add to possible moves
            possibleMoves.add(extendedMove);

            // Recursively try to extend further
            extendPlacement(game, extendedMove, extendedBoard, nextRow, nextCol,
                    direction, possibleMoves);
        }
    }

    /**
     * Gets the word at a position in a given direction.
     *
     * @param board The game board
     * @param row The starting row
     * @param col The starting column
     * @param isHorizontal Whether the word is horizontal
     * @return The word as a string
     */
    private String getWordAt(Board board, int row, int col, boolean isHorizontal) {
        StringBuilder word = new StringBuilder();

        // Find the start of the word
        int startRow = row;
        int startCol = col;

        if (isHorizontal) {
            // Look left
            while (startCol > 0 && board.getSquare(row, startCol - 1).hasTile()) {
                startCol--;
            }
        } else {
            // Look up
            while (startRow > 0 && board.getSquare(startRow - 1, col).hasTile()) {
                startRow--;
            }
        }

        // Read the word from start to end
        int currentRow = startRow;
        int currentCol = startCol;

        while (currentRow < Board.SIZE && currentCol < Board.SIZE) {
            Square square = board.getSquare(currentRow, currentCol);

            if (!square.hasTile()) {
                break;
            }

            word.append(square.getTile().getLetter());

            if (isHorizontal) {
                currentCol++;
            } else {
                currentRow++;
            }
        }

        return word.toString();
    }

    /**
     * Finds all anchor points on the board (empty squares adjacent to placed tiles).
     *
     * @param board The game board
     * @return List of anchor points
     */
    private List<Point> findAnchorPoints(Board board) {
        List<Point> anchorPoints = new ArrayList<>();

        for (int row = 0; row < Board.SIZE; row++) {
            for (int col = 0; col < Board.SIZE; col++) {
                // Skip occupied squares
                if (board.getSquare(row, col).hasTile()) {
                    continue;
                }

                // Check if this empty square is adjacent to any tile
                if (hasAdjacentTile(board, row, col)) {
                    anchorPoints.add(new Point(row, col));
                }
            }
        }

        return anchorPoints;
    }

    /**
     * Checks if a position has any adjacent tiles.
     *
     * @param board The game board
     * @param row The row
     * @param col The column
     * @return true if any adjacent square has a tile
     */
    private boolean hasAdjacentTile(Board board, int row, int col) {
        // Check all four adjacent positions
        if (row > 0 && board.getSquare(row - 1, col).hasTile()) return true;
        if (row < Board.SIZE - 1 && board.getSquare(row + 1, col).hasTile()) return true;
        if (col > 0 && board.getSquare(row, col - 1).hasTile()) return true;
        if (col < Board.SIZE - 1 && board.getSquare(row, col + 1).hasTile()) return true;

        return false;
    }

    /**
     * Finds all valid words that can be formed from rack letters.
     *
     * @param rackLetters The letters in the rack
     * @param dictionary The game dictionary
     * @return Set of valid words
     */
    private Set<String> findValidWordsFromRack(String rackLetters, Dictionary dictionary) {
        Set<String> validWords = new HashSet<>();

        // Generate all possible permutations of different lengths
        for (int len = 2; len <= rackLetters.length(); len++) {
            findWordsOfLength(rackLetters, len, "", dictionary, validWords);
        }

        return validWords;
    }

    /**
     * Recursively finds words of a specific length from available letters.
     *
     * @param letters Available letters
     * @param length Target word length
     * @param current Current partial word
     * @param dictionary The game dictionary
     * @param validWords Output set of valid words
     */
    private void findWordsOfLength(String letters, int length, String current,
                                   Dictionary dictionary, Set<String> validWords) {
        if (current.length() == length) {
            if (dictionary.isValidWord(current)) {
                validWords.add(current);
            }
            return;
        }

        for (int i = 0; i < letters.length(); i++) {
            char letter = letters.charAt(i);
            findWordsOfLength(
                    letters.substring(0, i) + letters.substring(i + 1),
                    length,
                    current + letter,
                    dictionary,
                    validWords
            );
        }
    }

    /**
     * Gets the tiles needed to form a word from the rack.
     *
     * @param word The word to form
     * @param rackTiles Available tiles in rack
     * @return List of tiles needed, or null if not possible
     */
    private List<Tile> getTilesForWord(String word, List<Tile> rackTiles) {
        // Count available letters in rack
        Map<Character, List<Tile>> availableTiles = new HashMap<>();

        for (Tile tile : rackTiles) {
            char letter = tile.getLetter();
            if (!availableTiles.containsKey(letter)) {
                availableTiles.put(letter, new ArrayList<>());
            }
            availableTiles.get(letter).add(tile);
        }

        // Try to form the word with available tiles
        List<Tile> result = new ArrayList<>();

        for (char c : word.toCharArray()) {
            // Check if we have this letter
            if (availableTiles.containsKey(c) && !availableTiles.get(c).isEmpty()) {
                // Use a tile with this letter
                Tile tile = availableTiles.get(c).remove(0);
                result.add(tile);
            } else {
                // Check for blank tiles
                if (availableTiles.containsKey('*') && !availableTiles.get('*').isEmpty()) {
                    // Use a blank tile
                    Tile blankTile = availableTiles.get('*').remove(0);
                    Tile letterTile = Tile.createBlankTile(c);
                    result.add(letterTile);
                } else {
                    // Can't form this word
                    return null;
                }
            }
        }

        return result;
    }

    /**
     * Calculates the score for a word placement.
     *
     * @param word The word
     * @param startRow The starting row
     * @param startCol The starting column
     * @param isHorizontal Whether the word is horizontal
     * @param board The game board
     * @return The score for the word
     */
    private int calculateWordScore(String word, int startRow, int startCol,
                                   boolean isHorizontal, Board board) {
        int score = 0;
        int wordMultiplier = 1;

        int row = startRow;
        int col = startCol;

        for (int i = 0; i < word.length(); i++) {
            Square square = board.getSquare(row, col);
            char letter = word.charAt(i);
            int letterValue = TileBag.getPointValue(letter);

            // Apply letter premium if square is unused
            if (!square.hasTile() && !square.isSquareTypeUsed()) {
                if (square.getSquareType() == Square.SquareType.DOUBLE_LETTER) {
                    letterValue *= 2;
                } else if (square.getSquareType() == Square.SquareType.TRIPLE_LETTER) {
                    letterValue *= 3;
                }

                // Collect word multipliers
                if (square.getSquareType() == Square.SquareType.DOUBLE_WORD ||
                        square.getSquareType() == Square.SquareType.CENTER) {
                    wordMultiplier *= 2;
                } else if (square.getSquareType() == Square.SquareType.TRIPLE_WORD) {
                    wordMultiplier *= 3;
                }
            }

            score += letterValue;

            // Move to next position
            if (isHorizontal) {
                col++;
            } else {
                row++;
            }
        }

        // Apply word multiplier
        score *= wordMultiplier;

        // Add bonus for using all 7 tiles
        if (word.length() == 7) {
            score += ScrabbleConstants.BINGO_BONUS;
        }

        return score;
    }

    /**
     * Calculates the score for a move with a single new tile placement.
     *
     * @param move The move
     * @param board The game board
     * @param newTile The new tile being placed
     * @param row The row of the new tile
     * @param col The column of the new tile
     * @return The total score for the move
     */
    private int calculateMoveScore(Move move, Board board, Tile newTile, int row, int col) {
        int totalScore = 0;

        // Calculate score for each formed word
        for (String word : move.getFormedWords()) {
            // Find where this word starts
            Point wordStart = findWordStart(board, word, row, col);
            if (wordStart == null) continue;

            // Determine if the word is horizontal
            boolean isHorizontal = isWordHorizontal(board, word, wordStart.x, wordStart.y, row, col);

            // Calculate score for this word
            int wordScore = calculateWordScore(word, wordStart.x, wordStart.y, isHorizontal, board);
            totalScore += wordScore;
        }

        // Add bonus for using all 7 tiles (bingo)
        if (move.getTiles().size() == 7) {
            totalScore += ScrabbleConstants.BINGO_BONUS;
        }

        return totalScore;
    }

    /**
     * Calculates the score for a move with multiple new tile placements.
     *
     * @param move The move
     * @param board The game board
     * @param newTilePos Position of a new tile being placed
     * @return The total score for the move
     */
    private int calculateMoveScore(Move move, Board board, Point newTilePos) {
        int totalScore = 0;

        // Calculate score for each formed word
        for (String word : move.getFormedWords()) {
            // Determine if the word contains the new tile position
            boolean isHorizontal = move.getDirection() == Move.Direction.HORIZONTAL;

            // Calculate score based on direction and position
            int wordScore = calculateWordScore(word, move.getStartRow(), move.getStartCol(), isHorizontal, board);
            totalScore += wordScore;
        }

        // Add bonus for using all 7 tiles (bingo)
        if (move.getTiles().size() == 7) {
            totalScore += ScrabbleConstants.BINGO_BONUS;
        }

        return totalScore;
    }

    /**
     * Finds the starting position of a word given a tile within it.
     *
     * @param board The game board
     * @param word The word to find
     * @param tileRow Row of a tile in the word
     * @param tileCol Column of a tile in the word
     * @return The starting position (row, col) or null if not found
     */
    private Point findWordStart(Board board, String word, int tileRow, int tileCol) {
        // Try horizontal direction
        for (int col = Math.max(0, tileCol - word.length() + 1); col <= tileCol; col++) {
            if (col + word.length() <= Board.SIZE) {
                String horizontalWord = getWordAt(board, tileRow, col, true);
                if (horizontalWord.equals(word)) {
                    return new Point(tileRow, col);
                }
            }
        }

        // Try vertical direction
        for (int row = Math.max(0, tileRow - word.length() + 1); row <= tileRow; row++) {
            if (row + word.length() <= Board.SIZE) {
                String verticalWord = getWordAt(board, row, tileCol, false);
                if (verticalWord.equals(word)) {
                    return new Point(row, tileCol);
                }
            }
        }

        return null;
    }

    /**
     * Determines if a word is placed horizontally.
     *
     * @param board The game board
     * @param word The word
     * @param startRow Starting row
     * @param startCol Starting column
     * @param tileRow Row of a tile in the word
     * @param tileCol Column of a tile in the word
     * @return true if horizontal, false if vertical
     */
    private boolean isWordHorizontal(Board board, String word, int startRow, int startCol,
                                     int tileRow, int tileCol) {
        // If the starting row is the same as the tile row, it's horizontal
        return startRow == tileRow;
    }

    /**
     * Helper methods for tile and rack operations
     */

    /**
     * Gets unique letters from a string.
     *
     * @param letters The input string
     * @return Set of unique letters
     */
    private Set<Character> getUniqueLetters(String letters) {
        Set<Character> uniqueLetters = new HashSet<>();
        for (char c : letters.toCharArray()) {
            uniqueLetters.add(c);
        }
        return uniqueLetters;
    }

    /**
     * Converts a list of tiles to a string of letters.
     *
     * @param tiles The tiles
     * @return String of letters
     */
    private String getTilesAsString(List<Tile> tiles) {
        StringBuilder sb = new StringBuilder();
        for (Tile tile : tiles) {
            sb.append(tile.getLetter());
        }
        return sb.toString();
    }

    /**
     * Removes a letter from a string.
     *
     * @param letters The input string
     * @param letter The letter to remove
     * @return The string with the letter removed
     */
    private String removeLetterFromString(String letters, char letter) {
        int index = letters.indexOf(letter);
        if (index == -1) return letters;
        return letters.substring(0, index) + letters.substring(index + 1);
    }

    /**
     * Checks if a letter is a vowel.
     *
     * @param letter The letter to check
     * @return true if it's a vowel
     */
    private boolean isVowel(char letter) {
        letter = Character.toUpperCase(letter);
        return letter == 'A' || letter == 'E' || letter == 'I' ||
                letter == 'O' || letter == 'U';
    }

    /**
     * Counts the number of vowels in a list of tiles.
     *
     * @param tiles The tiles
     * @return The number of vowels
     */
    private int countVowels(List<Tile> tiles) {
        int count = 0;
        for (Tile tile : tiles) {
            if (isVowel(tile.getLetter())) {
                count++;
            }
        }
        return count;
    }
}