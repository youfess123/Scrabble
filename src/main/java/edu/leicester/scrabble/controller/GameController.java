package edu.leicester.scrabble.controller;

import edu.leicester.scrabble.model.*;
import edu.leicester.scrabble.util.ScrabbleConstants;
import edu.leicester.scrabble.util.WordScoreCalculator;
import javafx.application.Platform;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

public class GameController {

    private final Game game;
    private final List<edu.leicester.scrabble.model.ComputerPlayer> computerPlayers;
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
                computerPlayers.add(new edu.leicester.scrabble.model.ComputerPlayer(player, 2)); // Medium difficulty
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
            edu.leicester.scrabble.model.ComputerPlayer computerPlayer = null;
            for (edu.leicester.scrabble.model.ComputerPlayer cp : computerPlayers) {
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
            final edu.leicester.scrabble.model.ComputerPlayer finalComputerPlayer = computerPlayer;

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

    public boolean placeTileTemporarily(int rackIndex, int row, int col) {
        try {
            // Validate input
            if (row < 0 || row >= Board.SIZE || col < 0 || col >= Board.SIZE) {
                return false;
            }

            Player currentPlayer = game.getCurrentPlayer();
            Rack rack = currentPlayer.getRack();

            if (rackIndex < 0 || rackIndex >= rack.size()) {
                return false;
            }

            // Check if square is already occupied
            if (game.getBoard().getSquare(row, col).hasTile() || hasTemporaryTileAt(row, col)) {
                return false;
            }

            // Get the tile from rack
            Tile tile = rack.getTile(rackIndex);

            // Check if this tile is already placed temporarily elsewhere
            if (temporaryIndices.contains(rackIndex)) {
                return false;
            }

            // Check placement validity
            if (!isValidTemporaryPlacement(row, col)) {
                return false;
            }

            // Add tile to temporary placements
            temporaryPlacements.put(new Point(row, col), tile);
            temporaryIndices.add(rackIndex);

            // Update the board view
            updateBoard();

            return true;
        } catch (Exception e) {
            System.err.println("Error in placeTileTemporarily: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Checks if there is a temporarily placed tile at the given position.
     *
     * @param row The row
     * @param col The column
     * @return True if there is a temporary tile at the position
     */
    public boolean hasTemporaryTileAt(int row, int col) {
        return temporaryPlacements.containsKey(new Point(row, col));
    }

    /**
     * Gets the temporarily placed tile at the given position.
     *
     * @param row The row
     * @param col The column
     * @return The temporary tile at the position, or null if none
     */
    public Tile getTemporaryTileAt(int row, int col) {
        return temporaryPlacements.get(new Point(row, col));
    }

    /**
     * Checks if a rack tile is currently placed on the board temporarily.
     *
     * @param rackIndex The index of the tile in the rack
     * @return True if the tile is placed on the board
     */
    public boolean isTileTemporarilyPlaced(int rackIndex) {
        return temporaryIndices.contains(rackIndex);
    }

    public boolean isValidTemporaryPlacement(int row, int col) {
        Board board = game.getBoard();

        // If board is empty and no temporary placements yet, require center square
        if (board.isEmpty() && temporaryPlacements.isEmpty()) {
            return row == ScrabbleConstants.CENTER_SQUARE && col == ScrabbleConstants.CENTER_SQUARE;
        }

        // If there are already temporary placements, check if this placement maintains a straight line
        if (!temporaryPlacements.isEmpty()) {
            Move.Direction direction = determineDirection();

            // If we can't determine a direction yet (only one tile placed), any adjacent square is valid
            if (direction == null) {
                // Check if this placement would be adjacent to existing temporary placement
                for (Point p : temporaryPlacements.keySet()) {
                    if ((p.x == row && Math.abs(p.y - col) == 1) || (p.y == col && Math.abs(p.x - row) == 1)) {
                        return true;
                    }
                }
                return false;
            }

            // If direction is determined, check if new placement maintains that direction
            if (direction == Move.Direction.HORIZONTAL) {
                // All tiles must be in same row, and columns must be consecutive
                int existingRow = -1;
                for (Point p : temporaryPlacements.keySet()) {
                    if (existingRow == -1) {
                        existingRow = p.x;
                    } else if (p.x != existingRow) {
                        return false; // Not all in same row
                    }
                }

                // Check if this placement is in the same row
                if (row != existingRow) {
                    return false;
                }

                // Check if this column is valid (must be consecutive with existing placements or board tiles)
                int minCol = Integer.MAX_VALUE;
                int maxCol = Integer.MIN_VALUE;
                for (Point p : temporaryPlacements.keySet()) {
                    minCol = Math.min(minCol, p.y);
                    maxCol = Math.max(maxCol, p.y);
                }

                // Expand range to include board tiles in the same row
                for (int c = 0; c < Board.SIZE; c++) {
                    if (board.getSquare(row, c).hasTile()) {
                        minCol = Math.min(minCol, c);
                        maxCol = Math.max(maxCol, c);
                    }
                }

                // Check if col is within or adjacent to the range
                return col >= minCol - 1 && col <= maxCol + 1;
            } else {
                // All tiles must be in same column, and rows must be consecutive
                int existingCol = -1;
                for (Point p : temporaryPlacements.keySet()) {
                    if (existingCol == -1) {
                        existingCol = p.y;
                    } else if (p.y != existingCol) {
                        return false; // Not all in same column
                    }
                }

                // Check if this placement is in the same column
                if (col != existingCol) {
                    return false;
                }

                // Check if this row is valid (must be consecutive with existing placements or board tiles)
                int minRow = Integer.MAX_VALUE;
                int maxRow = Integer.MIN_VALUE;
                for (Point p : temporaryPlacements.keySet()) {
                    minRow = Math.min(minRow, p.x);
                    maxRow = Math.max(maxRow, p.x);
                }

                // Expand range to include board tiles in the same column
                for (int r = 0; r < Board.SIZE; r++) {
                    if (board.getSquare(r, col).hasTile()) {
                        minRow = Math.min(minRow, r);
                        maxRow = Math.max(maxRow, r);
                    }
                }

                // Check if row is within or adjacent to the range
                return row >= minRow - 1 && row <= maxRow + 1;
            }
        }

        // Otherwise, require adjacency to existing tiles on the board
        return board.hasAdjacentTile(row, col);
    }

    public Move.Direction determineDirection() {
        // If there are less than 2 temporary tiles, we can't determine direction
        if (temporaryPlacements.size() < 2) {
            return null;
        }

        // Check if all temporary placements are in the same row
        boolean sameRow = true;
        int firstRow = -1;

        // Check if all temporary placements are in the same column
        boolean sameColumn = true;
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
                    sameColumn = false;
                }
            }
        }

        // If all in same row, it's horizontal
        if (sameRow) {
            return Move.Direction.HORIZONTAL;
        }

        // If all in same column, it's vertical
        if (sameColumn) {
            return Move.Direction.VERTICAL;
        }

        // If neither, the placement is invalid
        return null;
    }

    public boolean commitPlacement() {
        if (temporaryPlacements.isEmpty()) {
            return false;
        }

        // Determine the direction of the placement
        Move.Direction direction = determineDirection();
        if (direction == null) {
            // If only one tile placed, default to horizontal
            if (temporaryPlacements.size() == 1) {
                direction = Move.Direction.HORIZONTAL;
            } else {
                return false; // Invalid placement
            }
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

        // Calculate the words formed and their scores
        List<String> formedWords = calculateFormedWords(placeMove);
        if (formedWords.isEmpty()) {
            return false; // No valid words formed
        }

        // Set the formed words
        placeMove.setFormedWords(formedWords);

        // Calculate score
        int score = calculateMoveScore(placeMove);
        placeMove.setScore(score);

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

    private List<String> calculateFormedWords(Move move) {
        List<String> formedWords = new ArrayList<>();
        Board board = game.getBoard();
        int row = move.getStartRow();
        int col = move.getStartCol();

        // Create a temporary board to place the tiles
        Board tempBoard = new Board();

        // Copy existing tiles from the game board
        for (int r = 0; r < Board.SIZE; r++) {
            for (int c = 0; c < Board.SIZE; c++) {
                Square square = board.getSquare(r, c);
                if (square.hasTile()) {
                    tempBoard.placeTile(r, c, square.getTile());
                }
            }
        }

        // Place the new tiles
        int currentRow = row;
        int currentCol = col;

        for (Tile tile : move.getTiles()) {
            // Skip squares that already have tiles
            while (tempBoard.getSquare(currentRow, currentCol).hasTile()) {
                if (move.getDirection() == Move.Direction.HORIZONTAL) {
                    currentCol++;
                } else {
                    currentRow++;
                }

                // Check bounds
                if (currentRow >= Board.SIZE || currentCol >= Board.SIZE) {
                    break;
                }
            }

            // Place the tile
            if (currentRow < Board.SIZE && currentCol < Board.SIZE) {
                tempBoard.placeTile(currentRow, currentCol, tile);

                // Move to next position
                if (move.getDirection() == Move.Direction.HORIZONTAL) {
                    currentCol++;
                } else {
                    currentRow++;
                }
            }
        }

        // Find the main word
        List<Square> mainWord;
        if (move.getDirection() == Move.Direction.HORIZONTAL) {
            mainWord = tempBoard.getHorizontalWord(row, col);
        } else {
            mainWord = tempBoard.getVerticalWord(row, col);
        }

        if (!mainWord.isEmpty()) {
            formedWords.add(Board.getWordString(mainWord));
        }

        // Find any crossing words
        currentRow = row;
        currentCol = col;

        for (Tile tile : move.getTiles()) {
            // Skip squares that already have tiles on the original board
            while (board.getSquare(currentRow, currentCol).hasTile()) {
                if (move.getDirection() == Move.Direction.HORIZONTAL) {
                    currentCol++;
                } else {
                    currentRow++;
                }

                // Check bounds
                if (currentRow >= Board.SIZE || currentCol >= Board.SIZE) {
                    break;
                }
            }

            // Check for crossing word
            if (currentRow < Board.SIZE && currentCol < Board.SIZE) {
                List<Square> crossingWord;

                if (move.getDirection() == Move.Direction.HORIZONTAL) {
                    crossingWord = tempBoard.getVerticalWord(currentRow, currentCol);
                } else {
                    crossingWord = tempBoard.getHorizontalWord(currentRow, currentCol);
                }

                if (crossingWord.size() >= 2) { // Must be at least 2 letters
                    formedWords.add(Board.getWordString(crossingWord));
                }

                // Move to next position
                if (move.getDirection() == Move.Direction.HORIZONTAL) {
                    currentCol++;
                } else {
                    currentRow++;
                }
            }
        }

        return formedWords;
    }

    private int calculateMoveScore(Move move) {
        // This is a simplified score calculation
        // In a real implementation, you would need to account for premium squares

        int score = 0;
        for (String word : move.getFormedWords()) {
            // Basic scoring: 1 point per letter
            score += word.length();
        }

        // Bonus for using all tiles (Bingo)
        if (move.getTiles().size() == Rack.RACK_SIZE) {
            score += ScrabbleConstants.BINGO_BONUS;
        }

        return score;
    }

    private boolean validateWords(Move move) {
        Dictionary dictionary = game.getDictionary();

        for (String word : move.getFormedWords()) {
            if (!dictionary.isValidWord(word)) {
                return false;
            }
        }

        return true;
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

    public void cancelPlacements() {
        // Clear temporary placements
        temporaryPlacements.clear();
        temporaryIndices.clear();

        // Update board view
        updateBoard();
    }

    public void shutdown() {
        executor.shutdown();
    }

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