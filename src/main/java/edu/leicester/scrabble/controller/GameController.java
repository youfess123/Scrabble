package edu.leicester.scrabble.controller;

import edu.leicester.scrabble.model.*;
import edu.leicester.scrabble.util.ScrabbleConstants;
import edu.leicester.scrabble.util.WordScoreCalculator;
import javafx.application.Platform;
import javafx.scene.control.Alert;

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

            // Important: Clear temporary placements AFTER the move is executed successfully
            temporaryPlacements.clear();
            temporaryIndices.clear();

            // Force a complete board update
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

    // This code segment should replace the makeComputerMoveIfNeeded method in GameController.java

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

            // Execute the computer's move generation on a separate thread
            executor.submit(() -> {
                try {
                    // Generate a move using the enhanced AI logic
                    Move computerMove = finalComputerPlayer.generateMove(game);

                    // Apply a small delay for visual feedback
                    Thread.sleep(1000);

                    // Cancel the emergency timer since we're executing normally
                    emergencyTimeout.cancel(false);
                    emergencyTimer.shutdownNow();

                    // Execute the move on the JavaFX application thread
                    Platform.runLater(() -> {
                        try {
                            makeMove(computerMove);
                        } catch (Exception e) {
                            System.err.println("Error executing computer move: " + e.getMessage());
                            e.printStackTrace();
                            // Ensure we make a move even on exception
                            Move passMove = Move.createPassMove(currentPlayer);
                            makeMove(passMove);
                        } finally {
                            computerMoveInProgress = false;
                        }
                    });
                } catch (Exception e) {
                    System.err.println("Error in computer move: " + e.getMessage());
                    e.printStackTrace();

                    // Make sure we handle any exceptions and still make a move
                    Platform.runLater(() -> {
                        try {
                            // Cancel the emergency timer
                            emergencyTimeout.cancel(false);
                            emergencyTimer.shutdownNow();

                            // Default to a pass move on error
                            Move passMove = Move.createPassMove(currentPlayer);
                            makeMove(passMove);
                        } finally {
                            computerMoveInProgress = false;
                        }
                    });
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
                System.out.println("[Exchange] Game not in progress");
                return false;
            }

            // Verify we have tiles selected
            if (selectedTiles.isEmpty()) {
                System.out.println("[Exchange] No tiles selected");
                showError("No tiles selected. Please select tiles from your rack to exchange.");
                return false;
            }

            // Check if the tile bag has enough tiles
            if (game.getTileBag().getTileCount() < 1) {
                System.out.println("[Exchange] Not enough tiles in bag");
                showError("Not enough tiles in the bag for exchange.");
                return false;
            }

            // Check if there are any temporary placements
            if (!temporaryPlacements.isEmpty()) {
                System.out.println("[Exchange] Temporary placements exist");
                showError("Please cancel your current placement before exchanging tiles.");
                return false;
            }

            System.out.println("[Exchange] Exchanging " + selectedTiles.size() + " tiles");

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
            boolean currentPlayerIsHuman = !currentPlayer.isComputer();

            // Clear selections now to prevent issues
            selectedTiles.clear();
            selectedPositions.clear();

            // Execute the exchange move (this will advance to the next player)
            boolean success = game.executeMove(exchangeMove);

            if (success) {
                System.out.println("[Exchange] Exchange successful");

                // Update the UI first
                updateBoard();
                updateRack();
                updateCurrentPlayer();

                // Show confirmation and handle the computer's turn
                if (currentPlayerIsHuman) {
                    showExchangeConfirmation();
                }
            } else {
                System.out.println("[Exchange] Exchange failed");
                showError("Failed to exchange tiles. Please try again.");
            }

            return success;
        } catch (Exception e) {
            System.err.println("[Exchange] Error in exchangeTiles: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private void showExchangeConfirmation() {
        // This must be called on the JavaFX Application Thread
        Platform.runLater(() -> {
            try {
                // Create and display exchange confirmation
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Tiles Exchanged");
                alert.setHeaderText("Your tiles have been exchanged successfully");
                alert.setContentText("Your new tiles are now in your rack. The computer's turn will begin when you close this dialog.");

                // Update the UI again before showing the dialog
                updateRack();
                updateBoard();
                updateCurrentPlayer();

                // Show dialog and wait for it to be closed
                alert.showAndWait();

                System.out.println("[Exchange] Dialog closed, checking if computer's turn");

                // After dialog is closed, check if it's the computer's turn
                Player currentPlayer = game.getCurrentPlayer();

                if (currentPlayer.isComputer()) {
                    System.out.println("[Exchange] Current player is computer: " + currentPlayer.getName());

                    // Ensure UI is updated to show it's computer's turn
                    updateCurrentPlayer();

                    // This ensures the computer move gets triggered with a slight delay
                    // to allow the UI to update first
                    new Thread(() -> {
                        try {
                            Thread.sleep(300);
                            Platform.runLater(() -> {
                                System.out.println("[Exchange] Explicitly triggering computer move");
                                computerMoveInProgress = false; // Reset flag to ensure it can start
                                makeComputerMoveIfNeeded();
                            });
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }).start();
                } else {
                    System.out.println("[Exchange] Current player is NOT computer: " + currentPlayer.getName());
                }
            } catch (Exception e) {
                System.err.println("[Exchange] Error in exchange confirmation: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }


    public void showError(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
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
            updateRack();  // Also update rack to show tile as "used"

            return true;
        } catch (Exception e) {
            System.err.println("Error in placeTileTemporarily: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public boolean hasTemporaryTileAt(int row, int col) {
        return temporaryPlacements.containsKey(new Point(row, col));
    }

    public Tile getTemporaryTileAt(int row, int col) {
        return temporaryPlacements.get(new Point(row, col));
    }

    public boolean isTileTemporarilyPlaced(int rackIndex) {
        return temporaryIndices.contains(rackIndex);
    }

    public boolean isValidTemporaryPlacement(int row, int col) {
        Board board = game.getBoard();

        // Check if the position is already occupied (board tile or temporary placement)
        if (board.getSquare(row, col).hasTile() || hasTemporaryTileAt(row, col)) {
            return false;
        }

        // If board is empty and no temporary placements yet, require center square
        if (board.isEmpty() && temporaryPlacements.isEmpty()) {
            return row == ScrabbleConstants.CENTER_SQUARE && col == ScrabbleConstants.CENTER_SQUARE;
        }

        // If there are already temporary placements, check if this new placement maintains a straight line
        if (!temporaryPlacements.isEmpty()) {
            // Get all current temporary placement points
            List<Point> placementPoints = new ArrayList<>(temporaryPlacements.keySet());

            // If only one tile has been placed, we need to determine direction
            if (placementPoints.size() == 1) {
                Point existingPoint = placementPoints.get(0);

                // Check if the new placement is in the same row or column as the existing one
                return (row == existingPoint.x || col == existingPoint.y);
            }

            // More than one tile - determine direction
            Move.Direction direction = determineDirection();

            // If we can't determine a direction (tiles not in a line), placement is invalid
            if (direction == null) {
                return false;
            }

            // Check if the new placement follows the direction
            if (direction == Move.Direction.HORIZONTAL) {
                // All placements must be in the same row
                for (Point p : placementPoints) {
                    if (p.x != row) {
                        return false;
                    }
                }

                // Get min and max columns of temporary placements
                int minCol = Integer.MAX_VALUE;
                int maxCol = Integer.MIN_VALUE;
                for (Point p : placementPoints) {
                    minCol = Math.min(minCol, p.y);
                    maxCol = Math.max(maxCol, p.y);
                }

                // Also consider board tiles in the same row
                for (int c = 0; c < Board.SIZE; c++) {
                    if (board.getSquare(row, c).hasTile()) {
                        minCol = Math.min(minCol, c);
                        maxCol = Math.max(maxCol, c);
                    }
                }

                // New placement must be in same row and in valid column range
                return (col >= minCol - 1 && col <= maxCol + 1);
            } else {
                // All placements must be in the same column
                for (Point p : placementPoints) {
                    if (p.y != col) {
                        return false;
                    }
                }

                // Get min and max rows of temporary placements
                int minRow = Integer.MAX_VALUE;
                int maxRow = Integer.MIN_VALUE;
                for (Point p : placementPoints) {
                    minRow = Math.min(minRow, p.x);
                    maxRow = Math.max(maxRow, p.x);
                }

                // Also consider board tiles in the same column
                for (int r = 0; r < Board.SIZE; r++) {
                    if (board.getSquare(r, col).hasTile()) {
                        minRow = Math.min(minRow, r);
                        maxRow = Math.max(maxRow, r);
                    }
                }

                // New placement must be in same column and in valid row range
                return (row >= minRow - 1 && row <= maxRow + 1);
            }
        }

        // For the first tile placement in a turn, must be adjacent to at least one existing tile
        return board.hasAdjacentTile(row, col);
    }

    public Move.Direction determineDirection() {
        // If there are less than 2 temporary tiles, we can't determine direction
        if (temporaryPlacements.size() < 2) {
            return null;
        }

        // Get all points where tiles have been temporarily placed
        List<Point> points = new ArrayList<>(temporaryPlacements.keySet());

        // Check if all tiles are in the same row
        boolean sameRow = true;
        int firstRow = points.get(0).x;

        // Check if all tiles are in the same column
        boolean sameColumn = true;
        int firstCol = points.get(0).y;

        // Check each point
        for (Point p : points) {
            if (p.x != firstRow) {
                sameRow = false;
            }
            if (p.y != firstCol) {
                sameColumn = false;
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

    public boolean validateWords(Move move) {
        Board board = game.getBoard();
        Dictionary dictionary = game.getDictionary();

        List<String> formedWords = new ArrayList<>();
        Map<String, List<Square>> wordSquaresMap = new HashMap<>();

        int row = move.getStartRow();
        int col = move.getStartCol();
        Move.Direction direction = move.getDirection();

        Board tempBoard = new Board();

        // Copy the current board state
        for (int r = 0; r < Board.SIZE; r++) {
            for (int c = 0; c < Board.SIZE; c++) {
                Square square = board.getSquare(r, c);
                if (square.hasTile()) {
                    tempBoard.placeTile(r, c, square.getTile());
                }
            }
        }

        List<Point> newTilePositions = new ArrayList<>();

        // Place the new tiles on the temporary board
        if (direction == Move.Direction.HORIZONTAL) {
            int c = col;
            for (Tile tile : move.getTiles()) {
                // Skip positions that already have tiles
                while (c < Board.SIZE && tempBoard.getSquare(row, c).hasTile()) {
                    c++;
                }

                if (c < Board.SIZE) {
                    tempBoard.placeTile(row, c, tile);
                    newTilePositions.add(new Point(row, c));
                    c++;
                }
            }
        } else {
            int r = row;
            for (Tile tile : move.getTiles()) {
                // Skip positions that already have tiles
                while (r < Board.SIZE && tempBoard.getSquare(r, col).hasTile()) {
                    r++;
                }

                if (r < Board.SIZE) {
                    tempBoard.placeTile(r, col, tile);
                    newTilePositions.add(new Point(r, col));
                    r++;
                }
            }
        }

        // Check the main word formed in the direction of placement
        List<Square> mainWord;
        if (direction == Move.Direction.HORIZONTAL) {
            mainWord = tempBoard.getHorizontalWord(row, col);
        } else {
            mainWord = tempBoard.getVerticalWord(row, col);
        }

        // The main word must be at least 2 letters long
        if (mainWord.isEmpty() || mainWord.size() < 2) {
            System.out.println("Invalid move: Main word is too short");
            return false;
        }

        String mainWordStr = Board.getWordString(mainWord);
        if (!dictionary.isValidWord(mainWordStr)) {
            System.out.println("Invalid move: Main word '" + mainWordStr + "' is not in dictionary");
            return false;
        }

        formedWords.add(mainWordStr);
        wordSquaresMap.put(mainWordStr, mainWord);

        // Check any crossing words formed by each new tile
        for (Point p : newTilePositions) {
            List<Square> crossWord;

            // Check for a crossing word perpendicular to the direction of play
            if (direction == Move.Direction.HORIZONTAL) {
                crossWord = tempBoard.getVerticalWord(p.x, p.y);
            } else {
                crossWord = tempBoard.getHorizontalWord(p.x, p.y);
            }

            // Only validate crossing words that are at least 2 letters long
            if (crossWord.size() > 1) {
                String crossWordStr = Board.getWordString(crossWord);

                if (!dictionary.isValidWord(crossWordStr)) {
                    System.out.println("Invalid move: Crossing word '" + crossWordStr + "' is not in dictionary");
                    return false;
                }

                formedWords.add(crossWordStr);
                wordSquaresMap.put(crossWordStr, crossWord);
            }
        }

        // If we got here, all words are valid
        move.setFormedWords(formedWords);
        move.setMetadata("wordSquares", wordSquaresMap);

        // Calculate score
        int score = calculateMoveScore(move, tempBoard, wordSquaresMap);
        move.setScore(score);

        return true;
    }

    public boolean isValidWordFromTemporaryPlacements() {
        // Only proceed if there are temporary placements
        if (temporaryPlacements.isEmpty()) {
            return false;
        }

        Board board = game.getBoard();
        Dictionary dictionary = game.getDictionary();

        // Create a move from the temporary placements
        Move.Direction direction = determineDirection();

        // If direction is null and there's only one tile, we need to check both directions
        if (direction == null && temporaryPlacements.size() == 1) {
            // Get the position of the single temporary placement
            Point point = temporaryPlacements.keySet().iterator().next();

            // Create a temporary board with the current board state
            Board tempBoard = new Board();
            for (int r = 0; r < Board.SIZE; r++) {
                for (int c = 0; c < Board.SIZE; c++) {
                    if (board.getSquare(r, c).hasTile()) {
                        tempBoard.placeTile(r, c, board.getSquare(r, c).getTile());
                    }
                }
            }

            // Place the temporary tile
            tempBoard.placeTile(point.x, point.y, temporaryPlacements.get(point));

            // Check horizontal word
            List<Square> horizontalWord = tempBoard.getHorizontalWord(point.x, point.y);
            boolean horizontalValid = horizontalWord.size() >= 2 &&
                    dictionary.isValidWord(Board.getWordString(horizontalWord));

            // Check vertical word
            List<Square> verticalWord = tempBoard.getVerticalWord(point.x, point.y);
            boolean verticalValid = verticalWord.size() >= 2 &&
                    dictionary.isValidWord(Board.getWordString(verticalWord));

            // The placement is valid if it forms at least one valid word
            return horizontalValid || verticalValid;
        }

        // For multiple tiles or when direction is determined, use the regular validation
        return commitPlacement();
    }

    private int calculateWordScore(List<Square> wordSquares, boolean isNewMove) {
        int wordScore = 0;
        int wordMultiplier = 1;

        StringBuilder scoreCalc = new StringBuilder("Word score calculation: ");

        for (Square square : wordSquares) {
            Tile tile = square.getTile();
            int letterValue = tile.getValue();
            int effectiveValue = letterValue;

            if (isNewMove && !square.isSquareTypeUsed()) {
                if (square.getSquareType() == Square.SquareType.DOUBLE_LETTER) {
                    effectiveValue = letterValue * 2;
                    scoreCalc.append(tile.getLetter()).append("(").append(letterValue).append("×2) + ");
                } else if (square.getSquareType() == Square.SquareType.TRIPLE_LETTER) {
                    effectiveValue = letterValue * 3;
                    scoreCalc.append(tile.getLetter()).append("(").append(letterValue).append("×3) + ");
                } else {
                    scoreCalc.append(tile.getLetter()).append("(").append(letterValue).append(") + ");
                }

                if (square.getSquareType() == Square.SquareType.DOUBLE_WORD ||
                        square.getSquareType() == Square.SquareType.CENTER) {
                    wordMultiplier *= 2;
                } else if (square.getSquareType() == Square.SquareType.TRIPLE_WORD) {
                    wordMultiplier *= 3;
                }
            } else {
                scoreCalc.append(tile.getLetter()).append("(").append(letterValue).append(") + ");
            }

            wordScore += effectiveValue;
        }

        int finalWordScore = wordScore * wordMultiplier;

        if (wordMultiplier > 1) {
            scoreCalc.append(" = ").append(wordScore).append(" × ").append(wordMultiplier)
                    .append(" = ").append(finalWordScore);
        } else {
            if (scoreCalc.length() > 3) {
                scoreCalc.setLength(scoreCalc.length() - 3);
            }
            scoreCalc.append(" = ").append(finalWordScore);
        }

        System.out.println(scoreCalc.toString());
        return finalWordScore;
    }

    private int calculateMoveScore(Move move, Board tempBoard, Map<String, List<Square>> wordSquaresMap) {
        int totalScore = 0;
        boolean usedAllTiles = move.getTiles().size() == 7;

        System.out.println("Calculating score for move: " + move);

        for (String word : move.getFormedWords()) {
            List<Square> wordSquares = wordSquaresMap.get(word);
            if (wordSquares != null) {
                int wordScore = calculateWordScore(wordSquares, true);
                System.out.println("Word '" + word + "' score: " + wordScore);
                totalScore += wordScore;
            }
        }

        if (usedAllTiles) {
            System.out.println("Bingo bonus: " + ScrabbleConstants.BINGO_BONUS);
            totalScore += ScrabbleConstants.BINGO_BONUS;
        }

        System.out.println("Total move score: " + totalScore);
        return totalScore;
    }

    // Replace the selectTileFromRack method in GameController.java with this fixed version

    public boolean selectTileFromRack(int index) {
        if (!gameInProgress) {
            return false;
        }

        Player currentPlayer = game.getCurrentPlayer();
        Rack rack = currentPlayer.getRack();

        if (index < 0 || index >= rack.size()) {
            return false;
        }

        // Get the tile at the specified index
        Tile tile = rack.getTile(index);

        // Check if this specific index is already selected
        if (selectedPositions.contains(index)) {
            // It's selected, so deselect it
            selectedTiles.remove(tile);  // Remove the tile
            selectedPositions.remove(Integer.valueOf(index));  // Remove the position
            System.out.println("Deselected tile at index " + index + ": " + tile.getLetter());
        } else {
            // It's not selected, so select it
            selectedTiles.add(tile);
            selectedPositions.add(index);
            System.out.println("Selected tile at index " + index + ": " + tile.getLetter());
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