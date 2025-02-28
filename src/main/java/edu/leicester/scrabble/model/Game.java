package edu.leicester.scrabble.model;

import edu.leicester.scrabble.util.ScrabbleConstants;

import java.awt.Point;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public class Game {
    private final Board board;
    private final TileBag tileBag;
    private final List<Player> players;
    private final Dictionary dictionary;
    private int currentPlayerIndex;
    private boolean gameOver;
    private int consecutivePasses;
    private final List<Move> moveHistory;
    private static final int EMPTY_RACK_BONUS = 50;

    public Game(InputStream dictionaryStream, String dictionaryName) throws IOException {
        this.board = new Board();
        this.tileBag = new TileBag();
        this.players = new ArrayList<>();
        this.dictionary = new Dictionary(dictionaryStream, dictionaryName);
        this.currentPlayerIndex = 0;
        this.gameOver = false;
        this.consecutivePasses = 0;
        this.moveHistory = new ArrayList<>();
    }

    public void addPlayer(Player player) {
        players.add(player);
    }

    public void start() {
        if (players.isEmpty()) {
            throw new IllegalStateException("Cannot start game with no players");
        }

        tileBag.shuffle();

        for (Player player : players) {
            fillRack(player);
        }

        currentPlayerIndex = (int) (Math.random() * players.size());
        gameOver = false;
        consecutivePasses = 0;
        moveHistory.clear();
    }

    public void fillRack(Player player) {
        Rack rack = player.getRack();
        int tilesToDraw = rack.getEmptySlots();

        if (tilesToDraw == 0) {
            return;
        }

        List<Tile> drawnTiles = tileBag.drawTiles(tilesToDraw);
        rack.addTiles(drawnTiles);
    }

    public Player getCurrentPlayer() {
        return players.get(currentPlayerIndex);
    }

    public void nextPlayer() {
        int oldIndex = currentPlayerIndex;
        currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
        System.out.println(STR."Player changed from index \{oldIndex} to \{currentPlayerIndex}");
        System.out.println(STR."Current player is now: \{getCurrentPlayer().getName()}");
    }

    public Board getBoard() {
        return board;
    }

    public TileBag getTileBag() {
        return tileBag;
    }

    public Dictionary getDictionary() {
        return dictionary;
    }

    public List<Player> getPlayers() {
        return Collections.unmodifiableList(players);
    }

    public boolean isGameOver() {
        return gameOver;
    }

    public List<Move> getMoveHistory() {
        return Collections.unmodifiableList(moveHistory);
    }

    public boolean executeMove(Move move) {
        if (gameOver) {
            System.out.println("Game is already over");
            return false;
        }

        if (move.getPlayer() != getCurrentPlayer()) {
            System.out.println("Not this player's turn");
            return false;
        }

        boolean success = false;
        System.out.println(STR."Executing move of type: \{move.getType()}");

        try {
            success = switch (move.getType()) {
                case PLACE -> executePlaceMove(move);
                case EXCHANGE -> executeExchangeMove(move);
                case PASS -> executePassMove(move);
            };
        } catch (Exception e) {
            System.err.println(STR."Error executing move: \{e.getMessage()}");
            e.printStackTrace();
            return false;
        }

        if (success) {
            System.out.println("Move executed successfully");
            moveHistory.add(move);

            if (checkGameOver()) {
                finaliseGameScore();
                return true;
            }

            nextPlayer();
            System.out.println(STR."Turn passed to: \{getCurrentPlayer().getName()}");
        } else {
            System.out.println("Move execution failed");
        }

        return success;
    }


    private boolean executePlaceMove(Move move) {
        if (!isValidPlaceMove(move)) {
            return false;
        }

        int row = move.getStartRow();
        int col = move.getStartCol();
        Player player = move.getPlayer();
        List<Tile> tilesToPlace = move.getTiles();
        Move.Direction direction = move.getDirection();

        Board tempBoard = new Board();

        for (int r = 0; r < Board.SIZE; r++) {
            for (int c = 0; c < Board.SIZE; c++) {
                Square square = board.getSquare(r, c);
                if (square.hasTile()) {
                    tempBoard.placeTile(r, c, square.getTile());
                }
            }
        }

        List<Point> newTilePositions = new ArrayList<>();

        int currentRow = row;
        int currentCol = col;

        for (Tile tile : tilesToPlace) {
            while (currentRow < Board.SIZE && currentCol < Board.SIZE &&
                    tempBoard.getSquare(currentRow, currentCol).hasTile()) {
                if (direction == Move.Direction.HORIZONTAL) {
                    currentCol++;
                } else {
                    currentRow++;
                }
            }

            if (currentRow < Board.SIZE && currentCol < Board.SIZE) {
                tempBoard.placeTile(currentRow, currentCol, tile);
                newTilePositions.add(new Point(currentRow, currentCol));

                if (direction == Move.Direction.HORIZONTAL) {
                    currentCol++;
                } else {
                    currentRow++;
                }
            }
        }

        List<String> formedWords = validateWords(tempBoard, move, newTilePositions);

        if (formedWords.isEmpty()) {
            return false;
        }

        move.setFormedWords(formedWords);

        int score = calculateMoveScore(tempBoard, move, newTilePositions);
        move.setScore(score);

        for (int i = 0; i < tilesToPlace.size(); i++) {
            Tile tile = tilesToPlace.get(i);
            Point position = (i < newTilePositions.size()) ? newTilePositions.get(i) : null;

            if (position != null) {
                // Place the tile on the board
                board.placeTile(position.x, position.y, tile);

                board.getSquare(position.x, position.y).useSquareType();

                player.getRack().removeTile(tile);
            }
        }

        player.addScore(score);

        consecutivePasses = 0;

        fillRack(player);

        if (player.getRack().isEmpty() && tileBag.isEmpty()) {
            player.addScore(EMPTY_RACK_BONUS);
        }

        return true;
    }

    private List<String> validateWords(Board tempBoard, Move move, List<Point> newTilePositions) {
        List<String> formedWords = new ArrayList<>();

        int row = move.getStartRow();
        int col = move.getStartCol();
        Move.Direction direction = move.getDirection();

        String mainWord;
        if (direction == Move.Direction.HORIZONTAL) {
            mainWord = getWordAt(tempBoard, row, findWordStart(tempBoard, row, col, true), true);
        } else {
            mainWord = getWordAt(tempBoard, findWordStart(tempBoard, row, col, false), col, false);
        }

        if (mainWord.length() < 2) {
            System.out.println("Invalid move: Main word is too short");
            return formedWords;
        }

        if (!dictionary.isValidWord(mainWord)) {
            System.out.println(STR."Invalid move: Main word '\{mainWord}' is not in dictionary");
            return formedWords;
        }

        formedWords.add(mainWord);

        for (Point p : newTilePositions) {
            String crossWord;

            if (direction == Move.Direction.HORIZONTAL) {
                crossWord = getWordAt(tempBoard, findWordStart(tempBoard, p.x, p.y, false), p.y, false);
            } else {
                crossWord = getWordAt(tempBoard, p.x, findWordStart(tempBoard, p.x, p.y, true), true);
            }

            if (crossWord.length() >= 2) {
                if (!dictionary.isValidWord(crossWord)) {
                    System.out.println(STR."Invalid move: Crossing word '\{crossWord}' is not in dictionary");
                    return new ArrayList<>();
                }

                formedWords.add(crossWord);
            }
        }

        return formedWords;
    }

    private int findWordStart(Board board, int row, int col, boolean isHorizontal) {
        int position = isHorizontal ? col : row;

        // Look backwards until we find an empty square or the board edge
        while (position > 0) {
            int prevPos = position - 1;
            Square square = isHorizontal ? board.getSquare(row, prevPos) : board.getSquare(prevPos, col);

            if (!square.hasTile()) {
                break;
            }
            position = prevPos;
        }

        return position;
    }

    private String getWordAt(Board board, int row, int col, boolean isHorizontal) {
        StringBuilder word = new StringBuilder();

        int currentRow = row;
        int currentCol = col;

        // Collect letters until we reach an empty square or the board edge
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
     * Calculates the score for a move.
     *
     * @param board The game board
     * @param move The move being scored
     * @param newTilePositions Positions of new tiles
     * @return The total score for the move
     */
    private int calculateMoveScore(Board board, Move move, List<Point> newTilePositions) {
        int totalScore = 0;
        List<String> formedWords = move.getFormedWords();

        // Track which premium squares are used
        Set<Point> usedPremiumSquares = new HashSet<>();

        // For each formed word
        for (String word : formedWords) {
            int wordScore = 0;
            int wordMultiplier = 1;

            // Find where this word is on the board
            Point wordPosition = findWordPosition(board, word);
            if (wordPosition == null) continue;

            int startRow = wordPosition.x;
            int startCol = wordPosition.y;
            boolean isHorizontal = findWordOrientation(board, startRow, startCol, word);

            // Calculate score for this word
            int currentRow = startRow;
            int currentCol = startCol;

            for (int i = 0; i < word.length(); i++) {
                Square square = board.getSquare(currentRow, currentCol);
                Point currentPoint = new Point(currentRow, currentCol);
                boolean isNewTile = newTilePositions.contains(currentPoint);

                // Get letter value
                Tile tile = square.getTile();
                int letterValue = tile.getValue();

                // Apply letter premium for new tiles on unused premium squares
                if (isNewTile && !square.isSquareTypeUsed()) {
                    // Apply letter multiplier
                    if (square.getSquareType() == Square.SquareType.DOUBLE_LETTER) {
                        letterValue *= 2;
                    } else if (square.getSquareType() == Square.SquareType.TRIPLE_LETTER) {
                        letterValue *= 3;
                    }

                    // Collect word multipliers (only apply once per square)
                    if (!usedPremiumSquares.contains(currentPoint)) {
                        if (square.getSquareType() == Square.SquareType.DOUBLE_WORD ||
                                square.getSquareType() == Square.SquareType.CENTER) {
                            wordMultiplier *= 2;
                            usedPremiumSquares.add(currentPoint);
                        } else if (square.getSquareType() == Square.SquareType.TRIPLE_WORD) {
                            wordMultiplier *= 3;
                            usedPremiumSquares.add(currentPoint);
                        }
                    }
                }

                // Add letter value to word score
                wordScore += letterValue;

                // Move to next position
                if (isHorizontal) {
                    currentCol++;
                } else {
                    currentRow++;
                }
            }

            // Apply word multiplier
            wordScore *= wordMultiplier;

            // Add to total score
            totalScore += wordScore;

            System.out.println("Word: " + word + ", Score: " + wordScore);
        }

        // Add bingo bonus if all 7 tiles were used
        if (move.getTiles().size() == 7) {
            totalScore += ScrabbleConstants.BINGO_BONUS;
            System.out.println("Bingo bonus: " + ScrabbleConstants.BINGO_BONUS);
        }

        System.out.println("Total move score: " + totalScore);
        return totalScore;
    }

    /**
     * Finds the position of a word on the board.
     *
     * @param board The game board
     * @param word The word to find
     * @return The starting Point (row, col) of the word, or null if not found
     */
    private Point findWordPosition(Board board, String word) {
        // Check horizontal words
        for (int row = 0; row < Board.SIZE; row++) {
            for (int col = 0; col < Board.SIZE; col++) {
                String foundWord = getWordAt(board, row, col, true);
                if (foundWord.equals(word)) {
                    return new Point(row, col);
                }
            }
        }

        // Check vertical words
        for (int col = 0; col < Board.SIZE; col++) {
            for (int row = 0; row < Board.SIZE; row++) {
                String foundWord = getWordAt(board, row, col, false);
                if (foundWord.equals(word)) {
                    return new Point(row, col);
                }
            }
        }

        return null;
    }

    /**
     * Determines if a word at a given position is horizontal or vertical.
     *
     * @param board The game board
     * @param row The starting row
     * @param col The starting column
     * @param word The word to check
     * @return true if horizontal, false if vertical
     */
    private boolean findWordOrientation(Board board, int row, int col, String word) {
        // Check if the word matches horizontally
        String horizontalWord = getWordAt(board, row, col, true);

        return horizontalWord.equals(word);
    }

    private List<Square> getSquaresForWord(String word) {
        // This would need to be implemented based on how words are tracked
        // For now, return an empty list
        return new ArrayList<>();
    }

    private boolean executeExchangeMove(Move move) {
        try {
            Player player = move.getPlayer();
            List<Tile> tilesToExchange = move.getTiles();

            // Check if there are enough tiles in the bag (at least 1)
            if (tileBag.getTileCount() < 1) {
                System.out.println("Not enough tiles in bag: " + tileBag.getTileCount());
                return false;
            }

            // Log before removal
            System.out.println("Before removal - Rack size: " + player.getRack().size());
            System.out.println("Tiles to exchange: " + tilesToExchange.size());

            // Log the letters being exchanged (for debugging)
            StringBuilder exchangeLog = new StringBuilder("Exchanging tiles: ");
            for (Tile t : tilesToExchange) {
                exchangeLog.append(t.getLetter()).append(" ");
            }
            System.out.println(exchangeLog.toString());

            // Remove the tiles from the player's rack
            if (!player.getRack().removeTiles(tilesToExchange)) {
                System.out.println("Failed to remove tiles from rack");
                return false;
            }

            // Log after removal
            System.out.println("After removal - Rack size: " + player.getRack().size());

            // Draw new tiles first - same number as removed
            int numTilesToDraw = tilesToExchange.size();
            List<Tile> newTiles = tileBag.drawTiles(numTilesToDraw);

            System.out.println("Drew " + newTiles.size() + " new tiles");

            // Add the new tiles to the player's rack
            int tilesAdded = player.getRack().addTiles(newTiles);

            System.out.println("Added " + tilesAdded + " tiles to rack");

            // Return the exchanged tiles to the bag and shuffle
            tileBag.returnTiles(tilesToExchange);

            System.out.println("Returned " + tilesToExchange.size() + " tiles to bag");
            System.out.println("After exchange - Rack size: " + player.getRack().size());

            // Reset consecutive passes count
            consecutivePasses = 0;

            return true;
        } catch (Exception e) {
            System.err.println("Error in executeExchangeMove: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private boolean executePassMove(Move move) {
        consecutivePasses++;
        return true;
    }

    /**
     * Validates if a move is a valid placement.
     * Enhanced with GADDAG-based validation to properly handle word extensions.
     *
     * @param move The move to validate
     * @return true if the move is valid
     */
    public boolean isValidPlaceMove(Move move) {
        // Get necessary information from the move
        int startRow = move.getStartRow();
        int startCol = move.getStartCol();
        Move.Direction direction = move.getDirection();
        List<Tile> tiles = move.getTiles();

        // Check if the tiles list is empty
        if (tiles.isEmpty()) {
            System.out.println("Invalid move: No tiles to place");
            return false;
        }

        // Check if the starting position is valid
        if (startRow < 0 || startRow >= Board.SIZE || startCol < 0 || startCol >= Board.SIZE) {
            System.out.println("Invalid move: Starting position out of bounds");
            return false;
        }

        // If this is the first move, ensure it covers the center square
        if (board.isEmpty()) {
            boolean touchesCenter = false;

            if (direction == Move.Direction.HORIZONTAL) {
                // Check if the horizontal word covers the center square
                if (startRow == ScrabbleConstants.CENTER_SQUARE &&
                        startCol <= ScrabbleConstants.CENTER_SQUARE &&
                        startCol + tiles.size() > ScrabbleConstants.CENTER_SQUARE) {
                    touchesCenter = true;
                }
            } else { // VERTICAL
                // Check if the vertical word covers the center square
                if (startCol == ScrabbleConstants.CENTER_SQUARE &&
                        startRow <= ScrabbleConstants.CENTER_SQUARE &&
                        startRow + tiles.size() > ScrabbleConstants.CENTER_SQUARE) {
                    touchesCenter = true;
                }
            }

            if (!touchesCenter) {
                System.out.println("Invalid first move: Must cover center square");
                return false;
            }

            // First move only needs to cover center
            return true;
        }

        // For subsequent moves, check that the word connects to existing tiles
        // Create a temporary board with the move applied
        Board tempBoard = new Board();

        // Copy the current board state
        for (int r = 0; r < Board.SIZE; r++) {
            for (int c = 0; c < Board.SIZE; c++) {
                if (board.getSquare(r, c).hasTile()) {
                    tempBoard.placeTile(r, c, board.getSquare(r, c).getTile());
                }
            }
        }

        // Place tiles on temporary board and check connectivity
        int currentRow = startRow;
        int currentCol = startCol;
        boolean connectsToExisting = false;
        List<Point> newTilePositions = new ArrayList<>();

        for (Tile tile : tiles) {
            // Skip positions that already have tiles
            while (currentRow < Board.SIZE && currentCol < Board.SIZE &&
                    tempBoard.getSquare(currentRow, currentCol).hasTile()) {
                if (direction == Move.Direction.HORIZONTAL) {
                    currentCol++;
                } else {
                    currentRow++;
                }
            }

            // Check if we're still on the board
            if (currentRow >= Board.SIZE || currentCol >= Board.SIZE) {
                System.out.println("Invalid move: Placement extends beyond board");
                return false;
            }

            // Place the tile
            tempBoard.placeTile(currentRow, currentCol, tile);
            newTilePositions.add(new Point(currentRow, currentCol));

            // Check if this tile connects to existing tiles
            if (!connectsToExisting) {
                // Check adjacent tiles
                connectsToExisting = hasAdjacentTile(currentRow, currentCol);
            }

            // Move to next position
            if (direction == Move.Direction.HORIZONTAL) {
                currentCol++;
            } else {
                currentRow++;
            }
        }

        // For a tile to connect, it must either:
        // 1. Be adjacent to an existing tile, or
        // 2. Form a valid word that includes an existing tile

        // If no direct adjacency, check if formed words include existing tiles
        if (!connectsToExisting) {
            // Find all words formed by this move
            List<String> formedWords = validateWords(tempBoard, move, newTilePositions);

            // If no valid words formed, the move is invalid
            if (formedWords.isEmpty()) {
                System.out.println("Invalid move: No valid words formed");
                return false;
            }

            // Check if any formed word includes existing tiles
            for (String word : formedWords) {
                Point wordPos = findWordPosition(tempBoard, word);
                if (wordPos == null) continue;

                boolean isHorizontal = findWordOrientation(tempBoard, wordPos.x, wordPos.y, word);
                int row = wordPos.x;
                int col = wordPos.y;

                // Check each position in the word
                for (int i = 0; i < word.length(); i++) {
                    Point p = new Point(row, col);

                    // If this position was not a new tile placement but has a tile,
                    // then the word connects to an existing tile
                    if (board.getSquare(row, col).hasTile() && !newTilePositions.contains(p)) {
                        connectsToExisting = true;
                        break;
                    }

                    // Move to next position
                    if (isHorizontal) {
                        col++;
                    } else {
                        row++;
                    }
                }

                if (connectsToExisting) break;
            }
        }

        if (!connectsToExisting) {
            System.out.println("Invalid move: Does not connect to existing tiles");
            return false;
        }

        return true;
    }

    /**
     * Checks if a position has any adjacent tiles.
     *
     * @param row The row
     * @param col The column
     * @return true if any adjacent square has a tile
     */
    private boolean hasAdjacentTile(int row, int col) {
        // Check all four adjacent positions
        if (row > 0 && board.getSquare(row - 1, col).hasTile()) return true;
        if (row < Board.SIZE - 1 && board.getSquare(row + 1, col).hasTile()) return true;
        if (col > 0 && board.getSquare(row, col - 1).hasTile()) return true;
        if (col < Board.SIZE - 1 && board.getSquare(row, col + 1).hasTile()) return true;

        return false;
    }

    public boolean checkGameOver() {
        // Check if any player has no tiles and the bag is empty
        for (Player player : players) {
            if (player.isOutOfTiles() && tileBag.isEmpty()) {
                gameOver = true;
                return true;
            }
        }

        // Check if there have been too many consecutive passes
        if (consecutivePasses >= players.size() * 2) {
            gameOver = true;
            return true;
        }

        return false;
    }

    private void finaliseGameScore() {
        // Find the player who went out (if any)
        Player outPlayer = null;
        for (Player player : players) {
            if (player.isOutOfTiles()) {
                outPlayer = player;
                break;
            }
        }

        if (outPlayer != null) {
            // Winner gets other players' tile values
            int bonusPoints = 0;
            for (Player player : players) {
                if (player != outPlayer) {
                    int rackValue = player.getRackValue();
                    player.addScore(-rackValue);
                    bonusPoints += rackValue;
                }
            }
            outPlayer.addScore(bonusPoints);
        } else {
            // No player went out, subtract each player's remaining tile values
            for (Player player : players) {
                player.addScore(-player.getRackValue());
            }
        }
    }
}