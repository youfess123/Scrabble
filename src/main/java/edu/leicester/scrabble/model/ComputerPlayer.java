package edu.leicester.scrabble.model;

import edu.leicester.scrabble.util.ScrabbleConstants;

import java.util.*;
import java.util.List;

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

    public Move generateMove(Game game) {
        try {
            System.out.println("Computer player generating move...");

            // Safety check - if no tiles in rack, pass turn
            if (player.getRack().size() == 0) {
                System.out.println("Computer has no tiles, passing");
                return Move.createPassMove(player);
            }

            // Get possible moves with a timeout protection
            List<Move> possibleMoves = new ArrayList<>();
            try {
                // Try to find possible moves with a time limit
                possibleMoves = findPossibleMoves(game);
                System.out.println("Found " + possibleMoves.size() + " possible moves");
            } catch (Exception e) {
                System.err.println("Error finding possible moves: " + e.getMessage());
                e.printStackTrace();
                // If move generation fails, default to fallback move
                return generateFallbackMove(game);
            }

            // If there are no possible moves, either exchange tiles or pass
            if (possibleMoves.isEmpty()) {
                System.out.println("Computer: No possible word placements found, trying fallback");
                return generateFallbackMove(game);
            }

            // Sort moves by score
            possibleMoves.sort(Comparator.comparing(Move::getScore).reversed());

            // Select a move based on difficulty level
            // Higher difficulty levels choose better moves more consistently
            int selectedIndex;

            switch (difficultyLevel) {
                case 1: // Easy - Randomly select from all moves
                    selectedIndex = random.nextInt(possibleMoves.size());
                    break;

                case 2: // Medium - Select from the top half of moves
                    selectedIndex = random.nextInt(Math.max(1, possibleMoves.size() / 2));
                    break;

                case 3: // Hard - Select from the top few moves
                    selectedIndex = random.nextInt(Math.max(1, Math.min(3, possibleMoves.size())));
                    break;

                default: // Default to medium
                    selectedIndex = random.nextInt(Math.max(1, possibleMoves.size() / 2));
            }

            System.out.println("Computer selected move with score: " + possibleMoves.get(selectedIndex).getScore());
            return possibleMoves.get(selectedIndex);

        } catch (Exception e) {
            System.err.println("Error generating computer move: " + e.getMessage());
            e.printStackTrace();

            // Return a PASS move when something goes wrong
            return Move.createPassMove(player);
        }
    }

    private Move generateFallbackMove(Game game) {
        try {
            // If there are enough tiles in the bag, exchange some tiles
            if (game.getTileBag().getTileCount() >= 7) {
                System.out.println("Computer: Generating exchange move");
                // Select actual tiles from the rack
                Rack rack = player.getRack();
                List<Tile> tilesToExchange = new ArrayList<>();

                // Use the actual Tile objects from the rack instead of creating copies
                List<Tile> availableTiles = new ArrayList<>();
                for (int i = 0; i < rack.size(); i++) {
                    availableTiles.add(rack.getTile(i));
                }

                // Exchange 1-3 tiles based on difficulty
                int numToExchange = random.nextInt(4 - difficultyLevel) + 1;
                numToExchange = Math.min(numToExchange, availableTiles.size());

                // Add direct tile references to exchange list
                for (int i = 0; i < numToExchange && !availableTiles.isEmpty(); i++) {
                    int index = random.nextInt(availableTiles.size());
                    tilesToExchange.add(availableTiles.remove(index));
                }

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

            // Return a PASS move when something goes wrong
            return Move.createPassMove(player);
        }
    }

    private List<Move> findPossibleMoves(Game game) {
        List<Move> possibleMoves = new ArrayList<>();
        Board board = game.getBoard();
        Dictionary dictionary = game.getDictionary();

        // If the board is empty, place a word through the center
        if (board.isEmpty()) {
            findFirstMove(game, possibleMoves);
            return possibleMoves;
        }

        // Find anchor squares (empty squares adjacent to placed tiles)
        List<Square> anchorSquares = findAnchorSquares(board);

        // For each anchor square, try to find possible word placements
        for (Square anchor : anchorSquares) {
            int row = anchor.getRow();
            int col = anchor.getCol();

            // Try horizontal placements
            findHorizontalPlacements(game, row, col, possibleMoves);

            // Try vertical placements
            findVerticalPlacements(game, row, col, possibleMoves);
        }

        return possibleMoves;
    }

    private void findFirstMove(Game game, List<Move> possibleMoves) {
        // For the first move, try to place words through the center
        Dictionary dictionary = game.getDictionary();
        Rack rack = player.getRack();

        // Get all tiles from the rack
        List<Tile> rackTiles = rack.getTiles();
        String rackLetters = getTilesAsString(rackTiles);

        // Find valid words that can be formed with the rack letters
        Set<String> possibleWords = new HashSet<>();

        // Generate all permutations of the rack letters of different lengths
        for (int len = 2; len <= rackLetters.length(); len++) {
            findWordsOfLength(rackLetters, len, "", dictionary, possibleWords);
        }

        // For each possible word, try to place it through the center
        for (String word : possibleWords) {
            // Try horizontal placement
            for (int offset = 0; offset < word.length(); offset++) {
                int startCol = ScrabbleConstants.CENTER_SQUARE - offset;

                // Check if the word fits on the board and goes through center
                if (startCol >= 0 && startCol + word.length() <= Board.SIZE &&
                        startCol <= ScrabbleConstants.CENTER_SQUARE &&
                        startCol + word.length() > ScrabbleConstants.CENTER_SQUARE) {

                    // Create a move
                    Move move = Move.createPlaceMove(player, ScrabbleConstants.CENTER_SQUARE, startCol, Move.Direction.HORIZONTAL);

                    // Add tiles to the move
                    List<Tile> tilesForWord = getTilesForWord(word, rackTiles);
                    if (tilesForWord != null) {
                        move.addTiles(tilesForWord);

                        // Calculate score and set formed words
                        int score = estimateWordScore(word, ScrabbleConstants.CENTER_SQUARE, startCol, true, game.getBoard());
                        move.setScore(score);

                        List<String> formedWords = new ArrayList<>();
                        formedWords.add(word);
                        move.setFormedWords(formedWords);

                        possibleMoves.add(move);
                    }
                }
            }

            // Try vertical placement
            for (int offset = 0; offset < word.length(); offset++) {
                int startRow = ScrabbleConstants.CENTER_SQUARE - offset;

                // Check if the word fits on the board and goes through center
                if (startRow >= 0 && startRow + word.length() <= Board.SIZE &&
                        startRow <= ScrabbleConstants.CENTER_SQUARE &&
                        startRow + word.length() > ScrabbleConstants.CENTER_SQUARE) {

                    // Create a move
                    Move move = Move.createPlaceMove(player, startRow, ScrabbleConstants.CENTER_SQUARE, Move.Direction.VERTICAL);

                    // Add tiles to the move
                    List<Tile> tilesForWord = getTilesForWord(word, rackTiles);
                    if (tilesForWord != null) {
                        move.addTiles(tilesForWord);

                        // Calculate score and set formed words
                        int score = estimateWordScore(word, startRow, ScrabbleConstants.CENTER_SQUARE, false, game.getBoard());
                        move.setScore(score);

                        List<String> formedWords = new ArrayList<>();
                        formedWords.add(word);
                        move.setFormedWords(formedWords);

                        possibleMoves.add(move);
                    }
                }
            }
        }
    }

    private void findWordsOfLength(String letters, int length, String current, Dictionary dictionary, Set<String> validWords) {
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

    private List<Square> findAnchorSquares(Board board) {
        List<Square> anchorSquares = new ArrayList<>();

        for (int row = 0; row < Board.SIZE; row++) {
            for (int col = 0; col < Board.SIZE; col++) {
                Square square = board.getSquare(row, col);

                if (!square.hasTile() && board.hasAdjacentTile(row, col)) {
                    anchorSquares.add(square);
                }
            }
        }

        return anchorSquares;
    }

    private void findHorizontalPlacements(Game game, int row, int col, List<Move> possibleMoves) {
        Board board = game.getBoard();
        Dictionary dictionary = game.getDictionary();
        Rack rack = player.getRack();

        // Get available rack letters
        List<Tile> rackTiles = rack.getTiles();
        String rackLetters = getTilesAsString(rackTiles);

        // For simplicity, try to extend words to the right of any existing letters
        // Find the leftmost letter of any potential word
        int leftmostCol = col;
        while (leftmostCol > 0 && (board.getSquare(row, leftmostCol - 1).hasTile() ||
                canPlaceTileAt(row, leftmostCol - 1, board))) {
            leftmostCol--;
        }

        // Now find possible words starting from this position
        // First, build any prefix that's already on the board
        StringBuilder prefix = new StringBuilder();
        for (int c = leftmostCol; c < col; c++) {
            if (board.getSquare(row, c).hasTile()) {
                prefix.append(board.getSquare(row, c).getTile().getLetter());
            }
        }

        // For each available rack tile that could be placed at the anchor position
        for (Tile tile : rackTiles) {
            char letter = tile.getLetter();
            StringBuilder wordSoFar = new StringBuilder(prefix).append(letter);

            // Try to extend the word to the right
            tryHorizontalExtensions(game, row, col + 1, wordSoFar.toString(),
                    removeLetterFromString(rackLetters, letter),
                    leftmostCol, possibleMoves);
        }
    }

    private void tryHorizontalExtensions(Game game, int row, int col, String wordSoFar,
                                         String remainingLetters, int startCol, List<Move> possibleMoves) {
        Board board = game.getBoard();
        Dictionary dictionary = game.getDictionary();

        // If we've reached the end of the board or can't extend further
        if (col >= Board.SIZE || (!board.getSquare(row, col).hasTile() && remainingLetters.isEmpty())) {
            // Check if the current word is valid
            if (dictionary.isValidWord(wordSoFar)) {
                // Create a move
                Move move = Move.createPlaceMove(player, row, startCol, Move.Direction.HORIZONTAL);

                // Add tiles for the non-board parts of the word
                List<Tile> tilesForMove = getTilesForPartialWord(wordSoFar, startCol, row, true, board, player.getRack());
                if (tilesForMove != null && !tilesForMove.isEmpty()) {
                    move.addTiles(tilesForMove);

                    // Only add the move if it connects to the board
                    if (doesWordConnectToBoard(wordSoFar, row, startCol, true, board)) {
                        // Calculate score
                        int score = estimateWordScore(wordSoFar, row, startCol, true, board);
                        move.setScore(score);

                        // Set formed words
                        List<String> formedWords = new ArrayList<>();
                        formedWords.add(wordSoFar);
                        move.setFormedWords(formedWords);

                        possibleMoves.add(move);
                    }
                }
            }
            return;
        }

        // Rest of the method remains the same...
        // If this position already has a tile, incorporate it
        if (board.getSquare(row, col).hasTile()) {
            Tile boardTile = board.getSquare(row, col).getTile();
            tryHorizontalExtensions(game, row, col + 1, wordSoFar + boardTile.getLetter(),
                    remainingLetters, startCol, possibleMoves);
        } else {
            // Try each remaining letter
            for (int i = 0; i < remainingLetters.length(); i++) {
                char letter = remainingLetters.charAt(i);
                tryHorizontalExtensions(game, row, col + 1, wordSoFar + letter,
                        removeLetterFromString(remainingLetters, letter),
                        startCol, possibleMoves);
            }
        }
    }

    private void findVerticalPlacements(Game game, int row, int col, List<Move> possibleMoves) {
        Board board = game.getBoard();
        Dictionary dictionary = game.getDictionary();
        Rack rack = player.getRack();

        // Get available rack letters
        List<Tile> rackTiles = rack.getTiles();
        String rackLetters = getTilesAsString(rackTiles);

        // Similar to horizontal, find the topmost position
        int topmostRow = row;
        while (topmostRow > 0 && (board.getSquare(topmostRow - 1, col).hasTile() ||
                canPlaceTileAt(topmostRow - 1, col, board))) {
            topmostRow--;
        }

        // Build any prefix that's already on the board
        StringBuilder prefix = new StringBuilder();
        for (int r = topmostRow; r < row; r++) {
            if (board.getSquare(r, col).hasTile()) {
                prefix.append(board.getSquare(r, col).getTile().getLetter());
            }
        }

        // For each available rack tile that could be placed at the anchor position
        for (Tile tile : rackTiles) {
            char letter = tile.getLetter();
            StringBuilder wordSoFar = new StringBuilder(prefix).append(letter);

            // Try to extend the word downward
            tryVerticalExtensions(game, row + 1, col, wordSoFar.toString(),
                    removeLetterFromString(rackLetters, letter),
                    topmostRow, possibleMoves);
        }
    }

    private boolean doesWordConnectToBoard(String word, int startRow, int startCol, boolean isHorizontal, Board board) {
        // If board is empty, this would be the first move - it's valid if it goes through center
        if (board.isEmpty()) {
            int endRow = isHorizontal ? startRow : startRow + word.length() - 1;
            int endCol = isHorizontal ? startCol + word.length() - 1 : startCol;

            // Check if word covers center square
            if (isHorizontal) {
                return (startRow == ScrabbleConstants.CENTER_SQUARE &&
                        startCol <= ScrabbleConstants.CENTER_SQUARE &&
                        endCol >= ScrabbleConstants.CENTER_SQUARE);
            } else {
                return (startCol == ScrabbleConstants.CENTER_SQUARE &&
                        startRow <= ScrabbleConstants.CENTER_SQUARE &&
                        endRow >= ScrabbleConstants.CENTER_SQUARE);
            }
        }

        // Check if the word connects to any existing tile on the board
        for (int i = 0; i < word.length(); i++) {
            int row = isHorizontal ? startRow : startRow + i;
            int col = isHorizontal ? startCol + i : startCol;

            // Skip positions that already have tiles
            if (board.getSquare(row, col).hasTile()) {
                return true; // Word uses an existing tile, so it's connected
            }

            // Check adjacent squares (above, below, left, right)
            if (row > 0 && board.getSquare(row - 1, col).hasTile()) {
                return true; // Connected to a tile above
            }
            if (row < Board.SIZE - 1 && board.getSquare(row + 1, col).hasTile()) {
                return true; // Connected to a tile below
            }
            if (col > 0 && board.getSquare(row, col - 1).hasTile()) {
                return true; // Connected to a tile to the left
            }
            if (col < Board.SIZE - 1 && board.getSquare(row, col + 1).hasTile()) {
                return true; // Connected to a tile to the right
            }
        }

        // If we get here, the word doesn't connect to any existing tile
        return false;
    }

    private void tryVerticalExtensions(Game game, int row, int col, String wordSoFar,
                                       String remainingLetters, int startRow, List<Move> possibleMoves) {
        Board board = game.getBoard();
        Dictionary dictionary = game.getDictionary();

        // If we've reached the end of the board or can't extend further
        if (row >= Board.SIZE || (!board.getSquare(row, col).hasTile() && remainingLetters.isEmpty())) {
            // Check if the current word is valid
            if (dictionary.isValidWord(wordSoFar)) {
                // Create a move
                Move move = Move.createPlaceMove(player, startRow, col, Move.Direction.VERTICAL);

                // Add tiles for the non-board parts of the word
                List<Tile> tilesForMove = getTilesForPartialWord(wordSoFar, col, startRow, false, board, player.getRack());
                if (tilesForMove != null && !tilesForMove.isEmpty()) {
                    move.addTiles(tilesForMove);

                    // Only add the move if it connects to the board
                    if (doesWordConnectToBoard(wordSoFar, startRow, col, false, board)) {
                        // Calculate score
                        int score = estimateWordScore(wordSoFar, startRow, col, false, board);
                        move.setScore(score);

                        // Set formed words
                        List<String> formedWords = new ArrayList<>();
                        formedWords.add(wordSoFar);
                        move.setFormedWords(formedWords);

                        possibleMoves.add(move);
                    }
                }
            }
            return;
        }

        // Rest of the method remains the same...
        // If this position already has a tile, incorporate it
        if (board.getSquare(row, col).hasTile()) {
            Tile boardTile = board.getSquare(row, col).getTile();
            tryVerticalExtensions(game, row + 1, col, wordSoFar + boardTile.getLetter(),
                    remainingLetters, startRow, possibleMoves);
        } else {
            // Try each remaining letter
            for (int i = 0; i < remainingLetters.length(); i++) {
                char letter = remainingLetters.charAt(i);
                tryVerticalExtensions(game, row + 1, col, wordSoFar + letter,
                        removeLetterFromString(remainingLetters, letter),
                        startRow, possibleMoves);
            }
        }
    }

    private boolean canPlaceTileAt(int row, int col, Board board) {
        // Check if position is valid and empty
        return row >= 0 && row < Board.SIZE && col >= 0 && col < Board.SIZE && !board.getSquare(row, col).hasTile();
    }

    private String getTilesAsString(List<Tile> tiles) {
        StringBuilder sb = new StringBuilder();
        for (Tile tile : tiles) {
            sb.append(tile.getLetter());
        }
        return sb.toString();
    }

    private String removeLetterFromString(String letters, char letter) {
        int index = letters.indexOf(letter);
        if (index == -1) return letters;
        return letters.substring(0, index) + letters.substring(index + 1);
    }

    private List<Tile> getTilesForWord(String word, List<Tile> rackTiles) {
        Map<Character, Integer> letterCounts = new HashMap<>();

        // Count available letters
        for (Tile tile : rackTiles) {
            char letter = tile.getLetter();
            letterCounts.put(letter, letterCounts.getOrDefault(letter, 0) + 1);
        }

        // Check if we have all needed letters
        for (char c : word.toCharArray()) {
            if (letterCounts.getOrDefault(c, 0) <= 0) {
                return null; // Missing a required letter
            }
            letterCounts.put(c, letterCounts.get(c) - 1);
        }

        // Get the actual tiles in the right order
        List<Tile> result = new ArrayList<>();
        for (char c : word.toCharArray()) {
            for (Tile tile : rackTiles) {
                if (tile.getLetter() == c && !result.contains(tile)) {
                    result.add(tile);
                    break;
                }
            }
        }

        return result;
    }

    private List<Tile> getTilesForPartialWord(String word, int startPos, int fixedPos, boolean isHorizontal,
                                              Board board, Rack rack) {
        List<Tile> result = new ArrayList<>();

        // Create a copy of the rack tiles
        List<Tile> availableTiles = new ArrayList<>(rack.getTiles());

        // Iterate through the word positions
        for (int i = 0; i < word.length(); i++) {
            int row = isHorizontal ? fixedPos : startPos + i;
            int col = isHorizontal ? startPos + i : fixedPos;

            // Skip positions that already have tiles on the board
            if (board.getSquare(row, col).hasTile()) {
                continue;
            }

            // Find a matching tile in the rack
            char neededLetter = word.charAt(i);
            boolean found = false;

            for (int j = 0; j < availableTiles.size(); j++) {
                Tile tile = availableTiles.get(j);
                if (tile.getLetter() == neededLetter) {
                    result.add(tile);
                    availableTiles.remove(j);
                    found = true;
                    break;
                }
            }

            if (!found) {
                // Check for blanks
                for (int j = 0; j < availableTiles.size(); j++) {
                    Tile tile = availableTiles.get(j);
                    if (tile.isBlank()) {
                        // Use blank tile with the needed letter
                        result.add(Tile.createBlankTile(neededLetter));
                        availableTiles.remove(j);
                        found = true;
                        break;
                    }
                }
            }

            if (!found) {
                return null; // Can't form the word with available tiles
            }
        }

        return result;
    }

    private int estimateWordScore(String word, int startRow, int startCol, boolean isHorizontal, Board board) {
        int score = 0;
        int wordMultiplier = 1;

        for (int i = 0; i < word.length(); i++) {
            int row = isHorizontal ? startRow : startRow + i;
            int col = isHorizontal ? startCol + i : startCol;

            if (row >= Board.SIZE || col >= Board.SIZE) {
                break;
            }

            Square square = board.getSquare(row, col);
            char letter = word.charAt(i);
            int letterValue;

            if (square.hasTile()) {
                // Use value of existing tile
                letterValue = square.getTile().getValue();
            } else {
                // Use standard letter value
                letterValue = TileBag.getPointValue(letter);

                // Apply letter multiplier if square is unused
                if (!square.isSquareTypeUsed()) {
                    letterValue *= square.getLetterMultiplier();

                    // Collect word multipliers
                    wordMultiplier *= square.getWordMultiplier();
                }
            }

            score += letterValue;
        }

        // Apply word multiplier
        score *= wordMultiplier;

        // Add bonus for using all 7 tiles
        if (countNewTiles(word, startRow, startCol, isHorizontal, board) == 7) {
            score += ScrabbleConstants.BINGO_BONUS;
        }

        return score;
    }

    private int countNewTiles(String word, int startRow, int startCol, boolean isHorizontal, Board board) {
        int count = 0;

        for (int i = 0; i < word.length(); i++) {
            int row = isHorizontal ? startRow : startRow + i;
            int col = isHorizontal ? startCol + i : startCol;

            if (row >= Board.SIZE || col >= Board.SIZE) {
                break;
            }

            if (!board.getSquare(row, col).hasTile()) {
                count++;
            }
        }

        return count;
    }

}