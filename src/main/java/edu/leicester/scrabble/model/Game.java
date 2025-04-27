package edu.leicester.scrabble.model;

import edu.leicester.scrabble.util.*;
import java.awt.Point;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

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

        for (Player player : players) {
            fillRack(player);
        }

        currentPlayerIndex = (int) (Math.random() * players.size());
        gameOver = false;
        consecutivePasses = 0;
        moveHistory.clear();
    }

    public void fillRack(Player player) {
        Rack rack = player.getRack();
        int tilesToDraw = rack.getEmptySlots();

        if (tilesToDraw == 0) {
            return;
        }

        List<Tile> drawnTiles = tileBag.drawTiles(tilesToDraw);
        rack.addTiles(drawnTiles);
    }

    public Player getCurrentPlayer() {
        return players.get(currentPlayerIndex);
    }

    public void nextPlayer() {
        currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
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

        try {
            success = switch (move.getType()) {
                case PLACE -> executePlaceMove(move);
                case EXCHANGE -> executeExchangeMove(move);
                case PASS -> executePassMove(move);
            };
        } catch (Exception e) {
            System.err.println("Error executing move: " + e.getMessage());
            e.printStackTrace();
            return false;
        }

        if (success) {
            moveHistory.add(move);

            if (checkGameOver()) {
                finalizeGameScore();
                return true;
            }

            nextPlayer();
        }

        return success;
    }

    private boolean executePlaceMove(Move move) {
        // Validate the move
        if (!WordValidator.isValidPlaceMove(move, board, dictionary)) {
            return false;
        }

        Player player = move.getPlayer();
        Board tempBoard = BoardUtils.copyBoard(board);
        List<Point> newTilePositions = new ArrayList<>();

        // Place tiles on temp board to validate words and calculate score
        placeTilesOnBoard(tempBoard, move, newTilePositions);

        // Validate words formed
        List<String> formedWords = WordValidator.validateWords(tempBoard, move, newTilePositions, dictionary);

        if (formedWords.isEmpty()) {
            return false;
        }

        move.setFormedWords(formedWords);

        // Calculate score
        Set<Point> newPositionsSet = new HashSet<>(newTilePositions);
        int score = ScoreCalculator.calculateMoveScore(move, tempBoard, formedWords, newPositionsSet);
        move.setScore(score);

        // Apply the move to the actual board
        for (int i = 0; i < move.getTiles().size(); i++) {
            Tile tile = move.getTiles().get(i);
            if (i < newTilePositions.size()) {
                Point position = newTilePositions.get(i);
                board.placeTile(position.x, position.y, tile);
                board.getSquare(position.x, position.y).useSquareType();
                player.getRack().removeTile(tile);
            }
        }

        player.addScore(score);
        consecutivePasses = 0;
        fillRack(player);

        if (player.getRack().isEmpty() && tileBag.isEmpty()) {
            player.addScore(EMPTY_RACK_BONUS);
        }

        return true;
    }

    private void placeTilesOnBoard(Board tempBoard, Move move, List<Point> newTilePositions) {
        int row = move.getStartRow();
        int col = move.getStartCol();
        Move.Direction direction = move.getDirection();

        int currentRow = row;
        int currentCol = col;

        for (Tile tile : move.getTiles()) {
            // Skip over existing tiles
            while (currentRow < Board.SIZE && currentCol < Board.SIZE &&
                    tempBoard.getSquare(currentRow, currentCol).hasTile()) {
                if (direction == Move.Direction.HORIZONTAL) {
                    currentCol++;
                } else {
                    currentRow++;
                }
            }

            if (currentRow < Board.SIZE && currentCol < Board.SIZE) {
                tempBoard.placeTile(currentRow, currentCol, tile);
                newTilePositions.add(new Point(currentRow, currentCol));

                if (direction == Move.Direction.HORIZONTAL) {
                    currentCol++;
                } else {
                    currentRow++;
                }
            }
        }
    }

    private boolean executeExchangeMove(Move move) {
        Player player = move.getPlayer();
        List<Tile> tilesToExchange = move.getTiles();

        if (tileBag.getTileCount() < 1) {
            System.out.println("Not enough tiles in bag for exchange");
            return false;
        }

        if (!player.getRack().removeTiles(tilesToExchange)) {
            System.out.println("Failed to remove tiles from rack");
            return false;
        }

        int numTilesToDraw = tilesToExchange.size();
        List<Tile> newTiles = tileBag.drawTiles(numTilesToDraw);
        player.getRack().addTiles(newTiles);
        tileBag.returnTiles(tilesToExchange);

        consecutivePasses = 0;
        return true;
    }

    private boolean executePassMove(Move move) {
        consecutivePasses++;
        return true;
    }

    public boolean checkGameOver() {
        // Check if any player is out of tiles
        for (Player player : players) {
            if (player.isOutOfTiles() && tileBag.isEmpty()) {
                gameOver = true;
                return true;
            }
        }

        // Check for too many consecutive passes
        if (consecutivePasses >= players.size() * 2) {
            gameOver = true;
            return true;
        }

        return false;
    }

    private void finalizeGameScore() {
        Player outPlayer = null;

        // Find player who went out (if any)
        for (Player player : players) {
            if (player.isOutOfTiles()) {
                outPlayer = player;
                break;
            }
        }

        if (outPlayer != null) {
            // Player who went out gets points from other players' racks
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
            // Game ended due to passes - everyone loses points for tiles in rack
            for (Player player : players) {
                player.addScore(-player.getRackValue());
            }
        }
    }
}