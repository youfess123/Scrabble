package edu.leicester.scrabble.model;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

        // Fill each player's rack
        for (Player player : players) {
            fillRack(player);
        }

        // Randomize starting player
        currentPlayerIndex = (int) (Math.random() * players.size());
        gameOver = false;
        consecutivePasses = 0;
        moveHistory.clear();
    }

    public int fillRack(Player player) {
        Rack rack = player.getRack();
        int tilesToDraw = rack.getEmptySlots();

        if (tilesToDraw == 0) {
            return 0;
        }

        List<Tile> drawnTiles = tileBag.drawTiles(tilesToDraw);
        return rack.addTiles(drawnTiles);
    }

    public Player getCurrentPlayer() {
        return players.get(currentPlayerIndex);
    }

    public void nextPlayer() {
        currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
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
            return false;
        }

        if (move.getPlayer() != getCurrentPlayer()) {
            return false;
        }

        boolean success = false;

        switch (move.getType()) {
            case PLACE:
                success = executePlaceMove(move);
                break;

            case EXCHANGE:
                success = executeExchangeMove(move);
                break;

            case PASS:
                success = executePassMove(move);
                break;
        }

        if (success) {
            moveHistory.add(move);

            // Check if the game is over
            if (checkGameOver()) {
                finaliseGameScore();
                return true;
            }

            nextPlayer();
        }

        return success;
    }

    private boolean executePlaceMove(Move move) {
        // Verify the move is valid
        if (!isValidPlaceMove(move)) {
            return false;
        }

        // Apply the move to the board
        int row = move.getStartRow();
        int col = move.getStartCol();
        Player player = move.getPlayer();
        List<Tile> tilesToPlace = move.getTiles();

        // Place the tiles on the board
        for (Tile tile : tilesToPlace) {
            if (move.getDirection() == Move.Direction.HORIZONTAL) {
                while (board.getSquare(row, col).hasTile()) {
                    col++;
                }
                board.placeTile(row, col, tile);
                col++;
            } else {
                while (board.getSquare(row, col).hasTile()) {
                    row++;
                }
                board.placeTile(row, col, tile);
                row++;
            }

            // Remove the tile from the player's rack
            player.getRack().removeTile(tile);
        }

        // Mark premiums as used for all squares in formed words
        List<String> formedWords = new ArrayList<>(move.getFormedWords());
        for (String word : formedWords) {
            for (Square square : getSquaresForWord(word)) {
                square.useSquareType();
            }
        }

        // Add the score to the player
        player.addScore(move.getScore());

        // Reset consecutive passes count
        consecutivePasses = 0;

        // Fill the player's rack
        fillRack(player);

        // Check if the player used all tiles for a bonus
        if (player.getRack().isEmpty() && tileBag.isEmpty()) {
            player.addScore(EMPTY_RACK_BONUS);
        }

        return true;
    }

    private List<Square> getSquaresForWord(String word) {
        // This would need to be implemented based on how words are tracked
        // For now, return an empty list
        return new ArrayList<>();
    }

    private boolean executeExchangeMove(Move move) {
        Player player = move.getPlayer();
        List<Tile> tilesToExchange = move.getTiles();

        // Check if there are enough tiles in the bag
        if (tileBag.getTileCount() < 7) {
            return false;
        }

        // Remove the tiles from the player's rack
        if (!player.getRack().removeTiles(tilesToExchange)) {
            return false;
        }

        // Draw new tiles first before returning the old ones to the bag
        List<Tile> newTiles = tileBag.drawTiles(tilesToExchange.size());

        // Add the new tiles to the player's rack
        player.getRack().addTiles(newTiles);

        // Return the exchanged tiles to the bag and shuffle
        tileBag.returnTiles(tilesToExchange);

        // Reset consecutive passes count
        consecutivePasses = 0;

        return true;
    }

    private boolean executePassMove(Move move) {
        consecutivePasses++;
        return true;
    }

    public boolean isValidPlaceMove(Move move) {
        // This is a placeholder for the complete validation logic
        // A real implementation would check:
        // 1. All words formed are valid
        // 2. The placement is legal (connected to existing letters, etc.)
        // 3. The player has the tiles
        // 4. First move covers the center square
        return true;
    }

    public boolean checkGameOver() {
        // Check if any player has no tiles and the bag is empty
        for (Player player : players) {
            if (player.isOutOfTiles() && tileBag.isEmpty()) {
                gameOver = true;
                return true;
            }
        }

        // Check if there have been too many consecutive passes
        if (consecutivePasses >= players.size() * 2) {
            gameOver = true;
            return true;
        }

        return false;
    }

    private void finaliseGameScore() {
        // Find the player who went out (if any)
        Player outPlayer = null;
        for (Player player : players) {
            if (player.isOutOfTiles()) {
                outPlayer = player;
                break;
            }
        }

        if (outPlayer != null) {
            // Winner gets other players' tile values
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
            // No player went out, subtract each player's remaining tile values
            for (Player player : players) {
                player.addScore(-player.getRackValue());
            }
        }
    }
}
