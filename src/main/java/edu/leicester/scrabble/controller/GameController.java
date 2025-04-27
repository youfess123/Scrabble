package edu.leicester.scrabble.controller;

import edu.leicester.scrabble.model.*;
import edu.leicester.scrabble.util.ScrabbleConstants;
import javafx.application.Platform;
import javafx.scene.control.Alert;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
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
                moveInfo = "Computer placed tiles to form: " + String.join(", ", move.getFormedWords())
                        + "\nScore: " + move.getScore() + " points";
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

    private void makeComputerMoveIfNeeded() {
        Player currentPlayer = game.getCurrentPlayer();
        if (currentPlayer.isComputer() && !computerMoveInProgress) {
            System.out.println("Computer's turn - preparing move");
            computerMoveInProgress = true;
            updateCurrentPlayer();

            ComputerPlayer computerPlayer = null;
            for (ComputerPlayer cp : computerPlayers) {
                if (cp.getPlayer() == currentPlayer) {
                    computerPlayer = cp;
                    break;
                }
            }

            if (computerPlayer == null) {
                System.err.println("Computer player not found");
                Move passMove = Move.createPassMove(currentPlayer);
                makeMove(passMove);
                computerMoveInProgress = false;
                return;
            }

            final ComputerPlayer finalComputerPlayer = computerPlayer;

            // Emergency timeout: force a pass move after 5 seconds
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

            // Execute computer move generation on a separate thread
            executor.submit(() -> {
                try {
                    Move computerMove = finalComputerPlayer.generateMove(game);
                    Thread.sleep(1000);
                    emergencyTimeout.cancel(false);
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
                        try {
                            emergencyTimeout.cancel(false);
                            emergencyTimer.shutdownNow();
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
            System.out.println(exchangeLog);

            List<Tile> tilesToExchange = new ArrayList<>(selectedTiles);
            Move exchangeMove = Move.createExchangeMove(game.getCurrentPlayer(), tilesToExchange);
            Player currentPlayer = game.getCurrentPlayer();
            boolean currentPlayerIsHuman = !currentPlayer.isComputer();

            selectedTiles.clear();
            selectedPositions.clear();

            boolean success = game.executeMove(exchangeMove);
            if (success) {
                System.out.println("[Exchange] Exchange successful");
                updateBoard();
                updateRack();
                updateCurrentPlayer();
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

    public boolean isValidTemporaryPlacement(int row, int col) {
        Board board = game.getBoard();
        if (board.getSquare(row, col).hasTile() || hasTemporaryTileAt(row, col)) {
            return false;
        }
        // For the first move, require the center square.
        if (board.isEmpty() && temporaryPlacements.isEmpty()) {
            return row == ScrabbleConstants.CENTER_SQUARE && col == ScrabbleConstants.CENTER_SQUARE;
        }
        if (!temporaryPlacements.isEmpty()) {
            List<Point> placementPoints = new ArrayList<>(temporaryPlacements.keySet());
            if (placementPoints.size() == 1) {
                Point existingPoint = placementPoints.getFirst();
                return (row == existingPoint.x || col == existingPoint.y);
            }
            Move.Direction direction = determineDirection();
            if (direction == null) {
                return false;
            }
            if (direction == Move.Direction.HORIZONTAL) {
                for (Point p : placementPoints) {
                    if (p.x != row) {
                        return false;
                    }
                }
                int minCol = Integer.MAX_VALUE;
                int maxCol = Integer.MIN_VALUE;
                for (Point p : placementPoints) {
                    minCol = Math.min(minCol, p.y);
                    maxCol = Math.max(maxCol, p.y);
                }
                if (col >= minCol - 1 && col <= maxCol + 1) {
                    return true;
                }
                return (col > 0 && board.getSquare(row, col - 1).hasTile()) ||
                        (col < Board.SIZE - 1 && board.getSquare(row, col + 1).hasTile());
            } else { // VERTICAL
                for (Point p : placementPoints) {
                    if (p.y != col) {
                        return false;
                    }
                }
                int minRow = Integer.MAX_VALUE;
                int maxRow = Integer.MIN_VALUE;
                for (Point p : placementPoints) {
                    minRow = Math.min(minRow, p.x);
                    maxRow = Math.max(maxRow, p.x);
                }
                if (row >= minRow - 1 && row <= maxRow + 1) {
                    return true;
                }
                return (row > 0 && board.getSquare(row - 1, col).hasTile()) ||
                        (row < Board.SIZE - 1 && board.getSquare(row + 1, col).hasTile());
            }
        }
        boolean hasHorizontalAdjacent = (col > 0 && board.getSquare(row, col - 1).hasTile()) ||
                (col < Board.SIZE - 1 && board.getSquare(row, col + 1).hasTile());
        boolean hasVerticalAdjacent = (row > 0 && board.getSquare(row - 1, col).hasTile()) ||
                (row < Board.SIZE - 1 && board.getSquare(row + 1, col).hasTile());
        boolean hasDiagonalAdjacent = (row > 0 && col > 0 && board.getSquare(row - 1, col - 1).hasTile()) ||
                (row > 0 && col < Board.SIZE - 1 && board.getSquare(row - 1, col + 1).hasTile()) ||
                (row < Board.SIZE - 1 && col > 0 && board.getSquare(row + 1, col - 1).hasTile()) ||
                (row < Board.SIZE - 1 && col < Board.SIZE - 1 && board.getSquare(row + 1, col + 1).hasTile());
        boolean extendsHorizontalWord = (col > 0 && board.getSquare(row, col - 1).hasTile()) ||
                (col < Board.SIZE - 1 && board.getSquare(row, col + 1).hasTile());
        boolean extendsVerticalWord = (row > 0 && board.getSquare(row - 1, col).hasTile()) ||
                (row < Board.SIZE - 1 && board.getSquare(row + 1, col).hasTile());
        return hasHorizontalAdjacent || hasVerticalAdjacent || hasDiagonalAdjacent ||
                extendsHorizontalWord || extendsVerticalWord;
    }

    public Move.Direction determineDirection() {
        if (temporaryPlacements.size() <= 1) {
            if (temporaryPlacements.size() == 1) {
                Point p = temporaryPlacements.keySet().iterator().next();
                int row = p.x;
                int col = p.y;
                Board board = game.getBoard();
                boolean hasHorizontalAdjacent = (col > 0 && board.getSquare(row, col - 1).hasTile()) ||
                        (col < Board.SIZE - 1 && board.getSquare(row, col + 1).hasTile());
                boolean hasVerticalAdjacent = (row > 0 && board.getSquare(row - 1, col).hasTile()) ||
                        (row < Board.SIZE - 1 && board.getSquare(row + 1, col).hasTile());
                if (hasHorizontalAdjacent && !hasVerticalAdjacent) {
                    return Move.Direction.HORIZONTAL;
                }
                if (hasVerticalAdjacent && !hasHorizontalAdjacent) {
                    return Move.Direction.VERTICAL;
                }
                List<Square> horizontalWord = new ArrayList<>();
                List<Square> verticalWord = new ArrayList<>();
                int left = col;
                while (left > 0 && board.getSquare(row, left - 1).hasTile()) {
                    left--;
                }
                int right = col;
                while (right < Board.SIZE - 1 && board.getSquare(row, right + 1).hasTile()) {
                    right++;
                }
                if (right > left) {
                    for (int c = left; c <= right; c++) {
                        if (board.getSquare(row, c).hasTile()) {
                            horizontalWord.add(board.getSquare(row, c));
                        }
                    }
                }
                int top = row;
                while (top > 0 && board.getSquare(top - 1, col).hasTile()) {
                    top--;
                }
                int bottom = row;
                while (bottom < Board.SIZE - 1 && board.getSquare(bottom + 1, col).hasTile()) {
                    bottom++;
                }
                if (bottom > top) {
                    for (int r = top; r <= bottom; r++) {
                        if (board.getSquare(r, col).hasTile()) {
                            verticalWord.add(board.getSquare(r, col));
                        }
                    }
                }
                if (horizontalWord.size() > verticalWord.size()) {
                    return Move.Direction.HORIZONTAL;
                } else if (verticalWord.size() > horizontalWord.size()) {
                    return Move.Direction.VERTICAL;
                }
            }
            return null;
        }
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

    private List<String> calculateFormedWords(Move move) {
        List<String> formedWords = new ArrayList<>();
        Board board = game.getBoard();
        int row = move.getStartRow();
        int col = move.getStartCol();

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
        for (Tile tile : move.getTiles()) {
            while (currentRow < Board.SIZE && currentCol < Board.SIZE &&
                    tempBoard.getSquare(currentRow, currentCol).hasTile()) {
                if (move.getDirection() == Move.Direction.HORIZONTAL) {
                    currentCol++;
                } else {
                    currentRow++;
                }
            }
            if (currentRow >= Board.SIZE || currentCol >= Board.SIZE) {
                break;
            }
            tempBoard.placeTile(currentRow, currentCol, tile);
            newTilePositions.add(new Point(currentRow, currentCol));
            if (move.getDirection() == Move.Direction.HORIZONTAL) {
                currentCol++;
            } else {
                currentRow++;
            }
        }

        String mainWord = "";
        if (move.getDirection() == Move.Direction.HORIZONTAL) {
            List<Square> wordSquares = tempBoard.getHorizontalWord(row, col);
            if (!wordSquares.isEmpty()) {
                mainWord = Board.getWordString(wordSquares);
                formedWords.add(mainWord);
            }
        } else {
            List<Square> wordSquares = tempBoard.getVerticalWord(row, col);
            if (!wordSquares.isEmpty()) {
                mainWord = Board.getWordString(wordSquares);
                formedWords.add(mainWord);
            }
        }
        System.out.println("Main word formed: " + mainWord);

        for (Point p : newTilePositions) {
            List<Square> crossingWord;
            if (move.getDirection() == Move.Direction.HORIZONTAL) {
                crossingWord = tempBoard.getVerticalWord(p.x, p.y);
            } else {
                crossingWord = tempBoard.getHorizontalWord(p.x, p.y);
            }
            if (crossingWord.size() >= 2) {
                String crossWord = Board.getWordString(crossingWord);
                if (!crossWord.equals(mainWord)) {
                    System.out.println("Crossing word formed: " + crossWord);
                    formedWords.add(crossWord);
                }
            }
        }
        return formedWords;
    }

    public boolean validateWords(Move move) {
        Board board = game.getBoard();
        Dictionary dictionary = game.getDictionary();

        List<String> formedWords = calculateFormedWords(move);
        if (formedWords.isEmpty()) {
            System.out.println("Invalid move: No valid words formed");
            return false;
        }

        Map<String, List<Square>> wordSquaresMap = new HashMap<>();
        Board tempBoard = new Board();
        for (int r = 0; r < Board.SIZE; r++) {
            for (int c = 0; c < Board.SIZE; c++) {
                Square square = board.getSquare(r, c);
                if (square.hasTile()) {
                    tempBoard.placeTile(r, c, square.getTile());
                }
            }
        }
        int currentRow = move.getStartRow();
        int currentCol = move.getStartCol();
        for (Tile tile : move.getTiles()) {
            while (currentRow < Board.SIZE && currentCol < Board.SIZE &&
                    tempBoard.getSquare(currentRow, currentCol).hasTile()) {
                if (move.getDirection() == Move.Direction.HORIZONTAL) {
                    currentCol++;
                } else {
                    currentRow++;
                }
            }
            if (currentRow < Board.SIZE && currentCol < Board.SIZE) {
                tempBoard.placeTile(currentRow, currentCol, tile);
                if (move.getDirection() == Move.Direction.HORIZONTAL) {
                    currentCol++;
                } else {
                    currentRow++;
                }
            }
        }
        for (String word : formedWords) {
            List<Square> wordSquares = findWordSquares(tempBoard, word);
            if (wordSquares.isEmpty()) {
                System.out.println("Error: Could not find squares for word: " + word);
                return false;
            }
            if (!dictionary.isValidWord(word)) {
                System.out.println("Invalid move: Word '" + word + "' is not in dictionary");
                return false;
            }
            wordSquaresMap.put(word, wordSquares);
        }
        move.setFormedWords(formedWords);
        move.setMetadata("wordSquares", wordSquaresMap);
        int score = calculateMoveScore(move, tempBoard, wordSquaresMap);
        move.setScore(score);
        return true;
    }

    private List<Square> findWordSquares(Board tempBoard, String word) {
        for (int r = 0; r < Board.SIZE; r++) {
            for (int c = 0; c < Board.SIZE; c++) {
                List<Square> horizontal = tempBoard.getHorizontalWord(r, c);
                if (!horizontal.isEmpty() && Board.getWordString(horizontal).equals(word)) {
                    return horizontal;
                }
            }
        }
        for (int r = 0; r < Board.SIZE; r++) {
            for (int c = 0; c < Board.SIZE; c++) {
                List<Square> vertical = tempBoard.getVerticalWord(r, c);
                if (!vertical.isEmpty() && Board.getWordString(vertical).equals(word)) {
                    return vertical;
                }
            }
        }
        return new ArrayList<>();
    }

    public boolean commitPlacement() {
        if (temporaryPlacements.isEmpty()) {
            return false;
        }

        Move.Direction direction = determineDirection();
        if (temporaryPlacements.size() == 1) {
            Point p = temporaryPlacements.keySet().iterator().next();
            int row = p.x;
            int col = p.y;
            Board board = game.getBoard();
            Board tempBoard = new Board();
            for (int r = 0; r < Board.SIZE; r++) {
                for (int c = 0; c < Board.SIZE; c++) {
                    if (board.getSquare(r, c).hasTile()) {
                        tempBoard.placeTile(r, c, board.getSquare(r, c).getTile());
                    }
                }
            }
            Tile placedTile = temporaryPlacements.get(p);
            tempBoard.placeTile(row, col, placedTile);
            List<Square> horizontalWord = tempBoard.getHorizontalWord(row, col);
            List<Square> verticalWord = tempBoard.getVerticalWord(row, col);
            String horizontalWordStr = horizontalWord.size() >= 2 ? Board.getWordString(horizontalWord) : "";
            String verticalWordStr = verticalWord.size() >= 2 ? Board.getWordString(verticalWord) : "";
            boolean formingHorizontalWord = horizontalWord.size() >= 2 &&
                    game.getDictionary().isValidWord(horizontalWordStr);
            boolean formingVerticalWord = verticalWord.size() >= 2 &&
                    game.getDictionary().isValidWord(verticalWordStr);
            if (formingHorizontalWord && !formingVerticalWord) {
                direction = Move.Direction.HORIZONTAL;
            } else if (formingVerticalWord && !formingHorizontalWord) {
                direction = Move.Direction.VERTICAL;
            } else if (formingHorizontalWord && formingVerticalWord) {
                direction = horizontalWord.size() >= verticalWord.size() ?
                        Move.Direction.HORIZONTAL : Move.Direction.VERTICAL;
            } else {
                return false;
            }
        }
        if (direction == null) {
            direction = Move.Direction.HORIZONTAL;
        }
        int startRow = Integer.MAX_VALUE;
        int startCol = Integer.MAX_VALUE;
        for (Point p : temporaryPlacements.keySet()) {
            startRow = Math.min(startRow, p.x);
            startCol = Math.min(startCol, p.y);
        }
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
        Move placeMove = Move.createPlaceMove(game.getCurrentPlayer(), startRow, startCol, direction);
        List<Tile> tilesToPlace = new ArrayList<>();
        if (direction == Move.Direction.HORIZONTAL) {
            Map<Integer, Tile> colToTile = new TreeMap<>();
            for (Map.Entry<Point, Tile> entry : temporaryPlacements.entrySet()) {
                Point p = entry.getKey();
                if (p.x == startRow) {
                    colToTile.put(p.y, entry.getValue());
                }
            }
            tilesToPlace.addAll(colToTile.values());
        } else {
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
        List<String> formedWords = calculateFormedWords(placeMove);
        if (formedWords.isEmpty()) {
            return false;
        }
        placeMove.setFormedWords(formedWords);
        if (!validateWords(placeMove)) {
            return false;
        }
        boolean success = makeMove(placeMove);
        if (success) {
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
            selectedTiles.remove(tile);
            selectedPositions.remove(Integer.valueOf(index));
            System.out.println("Deselected tile at index " + index + ": " + tile.getLetter());
        } else {
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
