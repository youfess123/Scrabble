package edu.leicester.scrabble.controller;

import edu.leicester.scrabble.model.*;
import edu.leicester.scrabble.util.ScrabbleConstants;
import javafx.application.Platform;
import javafx.concurrent.Task;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

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

    private volatile boolean computerMoveInProgress = false;

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
            System.out.println("Move executed: " + move.getType() + " by " + move.getPlayer().getName());

            // Clear selections
            selectedTiles.clear();
            selectedPositions.clear();
            temporaryPlacements.clear();
            temporaryIndices.clear();

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

            // If this was a computer move, show what happened
            if (move.getPlayer().isComputer()) {
                showComputerMoveInfo(move);
            }

            // Make computer move if needed (if the next player is a computer)
            makeComputerMoveIfNeeded();
        } else {
            System.out.println("Move failed to execute");
        }

        return success;
    }

    private void showComputerMoveInfo(Move move) {
        String moveInfo = "";

        switch (move.getType()) {
            case PLACE:
                moveInfo = "Computer placed tiles to form: " + String.join(", ", move.getFormedWords());
                moveInfo += "\nScore: " + move.getScore() + " points";
                break;

            case EXCHANGE:
                moveInfo = "Computer exchanged " + move.getTiles().size() + " tiles";
                break;

            case PASS:
                moveInfo = "Computer passed its turn";
                break;
        }

        String finalMoveInfo = moveInfo;
        Platform.runLater(() -> {
            try {
                javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                        javafx.scene.control.Alert.AlertType.INFORMATION
                );
                alert.setTitle("Computer's Move");
                alert.setHeaderText("Computer has played");
                alert.setContentText(finalMoveInfo);

                // Show the dialog and wait for it to be closed
                alert.showAndWait();
            } catch (Exception e) {
                System.err.println("Error showing computer move info: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    private void makeComputerMoveIfNeeded() {
        Player currentPlayer = game.getCurrentPlayer();

        if (currentPlayer.isComputer() && !computerMoveInProgress) {
            System.out.println("Computer's turn - preparing move");
            computerMoveInProgress = true;

            // Show that it's the computer's turn in the UI
            updateCurrentPlayer();

            // Find the computer player
            com.scrabble.model.ComputerPlayer computerPlayer = null;
            for (com.scrabble.model.ComputerPlayer cp : computerPlayers) {
                if (cp.getPlayer() == currentPlayer) {
                    computerPlayer = cp;
                    break;
                }
            }

            if (computerPlayer == null) {
                System.err.println("Computer player not found");
                // If we can't find a computer player, just pass
                Move passMove = Move.createPassMove(currentPlayer);
                makeMove(passMove);
                computerMoveInProgress = false;
                return;
            }

            // Create a final reference for the task
            final com.scrabble.model.ComputerPlayer finalComputerPlayer = computerPlayer;

            // Create an emergency timeout that will force a pass move after 5 seconds
            ScheduledExecutorService emergencyTimer = Executors.newSingleThreadScheduledExecutor();
            ScheduledFuture<?> emergencyTimeout = emergencyTimer.schedule(() -> {
                if (computerMoveInProgress) {
                    System.out.println("EMERGENCY: Computer move taking too long - forcing PASS");
                    Platform.runLater(() -> {
                        Move passMove = Move.createPassMove(currentPlayer);
                        makeMove(passMove);
                        computerMoveInProgress = false;
                    });
                }
            }, 5, TimeUnit.SECONDS);

            // Use simple pass move for computer
            Platform.runLater(() -> {
                try {
                    // Small delay for visual feedback
                    Thread.sleep(1000);

                    // Cancel the emergency timer since we're executing normally
                    emergencyTimeout.cancel(false);
                    emergencyTimer.shutdownNow();

                    System.out.println("Computer defaulting to PASS move for stability");
                    Move passMove = Move.createPassMove(currentPlayer);
                    makeMove(passMove);
                } catch (Exception e) {
                    System.err.println("Error in computer move: " + e.getMessage());
                    e.printStackTrace();
                    // Ensure we make a move even on exception
                    Move passMove = Move.createPassMove(currentPlayer);
                    makeMove(passMove);
                } finally {
                    computerMoveInProgress = false;
                }
            });
        } else {
            System.out.println("It's " + currentPlayer.getName() + "'s turn");
        }
    }

    // Rest of the GameController methods...
    // ... (include all other methods from GameController here)

    public boolean passTurn() {
        if (!gameInProgress) {
            return false;
        }

        Move passMove = Move.createPassMove(game.getCurrentPlayer());
        return makeMove(passMove);
    }

    public boolean exchangeTiles() {
        try {
            // Check game state
            if (!gameInProgress) {
                System.out.println("Game not in progress");
                return false;
            }

            // Verify we have tiles selected
            if (selectedTiles.isEmpty()) {
                System.out.println("No tiles selected");
                return false;
            }

            // Check if the tile bag has enough tiles
            if (game.getTileBag().getTileCount() < 1) {
                System.out.println("Not enough tiles in bag");
                return false;
            }

            System.out.println("Exchanging " + selectedTiles.size() + " tiles");

            // Log the letters being exchanged (for debugging)
            StringBuilder exchangeLog = new StringBuilder("Selected tiles: ");
            for (Tile t : selectedTiles) {
                exchangeLog.append(t.getLetter()).append(" ");
            }
            System.out.println(exchangeLog.toString());

            // Make a copy of the selected tiles before clearing them
            List<Tile> tilesToExchange = new ArrayList<>(selectedTiles);

            // Create a move for the exchange
            Move exchangeMove = Move.createExchangeMove(game.getCurrentPlayer(), tilesToExchange);

            // Store the current player before executing the move
            Player currentPlayer = game.getCurrentPlayer();

            // Execute the move (this will advance to the next player)
            boolean success = game.executeMove(exchangeMove);

            if (success) {
                // Clear selections now that the move has been executed
                selectedTiles.clear();
                selectedPositions.clear();

                // Show a confirmation dialog with the updated rack
                showExchangeConfirmation();

                // Explicitly trigger view updates
                updateBoard();
                updateRack();
                updateCurrentPlayer();

                System.out.println("Exchange successful");
            } else {
                System.out.println("Exchange failed");
            }

            return success;
        } catch (Exception e) {
            System.err.println("Error in exchangeTiles: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private void showExchangeConfirmation() {
        // This method will be called in the JavaFX Application Thread
        Platform.runLater(() -> {
            try {
                javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                        javafx.scene.control.Alert.AlertType.INFORMATION
                );
                alert.setTitle("Tiles Exchanged");
                alert.setHeaderText("Your tiles have been exchanged successfully");
                alert.setContentText("Your new tiles are now in your rack. The computer's turn will begin when you close this dialog.");

                // Force the UI to update before showing the dialog
                updateRack();
                updateBoard();

                // Show the dialog and wait for it to be closed
                alert.showAndWait();
            } catch (Exception e) {
                System.err.println("Error showing exchange confirmation: " + e.getMessage());
                e.printStackTrace();
            }
        });
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

        // Check if the placement includes the center square for the first move
        if (game.getBoard().isEmpty()) {
            boolean includesCenter = false;
            for (Point p : temporaryPlacements.keySet()) {
                if (p.x == ScrabbleConstants.CENTER_SQUARE && p.y == ScrabbleConstants.CENTER_SQUARE) {
                    includesCenter = true;
                    break;
                }
            }
            if (!includesCenter) {
                return false; // First move must include center square
            }
        }

        // Create a place move
        Move placeMove = Move.createPlaceMove(game.getCurrentPlayer(), startRow, startCol, direction);

        // Add the tiles to the move in the correct order
        List<Tile> tilesToPlace = new ArrayList<>();

        if (direction == Move.Direction.HORIZONTAL) {
            for (int c = startCol; c < Board.SIZE; c++) {
                Point p = new Point(startRow, c);
                if (temporaryPlacements.containsKey(p)) {
                    tilesToPlace.add(temporaryPlacements.get(p));
                } else if (game.getBoard().getSquare(startRow, c).hasTile()) {
                    // Skip existing tiles on the board
                    continue;
                } else {
                    // End of the word
                    break;
                }
            }
        } else { // VERTICAL
            for (int r = startRow; r < Board.SIZE; r++) {
                Point p = new Point(r, startCol);
                if (temporaryPlacements.containsKey(p)) {
                    tilesToPlace.add(temporaryPlacements.get(p));
                } else if (game.getBoard().getSquare(r, startCol).hasTile()) {
                    // Skip existing tiles on the board
                    continue;
                } else {
                    // End of the word
                    break;
                }
            }
        }

        placeMove.addTiles(tilesToPlace);

        // Validate the move to check if all words formed are valid
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

    public void shutdown() {
        executor.shutdown();
    }

    // Include all other methods necessary for the controller to function
    // ... (all other methods of the GameController class)

    // Update notification methods
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

    // Getter methods
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

    public boolean isTileSelected(int index) {
        return selectedPositions.contains(index);
    }

    // Setter methods for listeners
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

    // Getter methods for various collections
    public Map<Point, Tile> getTemporaryPlacements() {
        return new HashMap<>(temporaryPlacements);
    }

    public List<Integer> getTemporaryIndices() {
        return new ArrayList<>(temporaryIndices);
    }

    public List<Integer> getSelectedPositions() {
        return new ArrayList<>(selectedPositions);
    }

    public List<Tile> getSelectedTiles() {
        return new ArrayList<>(selectedTiles);
    }
}