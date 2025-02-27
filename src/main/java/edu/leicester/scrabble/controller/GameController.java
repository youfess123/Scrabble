package edu.leicester.scrabble.controller;

import edu.leicester.scrabble.model.*;
import javafx.application.Platform;
import javafx.concurrent.Task;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GameController {

    private final Game game;
    private final List<com.scrabble.model.ComputerPlayer> computerPlayers;
    private final ExecutorService executor;

    private List<Tile> selectedTiles;
    private List<Integer> selectedPositions;
    private boolean gameInProgress;

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

    public boolean placeTiles(int startRow, int startCol, Move.Direction direction) {
        if (!gameInProgress || selectedTiles.isEmpty()) {
            return false;
        }

        // Create a place move
        Move placeMove = Move.createPlaceMove(game.getCurrentPlayer(), startRow, startCol, direction);
        placeMove.addTiles(selectedTiles);

        // TODO: Add logic to calculate formed words and score

        return makeMove(placeMove);
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
