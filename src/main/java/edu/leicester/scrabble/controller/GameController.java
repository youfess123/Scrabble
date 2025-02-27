package edu.leicester.scrabble.controller;

import edu.leicester.scrabble.model.*;
import javafx.application.Platform;
import javafx.concurrent.Task;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GameController {

    private final Game game;
    private final List<com.scrabble.model.ComputerPlayer> computerPlayers;
    private final ExecutorService executor;

    private List<Tile> selectedTiles;
    private List<Integer> selectedPositions;
    private boolean gameInProgress;

    private Map<Point, Tile> temporaryPlacements = new HashMap<>();
    private List<Integer> temporaryIndices = new ArrayList<>();

    private Runnable boardUpdateListener;
    private Runnable rackUpdateListener;
    private Runnable playerUpdateListener;
    private Runnable gameOverListener;

    public GameController(Game game) {
        this.game = game;
        this.computerPlayers = new ArrayList<>();
        this.executor = Executors.newSingleThreadExecutor();
        this.selectedTiles = new ArrayList<>();
        this.selectedPositions = new ArrayList<>();
        this.gameInProgress = false;

        // Create computer players for any AI players
        for (Player player : game.getPlayers()) {
            if (player.isComputer()) {
                computerPlayers.add(new com.scrabble.model.ComputerPlayer(player, 2)); // Medium difficulty
            }
        }
    }

    public void startGame() {
        game.start();
        gameInProgress = true;

        // Notify listeners
        updateBoard();
        updateRack();
        updateCurrentPlayer();

        // If the first player is a computer, make its move
        makeComputerMoveIfNeeded();
    }

    public boolean makeMove(Move move) {
        if (!gameInProgress) {
            return false;
        }

        boolean success = game.executeMove(move);

        if (success) {
            // Clear selections
            selectedTiles.clear();
            selectedPositions.clear();

            // Notify listeners
            updateBoard();
            updateRack();
            updateCurrentPlayer();

            // Check if game is over
            if (game.isGameOver()) {
                gameInProgress = false;
                if (gameOverListener != null) {
                    gameOverListener.run();
                }
                return true;
            }

            // Make computer move if needed
            makeComputerMoveIfNeeded();
        }

        return success;
    }

    private void makeComputerMoveIfNeeded() {
        Player currentPlayer = game.getCurrentPlayer();

        if (currentPlayer.isComputer()) {
            // Find the computer player
            com.scrabble.model.ComputerPlayer computerPlayer = null;
            for (com.scrabble.model.ComputerPlayer cp : computerPlayers) {
                if (cp.getPlayer() == currentPlayer) {
                    computerPlayer = cp;
                    break;
                }
            }

            if (computerPlayer != null) {
                // Run computer move in background
                final com.scrabble.model.ComputerPlayer finalComputerPlayer = computerPlayer; // Create a final reference
                Task<Move> task = new Task<Move>() {
                    @Override
                    protected Move call() throws Exception {
                        // Add a small delay to make it feel more natural
                        Thread.sleep(1000);
                        return finalComputerPlayer.generateMove(game);
                    }
                };

                task.setOnSucceeded(event -> {
                    Move move = task.getValue();
                    if (move != null) {
                        makeMove(move);
                    }
                });

                task.setOnSucceeded(event -> {
                    Move move = task.getValue();
                    if (move != null) {
                        makeMove(move);
                    }
                });

                executor.submit(task);
            }
        }
    }

    public boolean passTurn() {
        if (!gameInProgress) {
            return false;
        }

        Move passMove = Move.createPassMove(game.getCurrentPlayer());
        return makeMove(passMove);
    }

    public boolean exchangeTiles() {
        if (!gameInProgress || selectedTiles.isEmpty()) {
            return false;
        }

        // Can only exchange if there are at least 7 tiles in the bag
        if (game.getTileBag().getTileCount() < 7) {
            return false;
        }

        Move exchangeMove = Move.createExchangeMove(game.getCurrentPlayer(), new ArrayList<>(selectedTiles));
        return makeMove(exchangeMove);
    }

    public boolean selectTileFromRack(int index) {
        if (!gameInProgress) {
            return false;
        }

        Player currentPlayer = game.getCurrentPlayer();
        Rack rack = currentPlayer.getRack();

        if (index < 0 || index >= rack.size()) {
            return false;
        }

        Tile tile = rack.getTile(index);

        // Toggle selection
        if (selectedTiles.contains(tile)) {
            selectedTiles.remove(tile);
            selectedPositions.remove(Integer.valueOf(index));
        } else {
            selectedTiles.add(tile);
            selectedPositions.add(index);
        }

        // Notify rack update
        updateRack();

        return true;
    }

    public boolean isTileSelected(int index) {
        return selectedPositions.contains(index);
    }

    // In GameController.java, modify the placeTiles method:

    public boolean placeTiles(int startRow, int startCol, Move.Direction direction) {
        if (!gameInProgress || selectedTiles.isEmpty()) {
            return false;
        }

        // Create a place move
        Move placeMove = Move.createPlaceMove(game.getCurrentPlayer(), startRow, startCol, direction);

        // Add the selected tiles to the move
        placeMove.addTiles(new ArrayList<>(selectedTiles));

        // Make the move
        boolean success = makeMove(placeMove);

        return success;
    }

    public boolean placeTileTemporarily(int tileIndex, int row, int col) {
        if (!gameInProgress) {
            return false;
        }

        Player currentPlayer = game.getCurrentPlayer();
        Rack rack = currentPlayer.getRack();

        if (tileIndex < 0 || tileIndex >= rack.size()) {
            return false;
        }

        // Check if this square already has a temporary tile
        Point position = new Point(row, col);
        if (temporaryPlacements.containsKey(position)) {
            return false;
        }

        // Get the tile from the rack
        Tile tile = rack.getTile(tileIndex);

        // Store the temporary placement
        temporaryPlacements.put(position, tile);
        temporaryIndices.add(tileIndex);

        // Update the board view (but don't modify the game state yet)
        updateBoard();

        return true;
    }

    public boolean commitPlacement() {
        if (temporaryPlacements.isEmpty()) {
            return false;
        }

        // Determine the direction of the placement
        Move.Direction direction = determineDirection();
        if (direction == null) {
            return false; // Invalid placement
        }

        // Find the starting position
        int startRow = Integer.MAX_VALUE;
        int startCol = Integer.MAX_VALUE;
        for (Point p : temporaryPlacements.keySet()) {
            startRow = Math.min(startRow, p.x);
            startCol = Math.min(startCol, p.y);
        }

        // Create a place move
        Move placeMove = Move.createPlaceMove(game.getCurrentPlayer(), startRow, startCol, direction);

        // Add the tiles to the move
        for (Tile tile : temporaryPlacements.values()) {
            placeMove.addTile(tile);
        }

        // Validate the move
        if (!validateWords(placeMove)) {
            return false; // Invalid words formed
        }

        // Apply the move
        boolean success = makeMove(placeMove);

        if (success) {
            // Clear temporary state
            temporaryPlacements.clear();
            temporaryIndices.clear();
        }

        return success;
    }

    public void cancelPlacements() {
        temporaryPlacements.clear();
        temporaryIndices.clear();
        updateBoard();
    }

    /**
     * Determines the direction of the current placement
     */
    private Move.Direction determineDirection() {
        // Check if all tiles are in the same row
        boolean sameRow = true;
        int firstRow = -1;

        // Check if all tiles are in the same column
        boolean sameCol = true;
        int firstCol = -1;

        for (Point p : temporaryPlacements.keySet()) {
            if (firstRow == -1) {
                firstRow = p.x;
                firstCol = p.y;
            } else {
                if (p.x != firstRow) {
                    sameRow = false;
                }
                if (p.y != firstCol) {
                    sameCol = false;
                }
            }
        }

        if (sameRow && !sameCol) {
            return Move.Direction.HORIZONTAL;
        } else if (!sameRow && sameCol) {
            return Move.Direction.VERTICAL;
        } else {
            // Either all tiles are at the same point or they're scattered
            return null;
        }
    }

    private boolean validateWords(Move move) {
        // This should use the GADDAG to validate all words formed
        // For now, we're just returning true as a placeholder
        return true;
    }

    private void calculateWords(Move move) {
        // This would calculate the words formed by the move and set them on the move object
        // along with the score. For now, we'll just set a placeholder
        move.setScore(10); // Placeholder score
        move.addFormedWord("WORD"); // Placeholder word
    }

    public Board getBoard() {
        return game.getBoard();
    }

    public Player getCurrentPlayer() {
        return game.getCurrentPlayer();
    }

    public List<Player> getPlayers() {
        return game.getPlayers();
    }

    public int getRemainingTileCount() {
        return game.getTileBag().getTileCount();
    }

    public int getPlayerScore(Player player) {
        return player.getScore();
    }

    public void setBoardUpdateListener(Runnable listener) {
        this.boardUpdateListener = listener;
    }

    public void setRackUpdateListener(Runnable listener) {
        this.rackUpdateListener = listener;
    }

    public void setPlayerUpdateListener(Runnable listener) {
        this.playerUpdateListener = listener;
    }

    public void setGameOverListener(Runnable listener) {
        this.gameOverListener = listener;
    }

    private void updateBoard() {
        if (boardUpdateListener != null) {
            Platform.runLater(boardUpdateListener);
        }
    }

    private void updateRack() {
        if (rackUpdateListener != null) {
            Platform.runLater(rackUpdateListener);
        }
    }

    private void updateCurrentPlayer() {
        if (playerUpdateListener != null) {
            Platform.runLater(playerUpdateListener);
        }
    }

    public void shutdown() {
        executor.shutdown();
    }
}
