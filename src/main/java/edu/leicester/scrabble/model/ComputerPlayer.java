package edu.leicester.scrabble.model;


import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

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

                // FIX: Use the actual Tile objects from the rack instead of creating copies
                List<Tile> availableTiles = new ArrayList<>();
                for (int i = 0; i < rack.size(); i++) {
                    availableTiles.add(rack.getTile(i));
                }

                // Exchange 1-3 tiles based on difficulty
                int numToExchange = random.nextInt(4 - difficultyLevel) + 1;
                numToExchange = Math.min(numToExchange, availableTiles.size());

                // FIX: Add direct tile references to exchange list
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

    // Rest of the ComputerPlayer class implementation...
    private List<Move> findPossibleMoves(Game game) {
        List<Move> possibleMoves = new ArrayList<>();
        Board board = game.getBoard();
        Dictionary dictionary = game.getDictionary();

        // If the board is empty, place a word through the center
        if (board.isEmpty()) {
            findFirstMoves(game, possibleMoves);
            return possibleMoves;
        }

        // Find anchor squares (empty squares adjacent to placed tiles)
        List<Square> anchorSquares = findAnchorSquares(board);

        // For each anchor square, find possible placements
        for (Square anchor : anchorSquares) {
            // Convert rack to string for GADDAG
            String rackLetters = getRackLetters();

            // Check if there are tiles above/below and to the left/right
            boolean hasAbove = anchor.getRow() > 0 && board.getSquare(anchor.getRow() - 1, anchor.getCol()).hasTile();
            boolean hasBelow = anchor.getRow() < Board.SIZE - 1 && board.getSquare(anchor.getRow() + 1, anchor.getCol()).hasTile();
            boolean hasLeft = anchor.getCol() > 0 && board.getSquare(anchor.getRow(), anchor.getCol() - 1).hasTile();
            boolean hasRight = anchor.getCol() < Board.SIZE - 1 && board.getSquare(anchor.getRow(), anchor.getCol() + 1).hasTile();

            // If there are tiles horizontally, try vertical placement
            if (hasLeft || hasRight) {
                findVerticalPlacements(game, anchor, possibleMoves);
            }

            // If there are tiles vertically, try horizontal placement
            if (hasAbove || hasBelow) {
                findHorizontalPlacements(game, anchor, possibleMoves);
            }

            // If no adjacent tiles, try both directions
            if (!hasAbove && !hasBelow && !hasLeft && !hasRight) {
                findVerticalPlacements(game, anchor, possibleMoves);
                findHorizontalPlacements(game, anchor, possibleMoves);
            }
        }

        return possibleMoves;
    }

    private void findFirstMoves(Game game, List<Move> possibleMoves) {
        // For the first move, we'll just try to find valid words using the rack letters
        // that can be placed through the center square

        // Get rack letters
        String rackLetters = getRackLetters();

        // Find words that can be formed with the rack letters
        for (int length = 2; length <= Math.min(rackLetters.length(), 7); length++) {
            findWordsOfLength(rackLetters, length, "", game.getDictionary(), new ArrayList<>());
        }

        // Placeholder implementation
        // A complete implementation would generate candidate words using the GADDAG
        // and verify they can be placed through the center square
    }

    private void findWordsOfLength(String letters, int length, String current, Dictionary dictionary, List<String> validWords) {
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

    private void findVerticalPlacements(Game game, Square anchor, List<Move> possibleMoves) {
        // Placeholder implementation
        // A complete implementation would use the GADDAG to find valid words
        // that can be placed vertically through the anchor square
    }

    private void findHorizontalPlacements(Game game, Square anchor, List<Move> possibleMoves) {
        // Placeholder implementation
        // A complete implementation would use the GADDAG to find valid words
        // that can be placed horizontally through the anchor square
    }

    private String getRackLetters() {
        StringBuilder letters = new StringBuilder();
        for (Tile tile : player.getRack().getTiles()) {
            letters.append(tile.getLetter());
        }
        return letters.toString();
    }

    private int evaluateMove(Game game, Move move) {
        // Placeholder for move evaluation logic
        // A complete implementation would calculate the score based on
        // letter values, premium squares, etc.
        return 0;
    }
}