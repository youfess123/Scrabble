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
import java.util.Set;
import java.util.HashSet;
import java.util.TreeMap;
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

        updateBoard();
        updateRack();
        updateCurrentPlayer();

        makeComputerMoveIfNeeded();
    }

    public List<Move> getMoveHistory() {
        return game.getMoveHistory();
    }

    public boolean makeMove(Move move) {
        if (!gameInProgress) {
            return false;
        }

        boolean success = game.executeMove(move);

        if (success) {
            System.out.println("Move executed: " + move.getType() + " by " + move.getPlayer().getName());

            selectedTiles.clear();
            selectedPositions.clear();

            temporaryPlacements.clear();
            temporaryIndices.clear();

            updateBoard();
            updateRack();
            updateCurrentPlayer();

            if (playerUpdateListener != null) {
                Platform.runLater(playerUpdateListener);
            }

            if (game.isGameOver()) {
                gameInProgress = false;
                if (gameOverListener != null) {
                    gameOverListener.run();
                }
                return true;
            }

            if (move.getPlayer().isComputer()) {
                showComputerMoveInfo(move);
            }

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
            if (!gameInProgress) {
                System.out.println("[Exchange] Game not in progress");
                return false;
            }

            if (selectedTiles.isEmpty()) {
                System.out.println("[Exchange] No tiles selected");
                showError("No tiles selected. Please select tiles from your rack to exchange.");
                return false;
            }

            if (game.getTileBag().getTileCount() < 1) {
                System.out.println("[Exchange] Not enough tiles in bag");
                showError("Not enough tiles in the bag for exchange.");
                return false;
            }

            if (!temporaryPlacements.isEmpty()) {
                System.out.println("[Exchange] Temporary placements exist");
                showError("Please cancel your current placement before exchanging tiles.");
                return false;
            }

            System.out.println("[Exchange] Exchanging " + selectedTiles.size() + " tiles");

            StringBuilder exchangeLog = new StringBuilder("Selected tiles: ");
            for (Tile t : selectedTiles) {
                exchangeLog.append(t.getLetter()).append(" ");
            }
            System.out.println(exchangeLog.toString());

            List<Tile> tilesToExchange = new ArrayList<>(selectedTiles);

            Move exchangeMove = Move.createExchangeMove(game.getCurrentPlayer(), tilesToExchange);

            Player currentPlayer = game.getCurrentPlayer();
            boolean currentPlayerIsHuman = !currentPlayer.isComputer();

            selectedTiles.clear();
            selectedPositions.clear();

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
        Platform.runLater(() -> {
            try {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Tiles Exchanged");
                alert.setHeaderText("Your tiles have been exchanged successfully");
                alert.setContentText("Your new tiles are now in your rack. The computer's turn will begin when you close this dialog.");

                updateRack();
                updateBoard();
                updateCurrentPlayer();

                alert.showAndWait();

                System.out.println("[Exchange] Dialog closed, checking if computer's turn");

                Player currentPlayer = game.getCurrentPlayer();

                if (currentPlayer.isComputer()) {
                    System.out.println("[Exchange] Current player is computer: " + currentPlayer.getName());

                    updateCurrentPlayer();

                    new Thread(() -> {
                        try {
                            Thread.sleep(300);
                            Platform.runLater(() -> {
                                System.out.println("[Exchange] Explicitly triggering computer move");
                                computerMoveInProgress = false;
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

    /**
     * Places a tile temporarily on the board.
     * Enhanced to use GADDAG for placement validation.
     *
     * @param rackIndex Index of the tile in the player's rack
     * @param row Row position on the board
     * @param col Column position on the board
     * @return true if the placement was valid
     */
    public boolean placeTileTemporarily(int rackIndex, int row, int col) {
        try {
            if (row < 0 || row >= Board.SIZE || col < 0 || col >= Board.SIZE) {
                return false;
            }

            Player currentPlayer = game.getCurrentPlayer();
            Rack rack = currentPlayer.getRack();

            if (rackIndex < 0 || rackIndex >= rack.size()) {
                return false;
            }

            if (game.getBoard().getSquare(row, col).hasTile() || hasTemporaryTileAt(row, col)) {
                return false;
            }

            Tile tile = rack.getTile(rackIndex);

            if (temporaryIndices.contains(rackIndex)) {
                return false;
            }

            if (!isValidTemporaryPlacement(row, col)) {
                return false;
            }

            temporaryPlacements.put(new Point(row, col), tile);
            temporaryIndices.add(rackIndex);

            updateBoard();
            updateRack();

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

    /**
     * Validates if a temporary tile placement is valid.
     * Enhanced to better handle extending existing words with GADDAG logic.
     *
     * @param row The row position
     * @param col The column position
     * @return true if the placement is valid
     */
    public boolean isValidTemporaryPlacement(int row, int col) {
        Board board = game.getBoard();
        Dictionary dictionary = game.getDictionary();

        // Check if the position is already occupied
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

                // Check if this placement is in the range of existing placements
                int minCol = Integer.MAX_VALUE;
                int maxCol = Integer.MIN_VALUE;

                for (Point p : placementPoints) {
                    minCol = Math.min(minCol, p.y);
                    maxCol = Math.max(maxCol, p.y);
                }

                // The new placement must be between existing placements
                // or at most one position away to either side
                if (col >= minCol - 1 && col <= maxCol + 1) {
                    return true;
                }

                // Or if it's adjacent to an existing board tile in the same row
                return (col > 0 && board.getSquare(row, col-1).hasTile()) ||
                        (col < Board.SIZE-1 && board.getSquare(row, col+1).hasTile());

            } else { // VERTICAL
                // All placements must be in the same column
                for (Point p : placementPoints) {
                    if (p.y != col) {
                        return false;
                    }
                }

                // Check if this placement is in the range of existing placements
                int minRow = Integer.MAX_VALUE;
                int maxRow = Integer.MIN_VALUE;

                for (Point p : placementPoints) {
                    minRow = Math.min(minRow, p.x);
                    maxRow = Math.max(maxRow, p.x);
                }

                // The new placement must be between existing placements
                // or at most one position away to either side
                if (row >= minRow - 1 && row <= maxRow + 1) {
                    return true;
                }

                // Or if it's adjacent to an existing board tile in the same column
                return (row > 0 && board.getSquare(row-1, col).hasTile()) ||
                        (row < Board.SIZE-1 && board.getSquare(row+1, col).hasTile());
            }
        }

        // For the first tile in a move, check if it extends an existing word
        // or is adjacent to at least one existing tile

        // Check for adjacent tiles horizontally
        boolean hasHorizontalAdjacent = (col > 0 && board.getSquare(row, col-1).hasTile()) ||
                (col < Board.SIZE-1 && board.getSquare(row, col+1).hasTile());

        // Check for adjacent tiles vertically
        boolean hasVerticalAdjacent = (row > 0 && board.getSquare(row-1, col).hasTile()) ||
                (row < Board.SIZE-1 && board.getSquare(row+1, col).hasTile());

        // Check diagonally adjacent (only for determining if placement is valid)
        boolean hasDiagonalAdjacent = (row > 0 && col > 0 && board.getSquare(row-1, col-1).hasTile()) ||
                (row > 0 && col < Board.SIZE-1 && board.getSquare(row-1, col+1).hasTile()) ||
                (row < Board.SIZE-1 && col > 0 && board.getSquare(row+1, col-1).hasTile()) ||
                (row < Board.SIZE-1 && col < Board.SIZE-1 && board.getSquare(row+1, col+1).hasTile());

        // Check if this placement extends a word
        boolean extendsHorizontalWord = false;
        boolean extendsVerticalWord = false;

        // Check if this extends a horizontal word
        if (col > 0 && board.getSquare(row, col-1).hasTile()) {
            extendsHorizontalWord = true;
        } else if (col < Board.SIZE-1 && board.getSquare(row, col+1).hasTile()) {
            extendsHorizontalWord = true;
        }

        // Check if this extends a vertical word
        if (row > 0 && board.getSquare(row-1, col).hasTile()) {
            extendsVerticalWord = true;
        } else if (row < Board.SIZE-1 && board.getSquare(row+1, col).hasTile()) {
            extendsVerticalWord = true;
        }

        // Return true if adjacent to any existing tile or extends a word
        return hasHorizontalAdjacent || hasVerticalAdjacent || hasDiagonalAdjacent ||
                extendsHorizontalWord || extendsVerticalWord;
    }

    /**
     * Determines the direction of the current word placement.
     * Enhanced to better handle single tile extensions of existing words.
     *
     * @return The direction (HORIZONTAL or VERTICAL) or null if undetermined
     */
    public Move.Direction determineDirection() {
        // If there are less than 2 temporary tiles, try to determine from board context
        if (temporaryPlacements.size() <= 1) {
            // With only one tile, we need to examine surrounding tiles
            if (temporaryPlacements.size() == 1) {
                Point p = temporaryPlacements.keySet().iterator().next();
                int row = p.x;
                int col = p.y;
                Board board = game.getBoard();

                // Check for horizontal adjacency (tiles to left or right)
                boolean hasHorizontalAdjacent =
                        (col > 0 && board.getSquare(row, col-1).hasTile()) ||
                                (col < Board.SIZE-1 && board.getSquare(row, col+1).hasTile());

                // Check for vertical adjacency (tiles above or below)
                boolean hasVerticalAdjacent =
                        (row > 0 && board.getSquare(row-1, col).hasTile()) ||
                                (row < Board.SIZE-1 && board.getSquare(row+1, col).hasTile());

                // If only has horizontal adjacency, it's likely horizontal
                if (hasHorizontalAdjacent && !hasVerticalAdjacent) {
                    return Move.Direction.HORIZONTAL;
                }

                // If only has vertical adjacency, it's likely vertical
                if (hasVerticalAdjacent && !hasHorizontalAdjacent) {
                    return Move.Direction.VERTICAL;
                }

                // If has both or neither, check for longer words in each direction
                // to determine the most likely direction
                List<Square> horizontalWord = new ArrayList<>();
                List<Square> verticalWord = new ArrayList<>();

                // Get horizontal word (if any)
                int left = col;
                while (left > 0 && board.getSquare(row, left-1).hasTile()) {
                    left--;
                }

                int right = col;
                while (right < Board.SIZE-1 && board.getSquare(row, right+1).hasTile()) {
                    right++;
                }

                if (right > left) {
                    for (int c = left; c <= right; c++) {
                        if (board.getSquare(row, c).hasTile()) {
                            horizontalWord.add(board.getSquare(row, c));
                        }
                    }
                }

                // Get vertical word (if any)
                int top = row;
                while (top > 0 && board.getSquare(top-1, col).hasTile()) {
                    top--;
                }

                int bottom = row;
                while (bottom < Board.SIZE-1 && board.getSquare(bottom+1, col).hasTile()) {
                    bottom++;
                }

                if (bottom > top) {
                    for (int r = top; r <= bottom; r++) {
                        if (board.getSquare(r, col).hasTile()) {
                            verticalWord.add(board.getSquare(r, col));
                        }
                    }
                }

                // If one direction has a longer word, choose that direction
                if (horizontalWord.size() > verticalWord.size()) {
                    return Move.Direction.HORIZONTAL;
                } else if (verticalWord.size() > horizontalWord.size()) {
                    return Move.Direction.VERTICAL;
                }
            }

            // If still can't determine, we'll default to horizontal in commitPlacement
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

    /**
     * Identifies all words formed by the current placement.
     * Enhanced with GADDAG for better word identification including extensions.
     *
     * @param move The move being evaluated
     * @return List of words formed
     */
    private List<String> calculateFormedWords(Move move) {
        List<String> formedWords = new ArrayList<>();
        Board board = game.getBoard();
        Dictionary dictionary = game.getDictionary();
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
        List<Point> newTilePositions = new ArrayList<>();

        for (Tile tile : move.getTiles()) {
            // Skip squares that already have tiles
            while (currentRow < Board.SIZE && currentCol < Board.SIZE &&
                    tempBoard.getSquare(currentRow, currentCol).hasTile()) {
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
                newTilePositions.add(new Point(currentRow, currentCol));

                // Move to next position
                if (move.getDirection() == Move.Direction.HORIZONTAL) {
                    currentCol++;
                } else {
                    currentRow++;
                }
            }
        }

        // Special case for single tile placement - check both directions
        if (move.getTiles().size() == 1 && newTilePositions.size() == 1) {
            Point p = newTilePositions.get(0);

            // Get both possible words
            List<Square> horizontalWord = tempBoard.getHorizontalWord(p.x, p.y);
            List<Square> verticalWord = tempBoard.getVerticalWord(p.x, p.y);

            // Add horizontal word if valid (at least 2 letters)
            if (horizontalWord.size() >= 2) {
                String horizontalWordStr = Board.getWordString(horizontalWord);
                formedWords.add(horizontalWordStr);
            }

            // Add vertical word if valid (at least 2 letters)
            if (verticalWord.size() >= 2) {
                String verticalWordStr = Board.getWordString(verticalWord);
                // Don't add duplicate words
                if (!formedWords.contains(verticalWordStr)) {
                    formedWords.add(verticalWordStr);
                }
            }

            return formedWords;
        }

        // For multi-tile placements, find the main word first
        List<Square> mainWord;
        if (move.getDirection() == Move.Direction.HORIZONTAL) {
            mainWord = tempBoard.getHorizontalWord(row, col);
        } else {
            mainWord = tempBoard.getVerticalWord(row, col);
        }

        if (mainWord.size() >= 2) {
            formedWords.add(Board.getWordString(mainWord));
        }

        // Find any crossing words formed by each new tile
        for (Point p : newTilePositions) {
            List<Square> crossingWord;

            // Check for a crossing word perpendicular to the direction of play
            if (move.getDirection() == Move.Direction.HORIZONTAL) {
                crossingWord = tempBoard.getVerticalWord(p.x, p.y);
            } else {
                crossingWord = tempBoard.getHorizontalWord(p.x, p.y);
            }

            // Only add crossing words that are at least 2 letters long
            if (crossingWord.size() >= 2) {
                String crossWordStr = Board.getWordString(crossingWord);
                if (!formedWords.contains(crossWordStr)) {
                    formedWords.add(crossWordStr);
                }
            }
        }

        return formedWords;
    }

    /**
     * Validates all words formed by a move.
     * Enhanced with GADDAG-based validation.
     *
     * @param move The move to validate
     * @return true if all formed words are valid, false otherwise
     */
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

        // Special case for single tile placement - check both directions
        if (move.getTiles().size() == 1 && newTilePositions.size() == 1) {
            Point p = newTilePositions.get(0);

            // Check horizontal word
            List<Square> horizontalWord = tempBoard.getHorizontalWord(p.x, p.y);
            if (horizontalWord.size() >= 2) {
                String horizontalWordStr = Board.getWordString(horizontalWord);
                if (dictionary.isValidWord(horizontalWordStr)) {
                    formedWords.add(horizontalWordStr);
                    wordSquaresMap.put(horizontalWordStr, horizontalWord);
                }
            }

            // Check vertical word
            List<Square> verticalWord = tempBoard.getVerticalWord(p.x, p.y);
            if (verticalWord.size() >= 2) {
                String verticalWordStr = Board.getWordString(verticalWord);
                if (dictionary.isValidWord(verticalWordStr)) {
                    // Avoid duplicates
                    if (!formedWords.contains(verticalWordStr)) {
                        formedWords.add(verticalWordStr);
                        wordSquaresMap.put(verticalWordStr, verticalWord);
                    }
                }
            }

            // Make sure at least one valid word was formed
            if (formedWords.isEmpty()) {
                System.out.println("Invalid move: No valid words formed with single tile");
                return false;
            }

            // If we get here, the move is valid
            move.setFormedWords(formedWords);
            move.setMetadata("wordSquares", wordSquaresMap);

            // Calculate score
            int score = calculateMoveScore(move, tempBoard, wordSquaresMap);
            move.setScore(score);

            return true;
        }

        // For multi-tile placements, check the main word first
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

    /**
     * Commits the current temporary placement as a move.
     * Enhanced with GADDAG-based word detection and validation.
     *
     * @return true if the move was valid and successfully executed
     */
    public boolean commitPlacement() {
        if (temporaryPlacements.isEmpty()) {
            return false;
        }

        // Determine the direction of the placement
        Move.Direction direction = determineDirection();

        // Special handling for single tile placement
        if (temporaryPlacements.size() == 1) {
            Point p = temporaryPlacements.keySet().iterator().next();
            int row = p.x;
            int col = p.y;
            Board board = game.getBoard();

            // Create temporary board with the new tile
            Board tempBoard = new Board();

            // Copy existing board
            for (int r = 0; r < Board.SIZE; r++) {
                for (int c = 0; c < Board.SIZE; c++) {
                    if (board.getSquare(r, c).hasTile()) {
                        tempBoard.placeTile(r, c, board.getSquare(r, c).getTile());
                    }
                }
            }

            // Add our temporary tile
            Tile placedTile = temporaryPlacements.get(p);
            tempBoard.placeTile(row, col, placedTile);

            // Check for words in both directions
            List<Square> horizontalWord = tempBoard.getHorizontalWord(row, col);
            List<Square> verticalWord = tempBoard.getVerticalWord(row, col);

            String horizontalWordStr = horizontalWord.size() >= 2 ? Board.getWordString(horizontalWord) : "";
            String verticalWordStr = verticalWord.size() >= 2 ? Board.getWordString(verticalWord) : "";

            boolean formingHorizontalWord = horizontalWord.size() >= 2 &&
                    game.getDictionary().isValidWord(horizontalWordStr);

            boolean formingVerticalWord = verticalWord.size() >= 2 &&
                    game.getDictionary().isValidWord(verticalWordStr);

            // Choose direction based on which forms valid words
            if (formingHorizontalWord && !formingVerticalWord) {
                direction = Move.Direction.HORIZONTAL;
            } else if (formingVerticalWord && !formingHorizontalWord) {
                direction = Move.Direction.VERTICAL;
            } else if (formingHorizontalWord && formingVerticalWord) {
                // If forming words in both directions, choose the longer word
                direction = horizontalWord.size() >= verticalWord.size() ?
                        Move.Direction.HORIZONTAL : Move.Direction.VERTICAL;
            } else {
                // Not forming any valid words
                return false;
            }
        }

        // If direction still couldn't be determined, default to horizontal
        if (direction == null) {
            direction = Move.Direction.HORIZONTAL;
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
            // Use a TreeMap to sort by column (automatically sorted by key)
            Map<Integer, Tile> colToTile = new TreeMap<>();

            for (Map.Entry<Point, Tile> entry : temporaryPlacements.entrySet()) {
                Point p = entry.getKey();
                if (p.x == startRow) {
                    colToTile.put(p.y, entry.getValue());
                }
            }

            tilesToPlace.addAll(colToTile.values());
        } else { // VERTICAL
            // Use a TreeMap to sort by row (automatically sorted by key)
            Map<Integer, Tile> rowToTile = new TreeMap<>();

            for (Map.Entry<Point, Tile> entry : temporaryPlacements.entrySet()) {
                Point p = entry.getKey();
                if (p.y == startCol) {
                    rowToTile.put(p.x, entry.getValue());
                }
            }

            tilesToPlace.addAll(rowToTile.values());
        }

        placeMove.addTiles(tilesToPlace);

        // Calculate the words formed and their scores
        List<String> formedWords = calculateFormedWords(placeMove);
        if (formedWords.isEmpty()) {
            return false; // No valid words formed
        }

        // Set the formed words
        placeMove.setFormedWords(formedWords);

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

    public Map<Point, Tile> getTemporaryPlacements() {
        return new HashMap<>(temporaryPlacements);
    }

    public List<Tile> getSelectedTiles() {
        return new ArrayList<>(selectedTiles);
    }
}