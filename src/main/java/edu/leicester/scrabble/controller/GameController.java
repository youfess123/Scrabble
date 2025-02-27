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

        if (currentPlayer.isComputer()) {
            System.out.println("Computer's turn - preparing move");

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

            if (computerPlayer != null) {
                // Run computer move in background
                final com.scrabble.model.ComputerPlayer finalComputerPlayer = computerPlayer; // Create a final reference
                Task<Move> task = new Task<Move>() {
                    @Override
                    protected Move call() throws Exception {
                        // Add a small delay to make it feel more natural
                        Thread.sleep(1500);
                        System.out.println("Computer generating move...");
                        return finalComputerPlayer.generateMove(game);
                    }
                };

                task.setOnSucceeded(event -> {
                    Move move = task.getValue();
                    if (move != null) {
                        System.out.println("Computer executing move of type: " + move.getType());
                        makeMove(move);
                    } else {
                        System.out.println("Computer couldn't generate a move, passing");
                        Move passMove = Move.createPassMove(currentPlayer);
                        makeMove(passMove);
                    }
                });

                task.setOnFailed(event -> {
                    System.err.println("Computer move generation failed: " + task.getException().getMessage());
                    task.getException().printStackTrace();
                    // Create a pass move and execute it
                    Move passMove = Move.createPassMove(currentPlayer);
                    makeMove(passMove);
                });

                executor.submit(task);
            } else {
                System.err.println("Computer player not found");
                // If we can't find a computer player, just pass
                Move passMove = Move.createPassMove(currentPlayer);
                makeMove(passMove);
            }
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

        // For temporary placement, we just want to visually place the tile without committing
        // the move. The actual move validation and commitment happens in commitPlacement().
        Player currentPlayer = game.getCurrentPlayer();
        Rack rack = currentPlayer.getRack();

        // We'll place the first selected tile at the specified location
        if (selectedPositions.isEmpty()) {
            return false;
        }

        int tileIndex = selectedPositions.get(0);
        Tile tile = rack.getTile(tileIndex);

        return placeTileTemporarily(tileIndex, startRow, startCol);
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

        // Check placement validity
        Board board = game.getBoard();

        // Make sure the square is empty
        if (board.getSquare(row, col).hasTile() || hasTemporaryTileAt(row, col)) {
            return false;
        }

        // First move must include the center square
        if (board.isEmpty() && temporaryPlacements.isEmpty() &&
                (row != ScrabbleConstants.CENTER_SQUARE || col != ScrabbleConstants.CENTER_SQUARE)) {
            return false;
        }

        // For subsequent moves or continuing a placement,
        // either need to be adjacent to existing tiles or in line with temporary placements
        if (!board.isEmpty() && temporaryPlacements.isEmpty() && !board.hasAdjacentTile(row, col)) {
            return false;
        }

        // If we have existing temporary placements, check if the new placement is valid
        if (!temporaryPlacements.isEmpty() && !isValidTemporaryPlacement(row, col)) {
            return false;
        }

        // Get the tile from the rack
        Tile tile = rack.getTile(tileIndex);

        // Store the temporary placement
        temporaryPlacements.put(new Point(row, col), tile);
        temporaryIndices.add(tileIndex);

        // Clear the tile from the selected tiles and positions
        selectedTiles.remove(tile);
        selectedPositions.remove(Integer.valueOf(tileIndex));

        // Update the views
        updateBoard();
        updateRack();

        return true;
    }

    public boolean isValidTemporaryPlacement(int row, int col) {
        // If no temporary placements yet, it's valid if adjacent to an existing tile
        if (temporaryPlacements.isEmpty()) {
            return game.getBoard().hasAdjacentTile(row, col);
        }

        // If only one temporary placement exists, allow either horizontal or vertical continuation
        if (temporaryPlacements.size() == 1) {
            Point existingPoint = temporaryPlacements.keySet().iterator().next();

            // Allow placement in any of the four directions (above, below, left, right)
            boolean isAdjacent = (row == existingPoint.x && (col == existingPoint.y - 1 || col == existingPoint.y + 1)) ||
                    (col == existingPoint.y && (row == existingPoint.x - 1 || row == existingPoint.x + 1));

            return isAdjacent;
        }

        // For more than one tile, determine direction of existing temporary placements
        Move.Direction currentDirection = determineDirection();

        // If scattered (no clear direction), reject new placement
        if (currentDirection == null) {
            return false;
        }

        // Find bounds of current placements
        int minRow = Integer.MAX_VALUE;
        int maxRow = Integer.MIN_VALUE;
        int minCol = Integer.MAX_VALUE;
        int maxCol = Integer.MIN_VALUE;

        for (Point p : temporaryPlacements.keySet()) {
            minRow = Math.min(minRow, p.x);
            maxRow = Math.max(maxRow, p.x);
            minCol = Math.min(minCol, p.y);
            maxCol = Math.max(maxCol, p.y);
        }

        // Check if the new placement continues the line in the established direction
        if (currentDirection == Move.Direction.HORIZONTAL) {
            // Must be in the same row
            if (row != minRow) {
                return false;
            }

            // Must be on the immediate left, right, or fill a gap
            boolean isValid = col == minCol - 1 || col == maxCol + 1;

            // Check if filling a gap in the current placement
            if (!isValid) {
                for (int c = minCol; c <= maxCol; c++) {
                    if (col == c && !hasTemporaryTileAt(row, c) && !game.getBoard().getSquare(row, c).hasTile()) {
                        isValid = true;
                        break;
                    }
                }
            }

            return isValid;

        } else { // VERTICAL
            // Must be in the same column
            if (col != minCol) {
                return false;
            }

            // Must be on the immediate top, bottom, or fill a gap
            boolean isValid = row == minRow - 1 || row == maxRow + 1;

            // Check if filling a gap in the current placement
            if (!isValid) {
                for (int r = minRow; r <= maxRow; r++) {
                    if (row == r && !hasTemporaryTileAt(r, col) && !game.getBoard().getSquare(r, col).hasTile()) {
                        isValid = true;
                        break;
                    }
                }
            }

            return isValid;
        }
    }

    public Tile getTemporaryTileAt(int row, int col) {
        Point position = new Point(row, col);
        return temporaryPlacements.get(position);
    }

    public boolean hasTemporaryTileAt(int row, int col) {
        Point position = new Point(row, col);
        return temporaryPlacements.containsKey(position);
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

    public void cancelPlacements() {
        if (temporaryPlacements.isEmpty()) {
            return;
        }

        // Add the tiles back to selected state
        Player currentPlayer = game.getCurrentPlayer();
        Rack rack = currentPlayer.getRack();

        for (int index : temporaryIndices) {
            if (index >= 0 && index < rack.size()) {
                Tile tile = rack.getTile(index);
                if (!selectedTiles.contains(tile)) {
                    selectedTiles.add(tile);
                    selectedPositions.add(index);
                }
            }
        }

        // Clear temporary placements
        temporaryPlacements.clear();
        temporaryIndices.clear();

        // Update views
        updateBoard();
        updateRack();
    }

    public Move.Direction determineDirection() {
        // If only one tile, we can't determine direction yet - default to horizontal
        if (temporaryPlacements.size() <= 1) {
            return Move.Direction.HORIZONTAL;
        }

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
        } else if (sameRow && sameCol) {
            // All tiles are at the same point (should not happen)
            // Default to horizontal
            return Move.Direction.HORIZONTAL;
        } else {
            // Tiles are scattered (not in a line) - invalid
            return null;
        }
    }

    private boolean validateWords(Move move) {
        Board board = game.getBoard();
        Dictionary dictionary = game.getDictionary();

        // This will hold all words formed by this move
        List<String> formedWords = new ArrayList<>();

        int row = move.getStartRow();
        int col = move.getStartCol();
        Move.Direction direction = move.getDirection();

        // Create a temporary board to validate words
        Board tempBoard = new Board();

        // Copy existing tiles to the temporary board
        for (int r = 0; r < Board.SIZE; r++) {
            for (int c = 0; c < Board.SIZE; c++) {
                Square square = board.getSquare(r, c);
                if (square.hasTile()) {
                    tempBoard.placeTile(r, c, square.getTile());
                }
            }
        }

        // Add temporary placements to the board
        List<Point> newTilePositions = new ArrayList<>();

        if (direction == Move.Direction.HORIZONTAL) {
            int c = col;
            for (Tile tile : move.getTiles()) {
                // Skip squares that already have tiles
                while (c < Board.SIZE && tempBoard.getSquare(row, c).hasTile()) {
                    c++;
                }

                if (c < Board.SIZE) {
                    tempBoard.placeTile(row, c, tile);
                    newTilePositions.add(new Point(row, c));
                    c++;
                }
            }
        } else { // VERTICAL
            int r = row;
            for (Tile tile : move.getTiles()) {
                // Skip squares that already have tiles
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

        // Check the main word formed by the move
        List<Square> mainWord;
        if (direction == Move.Direction.HORIZONTAL) {
            mainWord = tempBoard.getHorizontalWord(row, col);
        } else {
            mainWord = tempBoard.getVerticalWord(row, col);
        }

        if (mainWord.isEmpty() || mainWord.size() < 2) {
            return false; // A word must have at least 2 letters
        }

        String mainWordStr = Board.getWordString(mainWord);
        if (!dictionary.isValidWord(mainWordStr)) {
            return false;
        }

        formedWords.add(mainWordStr);

        // Check any crossing words formed
        for (Point p : newTilePositions) {
            List<Square> crossWord;

            if (direction == Move.Direction.HORIZONTAL) {
                crossWord = tempBoard.getVerticalWord(p.x, p.y);
            } else {
                crossWord = tempBoard.getHorizontalWord(p.x, p.y);
            }

            // If a crossing word exists (more than just the tile itself)
            if (crossWord.size() > 1) {
                String crossWordStr = Board.getWordString(crossWord);

                if (!dictionary.isValidWord(crossWordStr)) {
                    return false;
                }

                formedWords.add(crossWordStr);
            }
        }

        // All words formed are valid
        move.setFormedWords(formedWords);

        // Calculate the score for this move
        int score = calculateMoveScore(move, tempBoard);
        move.setScore(score);

        return true;
    }

    private int calculateMoveScore(Move move, Board tempBoard) {
        int totalScore = 0;

        // The formed words are already calculated in validateWords
        for (String word : move.getFormedWords()) {
            int wordScore = calculateWordScore(word, tempBoard);
            totalScore += wordScore;
        }

        // Add bingo bonus if all 7 tiles were used
        if (move.getTiles().size() == 7) {
            totalScore += ScrabbleConstants.BINGO_BONUS;
        }

        return totalScore;
    }

    private int calculateWordScore(String word, Board board) {
        // This is a simplified scoring method - a complete implementation
        // would track which squares the word is on and apply letter/word multipliers

        int score = 0;
        for (char c : word.toCharArray()) {
            // Get the point value for this letter
            score += TileBag.getPointValue(c);
        }

        return score;
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

    public void shutdown() {
        executor.shutdown();
    }
}