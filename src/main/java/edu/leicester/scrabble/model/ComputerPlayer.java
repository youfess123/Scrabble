package edu.leicester.scrabble.model;

import edu.leicester.scrabble.util.*;
import java.awt.Point;
import java.util.*;

public class ComputerPlayer {
    private final Player player;
    private final Random random;
    private final int difficultyLevel;

    public ComputerPlayer(Player player, int difficultyLevel) {
        this.player = player;
        this.random = new Random();
        this.difficultyLevel = Math.max(1, Math.min(3, difficultyLevel));
        player.setComputer(true);
    }

    public Player getPlayer() {
        return player;
    }

    public Move generateMove(Game game) {
        try {
            System.out.println("Computer player generating move at difficulty " + difficultyLevel);

            if (player.getRack().size() == 0) {
                System.out.println("Computer has no tiles, passing");
                return Move.createPassMove(player);
            }

            List<Move> possibleMoves = findPossibleMoves(game);
            System.out.println("Found " + possibleMoves.size() + " possible moves");

            if (possibleMoves.isEmpty()) {
                System.out.println("Computer: No possible word placements found, trying fallback");
                return generateFallbackMove(game);
            }

            possibleMoves.sort(Comparator.comparing(Move::getScore).reversed());

            // Select move based on difficulty level
            Move selectedMove = selectMoveByDifficulty(possibleMoves);

            System.out.println("Computer selected move with score: " + selectedMove.getScore());
            System.out.println("Words formed: " + String.join(", ", selectedMove.getFormedWords()));

            return selectedMove;
        } catch (Exception e) {
            System.err.println("Error generating computer move: " + e.getMessage());
            e.printStackTrace();
            return Move.createPassMove(player);
        }
    }

    private Move selectMoveByDifficulty(List<Move> possibleMoves) {
        switch (difficultyLevel) {
            case 1: // Easy - random move
                return possibleMoves.get(random.nextInt(possibleMoves.size()));
            case 2: // Medium - random from top half
                int mediumCutoff = Math.max(1, possibleMoves.size() / 2);
                return possibleMoves.get(random.nextInt(mediumCutoff));
            case 3: // Hard - random from top 3
                int hardCutoff = Math.min(3, possibleMoves.size());
                return possibleMoves.get(random.nextInt(hardCutoff));
            default:
                int defaultCutoff = Math.max(1, possibleMoves.size() / 2);
                return possibleMoves.get(random.nextInt(defaultCutoff));
        }
    }

    private Move generateFallbackMove(Game game) {
        try {
            // Try to exchange tiles if there are enough in the bag
            if (game.getTileBag().getTileCount() >= 7) {
                System.out.println("Computer: Generating exchange move");
                List<Tile> tilesToExchange = selectOptimalTilesToExchange();

                if (!tilesToExchange.isEmpty()) {
                    System.out.println("Computer exchanging " + tilesToExchange.size() + " tiles");
                    return Move.createExchangeMove(player, tilesToExchange);
                }
            }

            // If exchange isn't possible, pass
            System.out.println("Computer: Generating pass move");
            return Move.createPassMove(player);
        } catch (Exception e) {
            System.err.println("Error generating fallback move: " + e.getMessage());
            e.printStackTrace();
            return Move.createPassMove(player);
        }
    }

    private List<Tile> selectOptimalTilesToExchange() {
        Rack rack = player.getRack();
        List<Tile> availableTiles = new ArrayList<>(rack.getTiles());
        List<Tile> tilesToExchange = new ArrayList<>();

        // Score each tile based on usefulness
        Map<Tile, Double> tileScores = scoreTilesForExchange(availableTiles);

        // Sort tiles by score (lowest = exchange first)
        availableTiles.sort(Comparator.comparing(tile -> tileScores.getOrDefault(tile, 0.0)));

        // Determine number of tiles to exchange based on difficulty
        int numToExchange = determineExchangeCount();

        // Select lowest-scoring tiles to exchange
        for (int i = 0; i < numToExchange && i < availableTiles.size(); i++) {
            if (tileScores.getOrDefault(availableTiles.get(i), 0.0) < 0) {
                tilesToExchange.add(availableTiles.get(i));
            }
        }

        return tilesToExchange;
    }

    private Map<Tile, Double> scoreTilesForExchange(List<Tile> availableTiles) {
        Map<Tile, Double> tileScores = new HashMap<>();
        Map<Character, Integer> letterCounts = countLetters(availableTiles);
        int vowelCount = countVowels(availableTiles);

        for (Tile tile : availableTiles) {
            char letter = tile.getLetter();
            double score = 0;

            // High value tiles might be hard to place
            if (tile.getValue() >= 8) {
                score -= 10;
            } else if (tile.getValue() >= 4) {
                score -= 5;
            }

            // Too many of the same consonant is bad
            if (!isVowel(letter) && letterCounts.get(letter) > 2) {
                score -= 8;
            }

            // Balance vowels (2-3 is good)
            if (isVowel(letter)) {
                if (vowelCount <= 2) {
                    score += 10; // Keep vowels if we have few
                } else if (vowelCount > 3) {
                    score -= 5;  // Exchange vowels if we have many
                }
            }

            // Hard-to-use letters
            if (letter == 'Q' || letter == 'Z' || letter == 'X' || letter == 'J') {
                score -= 7;
            }

            tileScores.put(tile, score);
        }

        return tileScores;
    }

    private int determineExchangeCount() {
        switch (difficultyLevel) {
            case 1: return 4; // Easy - exchange more tiles
            case 2: return 3; // Medium
            case 3: return 2; // Hard - exchange fewer tiles
            default: return 3;
        }
    }

    private Map<Character, Integer> countLetters(List<Tile> tiles) {
        Map<Character, Integer> counts = new HashMap<>();
        for (Tile tile : tiles) {
            char letter = tile.getLetter();
            counts.put(letter, counts.getOrDefault(letter, 0) + 1);
        }
        return counts;
    }

    private List<Move> findPossibleMoves(Game game) {
        List<Move> possibleMoves = new ArrayList<>();
        Board board = game.getBoard();
        Dictionary dictionary = game.getDictionary();
        Rack rack = player.getRack();

        // Special case for empty board
        if (board.isEmpty()) {
            findMovesForEmptyBoard(game, possibleMoves);
            return possibleMoves;
        }

        // Find anchor points (empty squares adjacent to placed tiles)
        List<Point> anchorPoints = BoardUtils.findAnchorPoints(board);
        System.out.println("Found " + anchorPoints.size() + " anchor points");

        // For each anchor point, find possible placements
        for (Point anchor : anchorPoints) {
            findPlacementsAt(game, anchor.x, anchor.y, Move.Direction.HORIZONTAL, possibleMoves);
            findPlacementsAt(game, anchor.x, anchor.y, Move.Direction.VERTICAL, possibleMoves);
        }

        return possibleMoves;
    }

    private void findMovesForEmptyBoard(Game game, List<Move> possibleMoves) {
        Dictionary dictionary = game.getDictionary();
        Rack rack = player.getRack();
        String rackLetters = getTilesAsString(rack.getTiles());

        // Find all valid words that can be formed with rack letters
        Set<String> validWords = findValidWordsFromRack(rackLetters, dictionary);

        for (String word : validWords) {
            if (word.length() < 2) continue;

            // Try horizontal placements through center
            for (int offset = 0; offset < word.length(); offset++) {
                int startCol = ScrabbleConstants.CENTER_SQUARE - offset;
                if (startCol >= 0 && startCol + word.length() <= Board.SIZE) {
                    createMoveForWord(word, ScrabbleConstants.CENTER_SQUARE, startCol,
                            Move.Direction.HORIZONTAL, rack, game, possibleMoves);
                }
            }

            // Try vertical placements through center
            for (int offset = 0; offset < word.length(); offset++) {
                int startRow = ScrabbleConstants.CENTER_SQUARE - offset;
                if (startRow >= 0 && startRow + word.length() <= Board.SIZE) {
                    createMoveForWord(word, startRow, ScrabbleConstants.CENTER_SQUARE,
                            Move.Direction.VERTICAL, rack, game, possibleMoves);
                }
            }
        }
    }

    private void createMoveForWord(String word, int row, int col, Move.Direction direction,
                                   Rack rack, Game game, List<Move> possibleMoves) {
        // Get tiles needed to form the word
        List<Tile> tilesForWord = getTilesForWord(word, rack.getTiles());

        if (tilesForWord != null) {
            Move move = Move.createPlaceMove(player, row, col, direction);
            move.addTiles(tilesForWord);

            // Calculate score
            Board tempBoard = BoardUtils.copyBoard(game.getBoard());
            List<Point> newPositions = placeTilesForWord(tempBoard, word, row, col, direction, tilesForWord);
            Set<Point> newPositionsSet = new HashSet<>(newPositions);

            int score = calculateWordScore(word, row, col, direction, tempBoard, newPositionsSet);
            move.setScore(score);

            // Set formed words
            List<String> formedWords = new ArrayList<>();
            formedWords.add(word);
            move.setFormedWords(formedWords);

            possibleMoves.add(move);
        }
    }

    private List<Point> placeTilesForWord(Board board, String word, int row, int col,
                                          Move.Direction direction, List<Tile> tiles) {
        List<Point> positions = new ArrayList<>();
        int currentRow = row;
        int currentCol = col;
        int tileIndex = 0;

        for (int i = 0; i < word.length(); i++) {
            if (!board.getSquare(currentRow, currentCol).hasTile() && tileIndex < tiles.size()) {
                board.placeTile(currentRow, currentCol, tiles.get(tileIndex));
                positions.add(new Point(currentRow, currentCol));
                tileIndex++;
            }

            if (direction == Move.Direction.HORIZONTAL) {
                currentCol++;
            } else {
                currentRow++;
            }
        }

        return positions;
    }

    private void findPlacementsAt(Game game, int row, int col, Move.Direction direction, List<Move> possibleMoves) {
        Board board = game.getBoard();
        Rack rack = player.getRack();
        String rackLetters = getTilesAsString(rack.getTiles());

        // Get partial words at this position
        String[] partialWords = BoardUtils.getPartialWordsAt(board, row, col, direction);
        String prefix = partialWords[0];
        String suffix = partialWords[1];

        // Try each letter in the rack at this position
        for (char letter : getUniqueLetters(rackLetters)) {
            findWordsWithLetterAt(game, row, col, letter, direction, possibleMoves);
        }
    }

    private void findWordsWithLetterAt(Game game, int row, int col, char letter,
                                       Move.Direction direction, List<Move> possibleMoves) {
        Board board = game.getBoard();
        Dictionary dictionary = game.getDictionary();
        Rack rack = player.getRack();

        // Get partial words surrounding this position
        String[] partialWords = BoardUtils.getPartialWordsAt(board, row, col, direction);
        String prefix = partialWords[0];
        String suffix = partialWords[1];

        // Form the complete word with our letter in the middle
        String completeWord = prefix + letter + suffix;

        // Skip short or invalid words
        if (completeWord.length() < 2 || !dictionary.isValidWord(completeWord)) {
            return;
        }

        // Find the start position of the word
        int startRow, startCol;
        if (direction == Move.Direction.HORIZONTAL) {
            startRow = row;
            startCol = col - prefix.length();
        } else {
            startRow = row - prefix.length();
            startCol = col;
        }

        // Check if we have all necessary tiles
        Tile tilePlaced = findTileWithLetter(rack.getTiles(), letter);
        if (tilePlaced == null) {
            return;
        }

        // Create and validate the move
        Move move = createMoveWithTile(row, col, direction, startRow, startCol,
                completeWord, tilePlaced, game);

        if (move != null) {
            possibleMoves.add(move);

            // Try extending this placement
            Board tempBoard = BoardUtils.copyBoard(board);
            tempBoard.placeTile(row, col, tilePlaced);
            extendPlacement(game, move, tempBoard, row, col, direction, possibleMoves);
        }
    }

    private Move createMoveWithTile(int row, int col, Move.Direction direction,
                                    int startRow, int startCol, String completeWord,
                                    Tile tilePlaced, Game game) {
        Board board = game.getBoard();
        Dictionary dictionary = game.getDictionary();

        // Create a temporary board to test the placement
        Board tempBoard = BoardUtils.copyBoard(board);
        tempBoard.placeTile(row, col, tilePlaced);

        // Create the move
        Move move = Move.createPlaceMove(player, startRow, startCol, direction);
        List<Tile> tilesNeeded = new ArrayList<>();
        tilesNeeded.add(tilePlaced);
        move.addTiles(tilesNeeded);

        // Check for cross-words
        List<String> formedWords = new ArrayList<>();
        formedWords.add(completeWord);

        String crossWord;
        if (direction == Move.Direction.HORIZONTAL) {
            crossWord = BoardUtils.getWordAt(tempBoard, row, col, Move.Direction.VERTICAL);
        } else {
            crossWord = BoardUtils.getWordAt(tempBoard, row, col, Move.Direction.HORIZONTAL);
        }

        if (crossWord.length() >= 2) {
            if (!dictionary.isValidWord(crossWord)) {
                return null; // Invalid cross-word
            }
            formedWords.add(crossWord);
        }

        move.setFormedWords(formedWords);

        // Calculate score
        Set<Point> newPositions = new HashSet<>();
        newPositions.add(new Point(row, col));
        int score = ScoreCalculator.calculateMoveScore(move, tempBoard, formedWords, newPositions);
        move.setScore(score);

        return move;
    }

    private void extendPlacement(Game game, Move baseMove, Board tempBoard,
                                 int row, int col, Move.Direction direction, List<Move> possibleMoves) {
        Dictionary dictionary = game.getDictionary();
        Rack rack = player.getRack();

        // Get remaining tiles (excluding those already used in base move)
        List<Tile> remainingTiles = new ArrayList<>(rack.getTiles());
        for (Tile t : baseMove.getTiles()) {
            remainingTiles.remove(t);
        }

        if (remainingTiles.isEmpty()) {
            return;
        }

        // Determine next position to try
        int nextRow = row;
        int nextCol = col;
        if (direction == Move.Direction.HORIZONTAL) {
            nextCol = col + 1;
        } else {
            nextRow = row + 1;
        }

        // Check if next position is valid
        if (nextRow >= Board.SIZE || nextCol >= Board.SIZE ||
                tempBoard.getSquare(nextRow, nextCol).hasTile()) {
            return;
        }

        // Try each remaining tile at the next position
        for (Tile tile : remainingTiles) {
            Board extendedBoard = BoardUtils.copyBoard(tempBoard);
            extendedBoard.placeTile(nextRow, nextCol, tile);

            // Check formed words
            List<String> formedWords = new ArrayList<>();
            String mainWord;

            if (direction == Move.Direction.HORIZONTAL) {
                mainWord = BoardUtils.getWordAt(extendedBoard, baseMove.getStartRow(),
                        baseMove.getStartCol(), Move.Direction.HORIZONTAL);
            } else {
                mainWord = BoardUtils.getWordAt(extendedBoard, baseMove.getStartRow(),
                        baseMove.getStartCol(), Move.Direction.VERTICAL);
            }

            if (mainWord.length() < 2 || !dictionary.isValidWord(mainWord)) {
                continue;
            }

            formedWords.add(mainWord);

            // Check cross-word
            String crossWord;
            if (direction == Move.Direction.HORIZONTAL) {
                crossWord = BoardUtils.getWordAt(extendedBoard, nextRow, nextCol, Move.Direction.VERTICAL);
            } else {
                crossWord = BoardUtils.getWordAt(extendedBoard, nextRow, nextCol, Move.Direction.HORIZONTAL);
            }

            if (crossWord.length() >= 2) {
                if (!dictionary.isValidWord(crossWord)) {
                    continue;
                }
                formedWords.add(crossWord);
            }

            // Create extended move
            Move extendedMove = Move.createPlaceMove(player, baseMove.getStartRow(),
                    baseMove.getStartCol(), direction);
            List<Tile> allTiles = new ArrayList<>(baseMove.getTiles());
            allTiles.add(tile);
            extendedMove.addTiles(allTiles);
            extendedMove.setFormedWords(formedWords);

            // Calculate score
            Set<Point> newPositions = new HashSet<>();
            newPositions.add(new Point(row, col));
            newPositions.add(new Point(nextRow, nextCol));
            int score = ScoreCalculator.calculateMoveScore(extendedMove, extendedBoard,
                    formedWords, newPositions);
            extendedMove.setScore(score);

            possibleMoves.add(extendedMove);

            // Recursively try extending further
            extendPlacement(game, extendedMove, extendedBoard, nextRow, nextCol,
                    direction, possibleMoves);
        }
    }

    // Utility methods

    private Tile findTileWithLetter(List<Tile> tiles, char letter) {
        for (Tile tile : tiles) {
            if (tile.getLetter() == letter) {
                return tile;
            }
        }
        return null;
    }

    private Set<String> findValidWordsFromRack(String rackLetters, Dictionary dictionary) {
        Set<String> validWords = new HashSet<>();
        // Find words of all possible lengths
        for (int len = 2; len <= rackLetters.length(); len++) {
            findWordsOfLength(rackLetters, len, "", dictionary, validWords);
        }
        return validWords;
    }

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
            String remaining = letters.substring(0, i) + letters.substring(i + 1);
            findWordsOfLength(remaining, length, current + letter, dictionary, validWords);
        }
    }

    private List<Tile> getTilesForWord(String word, List<Tile> rackTiles) {
        // Group tiles by letter
        Map<Character, List<Tile>> availableTiles = new HashMap<>();
        for (Tile tile : rackTiles) {
            char letter = tile.getLetter();
            if (!availableTiles.containsKey(letter)) {
                availableTiles.put(letter, new ArrayList<>());
            }
            availableTiles.get(letter).add(tile);
        }

        List<Tile> result = new ArrayList<>();

        // Try to match each letter in the word
        for (char c : word.toCharArray()) {
            if (availableTiles.containsKey(c) && !availableTiles.get(c).isEmpty()) {
                // Use a regular tile if available
                Tile tile = availableTiles.get(c).remove(0);
                result.add(tile);
            } else if (availableTiles.containsKey('*') && !availableTiles.get('*').isEmpty()) {
                // Use a blank tile if available
                Tile blankTile = availableTiles.get('*').remove(0);
                Tile letterTile = Tile.createBlankTile(c);
                result.add(letterTile);
            } else {
                // Can't form the word with available tiles
                return null;
            }
        }

        return result;
    }

    private int calculateWordScore(String word, int startRow, int startCol,
                                   Move.Direction direction, Board board, Set<Point> newTilePositions) {
        boolean isHorizontal = direction == Move.Direction.HORIZONTAL;
        return ScoreCalculator.calculateWordScore(word, startRow, startCol, isHorizontal,
                board, newTilePositions);
    }

    private String getTilesAsString(List<Tile> tiles) {
        StringBuilder sb = new StringBuilder();
        for (Tile tile : tiles) {
            sb.append(tile.getLetter());
        }
        return sb.toString();
    }

    private Set<Character> getUniqueLetters(String letters) {
        Set<Character> uniqueLetters = new HashSet<>();
        for (char c : letters.toCharArray()) {
            uniqueLetters.add(c);
        }
        return uniqueLetters;
    }

    private boolean isVowel(char letter) {
        letter = Character.toUpperCase(letter);
        return letter == 'A' || letter == 'E' || letter == 'I' || letter == 'O' || letter == 'U';
    }

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