package edu.leicester.scrabble.model;

import edu.leicester.scrabble.util.ScrabbleConstants;

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
        int oldIndex = currentPlayerIndex;
        currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
        System.out.println("Player changed from index "+oldIndex+ " to "+currentPlayerIndex);
        System.out.println("Current player is now: "+getCurrentPlayer().getName());
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
        System.out.println("Executing move of type: "+ move.getType());

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
            System.out.println("Move executed successfully");
            moveHistory.add(move);

            if (checkGameOver()) {
                finaliseGameScore();
                return true;
            }

            nextPlayer();
            System.out.println("Turn passed to: " + getCurrentPlayer().getName());
        } else {
            System.out.println("Move execution failed");
        }

        return success;
    }

    private boolean executePlaceMove(Move move) {
        if (!isValidPlaceMove(move)) {
            return false;
        }

        int row = move.getStartRow();
        int col = move.getStartCol();
        Player player = move.getPlayer();
        List<Tile> tilesToPlace = move.getTiles();
        Move.Direction direction = move.getDirection();

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

        for (Tile tile : tilesToPlace) {
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

        List<String> formedWords = validateWords(tempBoard, move, newTilePositions);

        if (formedWords.isEmpty()) {
            return false;
        }

        move.setFormedWords(formedWords);

        int score = calculateMoveScore(tempBoard, move, newTilePositions);
        move.setScore(score);

        for (int i = 0; i < tilesToPlace.size(); i++) {
            Tile tile = tilesToPlace.get(i);
            Point position = (i < newTilePositions.size()) ? newTilePositions.get(i) : null;

            if (position != null) {
                // Place the tile on the board
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

    private List<String> validateWords(Board tempBoard, Move move, List<Point> newTilePositions) {
        List<String> formedWords = new ArrayList<>();

        int row = move.getStartRow();
        int col = move.getStartCol();
        Move.Direction direction = move.getDirection();

        String mainWord;
        if (direction == Move.Direction.HORIZONTAL) {
            mainWord = getWordAt(tempBoard, row, findWordStart(tempBoard, row, col, true), true);
        } else {
            mainWord = getWordAt(tempBoard, findWordStart(tempBoard, row, col, false), col, false);
        }

        if (mainWord.length() < 2) {
            System.out.println("Invalid move: Main word is too short");
            return formedWords;
        }

        if (!dictionary.isValidWord(mainWord)) {
            System.out.println("Invalid move: Main word " + mainWord + " is not in dictionary");
            return formedWords;
        }

        formedWords.add(mainWord);

        for (Point p : newTilePositions) {
            String crossWord;

            if (direction == Move.Direction.HORIZONTAL) {
                crossWord = getWordAt(tempBoard, findWordStart(tempBoard, p.x, p.y, false), p.y, false);
            } else {
                crossWord = getWordAt(tempBoard, p.x, findWordStart(tempBoard, p.x, p.y, true), true);
            }

            if (crossWord.length() >= 2) {
                if (!dictionary.isValidWord(crossWord)) {
                    System.out.println("Invalid move: Crossing word "+crossWord+" is not in dictionary");
                    return new ArrayList<>();
                }

                formedWords.add(crossWord);
            }
        }

        return formedWords;
    }

    private int findWordStart(Board board, int row, int col, boolean isHorizontal) {
        int position = isHorizontal ? col : row;

        while (position > 0) {
            int prevPos = position - 1;
            Square square = isHorizontal ? board.getSquare(row, prevPos) : board.getSquare(prevPos, col);

            if (!square.hasTile()) {
                break;
            }
            position = prevPos;
        }

        return position;
    }

    private String getWordAt(Board board, int row, int col, boolean isHorizontal) {
        StringBuilder word = new StringBuilder();

        int currentRow = row;
        int currentCol = col;

        // Collect letters until we reach an empty square or the board edge
        while (currentRow < Board.SIZE && currentCol < Board.SIZE) {
            Square square = board.getSquare(currentRow, currentCol);

            if (!square.hasTile()) {
                break;
            }

            word.append(square.getTile().getLetter());

            if (isHorizontal) {
                currentCol++;
            } else {
                currentRow++;
            }
        }

        return word.toString();
    }

    private int calculateMoveScore(Board board, Move move, List<Point> newTilePositions) {
        int totalScore = 0;
        List<String> formedWords = move.getFormedWords();

        Set<Point> usedPremiumSquares = new HashSet<>();

        for (String word : formedWords) {
            int wordScore = 0;
            int wordMultiplier = 1;

            Point wordPosition = findWordPosition(board, word);
            if (wordPosition == null) continue;

            int startRow = wordPosition.x;
            int startCol = wordPosition.y;
            boolean isHorizontal = findWordOrientation(board, startRow, startCol, word);

            int currentRow = startRow;
            int currentCol = startCol;

            for (int i = 0; i < word.length(); i++) {
                Square square = board.getSquare(currentRow, currentCol);
                Point currentPoint = new Point(currentRow, currentCol);
                boolean isNewTile = newTilePositions.contains(currentPoint);

                Tile tile = square.getTile();
                int letterValue = tile.getValue();

                if (isNewTile && !square.isSquareTypeUsed()) {
                    if (square.getSquareType() == Square.SquareType.DOUBLE_LETTER) {
                        letterValue *= 2;
                    } else if (square.getSquareType() == Square.SquareType.TRIPLE_LETTER) {
                        letterValue *= 3;
                    }

                    if (!usedPremiumSquares.contains(currentPoint)) {
                        if (square.getSquareType() == Square.SquareType.DOUBLE_WORD ||
                                square.getSquareType() == Square.SquareType.CENTER) {
                            wordMultiplier *= 2;
                            usedPremiumSquares.add(currentPoint);
                        } else if (square.getSquareType() == Square.SquareType.TRIPLE_WORD) {
                            wordMultiplier *= 3;
                            usedPremiumSquares.add(currentPoint);
                        }
                    }
                }

                wordScore += letterValue;

                if (isHorizontal) {
                    currentCol++;
                } else {
                    currentRow++;
                }
            }

            wordScore *= wordMultiplier;

            totalScore += wordScore;

            System.out.println("Word: " + word + ", Score: " + wordScore);
        }

        if (move.getTiles().size() == 7) {
            totalScore += ScrabbleConstants.BINGO_BONUS;
            System.out.println("Bingo bonus: " + ScrabbleConstants.BINGO_BONUS);
        }

        System.out.println("Total move score: " + totalScore);
        return totalScore;
    }

    private Point findWordPosition(Board board, String word) {
        // Check horizontal words
        for (int row = 0; row < Board.SIZE; row++) {
            for (int col = 0; col < Board.SIZE; col++) {
                String foundWord = getWordAt(board, row, col, true);
                if (foundWord.equals(word)) {
                    return new Point(row, col);
                }
            }
        }

        for (int col = 0; col < Board.SIZE; col++) {
            for (int row = 0; row < Board.SIZE; row++) {
                String foundWord = getWordAt(board, row, col, false);
                if (foundWord.equals(word)) {
                    return new Point(row, col);
                }
            }
        }

        return null;
    }

    private boolean findWordOrientation(Board board, int row, int col, String word) {
        String horizontalWord = getWordAt(board, row, col, true);

        return horizontalWord.equals(word);
    }

    private boolean executeExchangeMove(Move move) {
        try {
            Player player = move.getPlayer();
            List<Tile> tilesToExchange = move.getTiles();

            if (tileBag.getTileCount() < 1) {
                System.out.println("Not enough tiles in bag: " + tileBag.getTileCount());
                return false;
            }

            System.out.println("Before removal - Rack size: " + player.getRack().size());
            System.out.println("Tiles to exchange: " + tilesToExchange.size());

            StringBuilder exchangeLog = new StringBuilder("Exchanging tiles: ");
            for (Tile t : tilesToExchange) {
                exchangeLog.append(t.getLetter()).append(" ");
            }
            System.out.println(exchangeLog.toString());

            if (!player.getRack().removeTiles(tilesToExchange)) {
                System.out.println("Failed to remove tiles from rack");
                return false;
            }

            System.out.println("After removal - Rack size: " + player.getRack().size());

            int numTilesToDraw = tilesToExchange.size();
            List<Tile> newTiles = tileBag.drawTiles(numTilesToDraw);

            System.out.println("Drew " + newTiles.size() + " new tiles");

            int tilesAdded = player.getRack().addTiles(newTiles);

            System.out.println("Added " + tilesAdded + " tiles to rack");

            tileBag.returnTiles(tilesToExchange);

            System.out.println("Returned " + tilesToExchange.size() + " tiles to bag");
            System.out.println("After exchange - Rack size: " + player.getRack().size());

            consecutivePasses = 0;

            return true;
        } catch (Exception e) {
            System.err.println("Error in executeExchangeMove: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private boolean executePassMove(Move move) {
        consecutivePasses++;
        return true;
    }

    public boolean isValidPlaceMove(Move move) {
        int startRow = move.getStartRow();
        int startCol = move.getStartCol();
        Move.Direction direction = move.getDirection();
        List<Tile> tiles = move.getTiles();

        if (tiles.isEmpty()) {
            System.out.println("Invalid move: No tiles to place");
            return false;
        }

        if (startRow < 0 || startRow >= Board.SIZE || startCol < 0 || startCol >= Board.SIZE) {
            System.out.println("Invalid move: Starting position out of bounds");
            return false;
        }

        if (board.isEmpty()) {
            boolean touchesCenter = false;

            if (direction == Move.Direction.HORIZONTAL) {
                if (startRow == ScrabbleConstants.CENTER_SQUARE &&
                        startCol <= ScrabbleConstants.CENTER_SQUARE &&
                        startCol + tiles.size() > ScrabbleConstants.CENTER_SQUARE) {
                    touchesCenter = true;
                }
            } else {
                if (startCol == ScrabbleConstants.CENTER_SQUARE &&
                        startRow <= ScrabbleConstants.CENTER_SQUARE &&
                        startRow + tiles.size() > ScrabbleConstants.CENTER_SQUARE) {
                    touchesCenter = true;
                }
            }

            if (!touchesCenter) {
                System.out.println("Invalid first move: Must cover center square");
                return false;
            }

            return true;
        }

        Board tempBoard = new Board();

        for (int r = 0; r < Board.SIZE; r++) {
            for (int c = 0; c < Board.SIZE; c++) {
                if (board.getSquare(r, c).hasTile()) {
                    tempBoard.placeTile(r, c, board.getSquare(r, c).getTile());
                }
            }
        }

        int currentRow = startRow;
        int currentCol = startCol;
        boolean connectsToExisting = false;
        List<Point> newTilePositions = new ArrayList<>();

        for (Tile tile : tiles) {
            while (currentRow < Board.SIZE && currentCol < Board.SIZE &&
                    tempBoard.getSquare(currentRow, currentCol).hasTile()) {
                if (direction == Move.Direction.HORIZONTAL) {
                    currentCol++;
                } else {
                    currentRow++;
                }
            }

            if (currentRow >= Board.SIZE || currentCol >= Board.SIZE) {
                System.out.println("Invalid move: Placement extends beyond board");
                return false;
            }

            tempBoard.placeTile(currentRow, currentCol, tile);
            newTilePositions.add(new Point(currentRow, currentCol));

            if (!connectsToExisting) {
                connectsToExisting = hasAdjacentTile(currentRow, currentCol);
            }

            if (direction == Move.Direction.HORIZONTAL) {
                currentCol++;
            } else {
                currentRow++;
            }
        }

        if (!connectsToExisting) {
            List<String> formedWords = validateWords(tempBoard, move, newTilePositions);

            if (formedWords.isEmpty()) {
                System.out.println("Invalid move: No valid words formed");
                return false;
            }

            for (String word : formedWords) {
                Point wordPos = findWordPosition(tempBoard, word);
                if (wordPos == null) continue;

                boolean isHorizontal = findWordOrientation(tempBoard, wordPos.x, wordPos.y, word);
                int row = wordPos.x;
                int col = wordPos.y;

                for (int i = 0; i < word.length(); i++) {
                    Point p = new Point(row, col);

                    if (board.getSquare(row, col).hasTile() && !newTilePositions.contains(p)) {
                        connectsToExisting = true;
                        break;
                    }

                    if (isHorizontal) {
                        col++;
                    } else {
                        row++;
                    }
                }

                if (connectsToExisting) break;
            }
        }

        if (!connectsToExisting) {
            System.out.println("Invalid move: Does not connect to existing tiles");
            return false;
        }

        return true;
    }

    private boolean hasAdjacentTile(int row, int col) {
        if (row > 0 && board.getSquare(row - 1, col).hasTile()) return true;
        if (row < Board.SIZE - 1 && board.getSquare(row + 1, col).hasTile()) return true;
        if (col > 0 && board.getSquare(row, col - 1).hasTile()) return true;
        if (col < Board.SIZE - 1 && board.getSquare(row, col + 1).hasTile()) return true;

        return false;
    }

    public boolean checkGameOver() {
        for (Player player : players) {
            if (player.isOutOfTiles() && tileBag.isEmpty()) {
                gameOver = true;
                return true;
            }
        }

        if (consecutivePasses >= players.size() * 2) {
            gameOver = true;
            return true;
        }

        return false;
    }

    private void finaliseGameScore() {
        Player outPlayer = null;
        for (Player player : players) {
            if (player.isOutOfTiles()) {
                outPlayer = player;
                break;
            }
        }

        if (outPlayer != null) {
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
            for (Player player : players) {
                player.addScore(-player.getRackValue());
            }
        }
    }
}