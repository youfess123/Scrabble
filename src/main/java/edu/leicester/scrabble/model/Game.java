package edu.leicester.scrabble.model;

import edu.leicester.scrabble.util.ScrabbleConstants;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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

        // Fill each player's rack
        for (Player player : players) {
            fillRack(player);
        }

        // Randomize starting player
        currentPlayerIndex = (int) (Math.random() * players.size());
        gameOver = false;
        consecutivePasses = 0;
        moveHistory.clear();
    }

    public int fillRack(Player player) {
        Rack rack = player.getRack();
        int tilesToDraw = rack.getEmptySlots();

        if (tilesToDraw == 0) {
            return 0;
        }

        List<Tile> drawnTiles = tileBag.drawTiles(tilesToDraw);
        return rack.addTiles(drawnTiles);
    }

    public Player getCurrentPlayer() {
        return players.get(currentPlayerIndex);
    }

    public void nextPlayer() {
        int oldIndex = currentPlayerIndex;
        currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
        System.out.println("Player changed from index " + oldIndex + " to " + currentPlayerIndex);
        System.out.println("Current player is now: " + getCurrentPlayer().getName());
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
        System.out.println("Executing move of type: " + move.getType());

        try {
            switch (move.getType()) {
                case PLACE:
                    success = executePlaceMove(move);
                    break;

                case EXCHANGE:
                    success = executeExchangeMove(move);
                    break;

                case PASS:
                    success = executePassMove(move);
                    break;
            }
        } catch (Exception e) {
            System.err.println("Error executing move: " + e.getMessage());
            e.printStackTrace();
            return false;
        }

        if (success) {
            System.out.println("Move executed successfully");
            moveHistory.add(move);

            // Check if the game is over
            if (checkGameOver()) {
                finaliseGameScore();
                return true;
            }

            // Move to next player
            nextPlayer();
            System.out.println("Turn passed to: " + getCurrentPlayer().getName());
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

        for (Tile tile : tilesToPlace) {
            if (move.getDirection() == Move.Direction.HORIZONTAL) {
                while (board.getSquare(row, col).hasTile()) {
                    col++;
                }
                board.placeTile(row, col, tile);
                col++;
            } else {
                while (board.getSquare(row, col).hasTile()) {
                    row++;
                }
                board.placeTile(row, col, tile);
                row++;
            }

            player.getRack().removeTile(tile);
        }

        Map<String, List<Square>> wordSquaresMap = move.getMetadata("wordSquares");
        if (wordSquaresMap != null) {
            for (List<Square> squares : wordSquaresMap.values()) {
                for (Square square : squares) {
                    if (tilesToPlace.contains(square.getTile())) {
                        square.useSquareType();
                    }
                }
            }
        }

        player.addScore(move.getScore());
        consecutivePasses = 0;
        fillRack(player);

        if (player.getRack().isEmpty() && tileBag.isEmpty()) {
            player.addScore(EMPTY_RACK_BONUS);
        }

        return true;
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

    // Update this method in your Game.java class to ensure proper validation for all players

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

            return true; // First move only needs to cover center
        }

        // For subsequent moves, check that the word connects to existing tiles
        boolean connects = false;
        int currentRow = startRow;
        int currentCol = startCol;

        // Temporarily place the tiles to check connectivity
        Board tempBoard = new Board();

        // Copy the current state of the board
        for (int r = 0; r < Board.SIZE; r++) {
            for (int c = 0; c < Board.SIZE; c++) {
                if (board.getSquare(r, c).hasTile()) {
                    tempBoard.placeTile(r, c, board.getSquare(r, c).getTile());
                }
            }
        }

        // Place the tiles from the move onto the temporary board
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

            // If we've gone off the board, the move is invalid
            if (currentRow >= Board.SIZE || currentCol >= Board.SIZE) {
                System.out.println("Invalid move: Placement extends beyond board");
                return false;
            }

            // Place the tile
            tempBoard.placeTile(currentRow, currentCol, tile);

            // Check if this tile connects to an existing tile
            if (!connects) {
                // Directly on top of an existing tile (should not happen, but check anyway)
                if (board.getSquare(currentRow, currentCol).hasTile()) {
                    connects = true;
                }

                // Check adjacent tiles on the real board (not the temp board)
                connects = connects || board.hasAdjacentTile(currentRow, currentCol);
            }

            // Move to next position
            if (direction == Move.Direction.HORIZONTAL) {
                currentCol++;
            } else {
                currentRow++;
            }
        }

        if (!connects) {
            System.out.println("Invalid move: Word does not connect to existing tiles");
            return false;
        }

        // Check if all formed words are valid
        // This would typically be done in the validateWords method called from executeMove

        return true;
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
