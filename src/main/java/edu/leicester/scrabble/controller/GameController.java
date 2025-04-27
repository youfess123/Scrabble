package edu.leicester.scrabble.controller;

import edu.leicester.scrabble.model.*;
import edu.leicester.scrabble.util.*;
import javafx.application.Platform;
import javafx.scene.control.Alert;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;

public class GameController {

    private final Game game;
    private final List<ComputerPlayer> computerPlayers;
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

        // Initialize computer players
        for (Player player : game.getPlayers()) {
            if (player.isComputer()) {
                computerPlayers.add(new ComputerPlayer(player, 2)); // Medium difficulty
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

            // Clear selections and temporary placements
            selectedTiles.clear();
            selectedPositions.clear();
            temporaryPlacements.clear();
            temporaryIndices.clear();

            // Update views
            updateBoard();
            updateRack();
            updateCurrentPlayer();

            if (playerUpdateListener != null) {
                Platform.runLater(playerUpdateListener);
            }

            // Check game over
            if (game.isGameOver()) {
                gameInProgress = false;
                if (gameOverListener != null) {
                    gameOverListener.run();
                }
                return true;
            }

            // Show computer move info if needed
            if (move.getPlayer().isComputer()) {
                showComputerMoveInfo(move);
            }

            // Trigger computer move if needed
            makeComputerMoveIfNeeded();
        }

        return success;
    }

    private void showComputerMoveInfo(Move move) {
        String moveInfo = formatMoveInfo(move);
        String finalMoveInfo = moveInfo;

        Platform.runLater(() -> {
            try {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Computer's Move");
                alert.setHeaderText("Computer has played");
                alert.setContentText(finalMoveInfo);
                alert.showAndWait();
            } catch (Exception e) {
                System.err.println("Error showing computer move info: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    private String formatMoveInfo(Move move) {
        switch (move.getType()) {
            case PLACE:
                return "Computer placed tiles to form: " + String.join(", ", move.getFormedWords())
                        + "\nScore: " + move.getScore() + " points";
            case EXCHANGE:
                return "Computer exchanged " + move.getTiles().size() + " tiles";
            case PASS:
                return "Computer passed its turn";
            default:
                return "Computer made a move";
        }
    }

    private void makeComputerMoveIfNeeded() {
        Player currentPlayer = game.getCurrentPlayer();
        if (currentPlayer.isComputer() && !computerMoveInProgress) {
            System.out.println("Computer's turn - preparing move");
            computerMoveInProgress = true;
            updateCurrentPlayer();

            ComputerPlayer computerPlayer = getComputerPlayerFor(currentPlayer);
            if (computerPlayer == null) {
                System.err.println("Computer player not found");
                Move passMove = Move.createPassMove(currentPlayer);
                makeMove(passMove);
                computerMoveInProgress = false;
                return;
            }

            // Set up emergency timeout
            ScheduledExecutorService emergencyTimer = setupEmergencyTimer(currentPlayer);

            // Execute computer move generation on a separate thread
            executeComputerMove(computerPlayer, currentPlayer, emergencyTimer);
        }
    }

    private ComputerPlayer getComputerPlayerFor(Player player) {
        for (ComputerPlayer cp : computerPlayers) {
            if (cp.getPlayer() == player) {
                return cp;
            }
        }
        return null;
    }

    private ScheduledExecutorService setupEmergencyTimer(Player currentPlayer) {
        ScheduledExecutorService emergencyTimer = Executors.newSingleThreadScheduledExecutor();
        emergencyTimer.schedule(() -> {
            if (computerMoveInProgress) {
                System.out.println("EMERGENCY: Computer move taking too long - forcing PASS");
                Platform.runLater(() -> {
                    Move passMove = Move.createPassMove(currentPlayer);
                    makeMove(passMove);
                    computerMoveInProgress = false;
                });
            }
        }, 5, TimeUnit.SECONDS);

        return emergencyTimer;
    }

    private void executeComputerMove(ComputerPlayer computerPlayer, Player currentPlayer,
                                     ScheduledExecutorService emergencyTimer) {
        executor.submit(() -> {
            try {
                Move computerMove = computerPlayer.generateMove(game);
                Thread.sleep(1000); // Small delay for better user experience

                // Cancel the emergency timeout
                emergencyTimer.shutdownNow();

                Platform.runLater(() -> {
                    try {
                        makeMove(computerMove);
                    } catch (Exception e) {
                        System.err.println("Error executing computer move: " + e.getMessage());
                        e.printStackTrace();
                        Move passMove = Move.createPassMove(currentPlayer);
                        makeMove(passMove);
                    } finally {
                        computerMoveInProgress = false;
                    }
                });
            } catch (Exception e) {
                System.err.println("Error in computer move: " + e.getMessage());
                e.printStackTrace();
                Platform.runLater(() -> {
                    emergencyTimer.shutdownNow();
                    Move passMove = Move.createPassMove(currentPlayer);
                    makeMove(passMove);
                    computerMoveInProgress = false;
                });
            }
        });
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

            List<Tile> tilesToExchange = new ArrayList<>(selectedTiles);
            Move exchangeMove = Move.createExchangeMove(game.getCurrentPlayer(), tilesToExchange);

            selectedTiles.clear();
            selectedPositions.clear();

            boolean success = game.executeMove(exchangeMove);
            if (success) {
                System.out.println("[Exchange] Exchange successful");
                updateBoard();
                updateRack();
                updateCurrentPlayer();

                if (!game.getCurrentPlayer().isComputer()) {
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

                Player currentPlayer = game.getCurrentPlayer();
                if (currentPlayer.isComputer()) {
                    new Thread(() -> {
                        try {
                            Thread.sleep(300);
                            Platform.runLater(() -> {
                                computerMoveInProgress = false;
                                makeComputerMoveIfNeeded();
                            });
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }).start();
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
            // Basic validation
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

            // Place the tile temporarily
            temporaryPlacements.put(new Point(row, col), tile);
            temporaryIndices.add(rackIndex);

            // Update views
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

    public boolean isValidTemporaryPlacement(int row, int col) {
        Board board = game.getBoard();

        // Check for existing tile
        if (board.getSquare(row, col).hasTile() || hasTemporaryTileAt(row, col)) {
            return false;
        }

        // For the first move, require the center square
        if (board.isEmpty() && temporaryPlacements.isEmpty()) {
            return row == ScrabbleConstants.CENTER_SQUARE && col == ScrabbleConstants.CENTER_SQUARE;
        }

        // Check for placement direction constraints
        if (!temporaryPlacements.isEmpty()) {
            return isValidDirectionalPlacement(row, col);
        }

        // For subsequent moves, must connect to existing tiles
        return BoardUtils.hasAdjacentTile(board, row, col);
    }

    private boolean isValidDirectionalPlacement(int row, int col) {
        List<Point> placementPoints = new ArrayList<>(temporaryPlacements.keySet());

        // For the first temporary tile, any adjacent square is valid
        if (placementPoints.size() == 1) {
            Point existingPoint = placementPoints.getFirst();
            return (row == existingPoint.x || col == existingPoint.y);
        }

        // For subsequent tiles, must follow the established direction
        Move.Direction direction = determineDirection();
        if (direction == null) {
            return false;
        }

        return isValidPlacementInDirection(row, col, direction, placementPoints);
    }

    private boolean isValidPlacementInDirection(int row, int col, Move.Direction direction,
                                                List<Point> placementPoints) {
        Board board = game.getBoard();

        if (direction == Move.Direction.HORIZONTAL) {
            // Check if all placements are in the same row
            for (Point p : placementPoints) {
                if (p.x != row) {
                    return false;
                }
            }

            // Check if the new column is adjacent to existing placements
            int minCol = Integer.MAX_VALUE;
            int maxCol = Integer.MIN_VALUE;
            for (Point p : placementPoints) {
                minCol = Math.min(minCol, p.y);
                maxCol = Math.max(maxCol, p.y);
            }

            // Allow placement adjacent to existing temporary tiles
            if (col >= minCol - 1 && col <= maxCol + 1) {
                return true;
            }

            // Or adjacent to a board tile
            return (col > 0 && board.getSquare(row, col - 1).hasTile()) ||
                    (col < Board.SIZE - 1 && board.getSquare(row, col + 1).hasTile());
        } else {
            // Check if all placements are in the same column
            for (Point p : placementPoints) {
                if (p.y != col) {
                    return false;
                }
            }

            // Check if the new row is adjacent to existing placements
            int minRow = Integer.MAX_VALUE;
            int maxRow = Integer.MIN_VALUE;
            for (Point p : placementPoints) {
                minRow = Math.min(minRow, p.x);
                maxRow = Math.max(maxRow, p.x);
            }

            // Allow placement adjacent to existing temporary tiles
            if (row >= minRow - 1 && row <= maxRow + 1) {
                return true;
            }

            // Or adjacent to a board tile
            return (row > 0 && board.getSquare(row - 1, col).hasTile()) ||
                    (row < Board.SIZE - 1 && board.getSquare(row + 1, col).hasTile());
        }
    }

    public Move.Direction determineDirection() {
        if (temporaryPlacements.size() <= 1) {
            if (temporaryPlacements.size() == 1) {
                return determineDirectionForSingleTile();
            }
            return null;
        }

        // Check if all placements are in the same row or column
        List<Point> points = new ArrayList<>(temporaryPlacements.keySet());
        boolean sameRow = true;
        int firstRow = points.getFirst().x;
        boolean sameColumn = true;
        int firstCol = points.getFirst().y;

        for (Point p : points) {
            if (p.x != firstRow) {
                sameRow = false;
            }
            if (p.y != firstCol) {
                sameColumn = false;
            }
        }

        if (sameRow) {
            return Move.Direction.HORIZONTAL;
        }
        if (sameColumn) {
            return Move.Direction.VERTICAL;
        }

        return null;
    }

    private Move.Direction determineDirectionForSingleTile() {
        Point p = temporaryPlacements.keySet().iterator().next();
        int row = p.x;
        int col = p.y;
        Board board = game.getBoard();

        // Check for adjacent tiles in each direction
        boolean hasHorizontalAdjacent = (col > 0 && board.getSquare(row, col - 1).hasTile()) ||
                (col < Board.SIZE - 1 && board.getSquare(row, col + 1).hasTile());
        boolean hasVerticalAdjacent = (row > 0 && board.getSquare(row - 1, col).hasTile()) ||
                (row < Board.SIZE - 1 && board.getSquare(row + 1, col).hasTile());

        // If only one direction has adjacents, use that
        if (hasHorizontalAdjacent && !hasVerticalAdjacent) {
            return Move.Direction.HORIZONTAL;
        }
        if (hasVerticalAdjacent && !hasHorizontalAdjacent) {
            return Move.Direction.VERTICAL;
        }

        // If both have adjacents, determine based on which forms longer words
        List<Square> horizontalWord = board.getHorizontalWord(row, col);
        List<Square> verticalWord = board.getVerticalWord(row, col);

        if (horizontalWord.size() > verticalWord.size()) {
            return Move.Direction.HORIZONTAL;
        } else if (verticalWord.size() > horizontalWord.size()) {
            return Move.Direction.VERTICAL;
        }

        // Default to horizontal if can't determine
        return null;
    }

    public boolean commitPlacement() {
        if (temporaryPlacements.isEmpty()) {
            return false;
        }

        // Determine direction
        Move.Direction direction = determineDirection();
        if (direction == null) {
            direction = determineBestDirection();
        }

        // Find start position
        int startRow = Integer.MAX_VALUE;
        int startCol = Integer.MAX_VALUE;
        for (Point p : temporaryPlacements.keySet()) {
            startRow = Math.min(startRow, p.x);
            startCol = Math.min(startCol, p.y);
        }

        // For first move, must include center
        if (game.getBoard().isEmpty()) {
            boolean includesCenter = false;
            for (Point p : temporaryPlacements.keySet()) {
                if (p.x == ScrabbleConstants.CENTER_SQUARE && p.y == ScrabbleConstants.CENTER_SQUARE) {
                    includesCenter = true;
                    break;
                }
            }
            if (!includesCenter) {
                return false;
            }
        }

        // Create the move
        Move placeMove = Move.createPlaceMove(game.getCurrentPlayer(), startRow, startCol, direction);
        List<Tile> tilesToPlace = getTilesInOrder(direction, startRow, startCol);
        placeMove.addTiles(tilesToPlace);

        // Validate the move
        Board tempBoard = BoardUtils.copyBoard(game.getBoard());
        List<Point> newPositions = placeTilesTemporarilyOnBoard(tempBoard, placeMove);
        List<String> formedWords = WordValidator.validateWords(tempBoard, placeMove, newPositions, game.getDictionary());

        if (formedWords.isEmpty()) {
            return false;
        }

        placeMove.setFormedWords(formedWords);

        // Calculate score
        Set<Point> newPositionsSet = new HashSet<>(newPositions);
        int score = ScoreCalculator.calculateMoveScore(placeMove, tempBoard, formedWords, newPositionsSet);
        placeMove.setScore(score);

        // Execute the move
        boolean success = makeMove(placeMove);

        if (success) {
            temporaryPlacements.clear();
            temporaryIndices.clear();
        }

        return success;
    }

    private Move.Direction determineBestDirection() {
        // Try to determine direction based on existing tiles on the board
        // Default to horizontal if can't determine
        return Move.Direction.HORIZONTAL;
    }

    private List<Tile> getTilesInOrder(Move.Direction direction, int startRow, int startCol) {
        List<Tile> orderedTiles = new ArrayList<>();

        if (direction == Move.Direction.HORIZONTAL) {
            Map<Integer, Tile> colToTile = new TreeMap<>();
            for (Map.Entry<Point, Tile> entry : temporaryPlacements.entrySet()) {
                Point p = entry.getKey();
                if (p.x == startRow) {
                    colToTile.put(p.y, entry.getValue());
                }
            }
            orderedTiles.addAll(colToTile.values());
        } else {
            Map<Integer, Tile> rowToTile = new TreeMap<>();
            for (Map.Entry<Point, Tile> entry : temporaryPlacements.entrySet()) {
                Point p = entry.getKey();
                if (p.y == startCol) {
                    rowToTile.put(p.x, entry.getValue());
                }
            }
            orderedTiles.addAll(rowToTile.values());
        }

        return orderedTiles;
    }

    private List<Point> placeTilesTemporarilyOnBoard(Board tempBoard, Move move) {
        int currentRow = move.getStartRow();
        int currentCol = move.getStartCol();
        Move.Direction direction = move.getDirection();
        List<Tile> tiles = move.getTiles();
        List<Point> newPositions = new ArrayList<>();

        for (Tile tile : tiles) {
            // Skip over existing tiles
            while (currentRow < Board.SIZE && currentCol < Board.SIZE &&
                    tempBoard.getSquare(currentRow, currentCol).hasTile()) {
                if (direction == Move.Direction.HORIZONTAL) {
                    currentCol++;
                } else {
                    currentRow++;
                }
            }

            // Place the tile
            if (currentRow < Board.SIZE && currentCol < Board.SIZE) {
                tempBoard.placeTile(currentRow, currentCol, tile);
                newPositions.add(new Point(currentRow, currentCol));

                if (direction == Move.Direction.HORIZONTAL) {
                    currentCol++;
                } else {
                    currentRow++;
                }
            }
        }

        return newPositions;
    }

    public void selectTileFromRack(int index) {
        if (!gameInProgress) {
            return;
        }

        Player currentPlayer = game.getCurrentPlayer();
        Rack rack = currentPlayer.getRack();

        if (index < 0 || index >= rack.size()) {
            return;
        }

        Tile tile = rack.getTile(index);

        if (selectedPositions.contains(index)) {
            // Deselect the tile
            selectedTiles.remove(tile);
            selectedPositions.remove(Integer.valueOf(index));
            System.out.println("Deselected tile at index " + index + ": " + tile.getLetter());
        } else {
            // Select the tile
            selectedTiles.add(tile);
            selectedPositions.add(index);
            System.out.println("Selected tile at index " + index + ": " + tile.getLetter());
        }

        updateRack();
    }

    public void cancelPlacements() {
        temporaryPlacements.clear();
        temporaryIndices.clear();
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

    public void setBlankTileLetter(int rackIndex, Tile blankTile) {
        Player currentPlayer = game.getCurrentPlayer();
        Rack rack = currentPlayer.getRack();

        if (rackIndex < 0 || rackIndex >= rack.size()) {
            return;
        }

        Tile currentTile = rack.getTile(rackIndex);
        if (!currentTile.isBlank()) {
            return; // Only allow changing blank tiles
        }

        // Replace the blank tile in the rack with the assigned letter
        rack.removeTile(currentTile);
        rack.addTile(blankTile);

        updateRack();
    }

    // Getters and setters

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