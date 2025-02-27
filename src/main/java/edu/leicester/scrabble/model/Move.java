package edu.leicester.scrabble.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Move {

    public enum Type {
        PLACE,
        EXCHANGE, //swap tiles out in bag
        PASS
    }

    public enum Direction {
        HORIZONTAL,
        VERTICAL
    }

    private final Player player;
    private final Type type;
    private Direction direction;
    private int startRow;
    private int startCol;
    private final List<Tile> tiles;
    private final List<Character> placedLetters;
    private int score;
    private List<String> formedWords;
    private Map<String, Object> metadata;

    public Move(Player player, Type type) {
        this.player = player;
        this.type = type;
        this.tiles = new ArrayList<>();
        this.placedLetters = new ArrayList<>();
        this.formedWords = new ArrayList<>();
        this.score = 0;
        this.metadata = new HashMap<>();
    }

    public static Move createPlaceMove(Player player, int startRow, int startCol, Direction direction) {
        Move move = new Move(player, Type.PLACE);
        move.startRow = startRow;
        move.startCol = startCol;
        move.direction = direction;
        return move;
    }

    public static Move createExchangeMove(Player player, List<Tile> tilesToExchange) {
        Move move = new Move(player, Type.EXCHANGE);
        move.tiles.addAll(tilesToExchange);
        return move;
    }

    public static Move createPassMove(Player player) {
        return new Move(player, Type.PASS);
    }

    public Player getPlayer() {
        return player;
    }

    public Type getType() {
        return type;
    }

    public Direction getDirection() {
        return direction;
    }

    public int getStartRow() {
        return startRow;
    }

    public int getStartCol() {
        return startCol;
    }

    public List<Tile> getTiles() {
        return new ArrayList<>(tiles);
    }

    public void addTile(Tile tile) {
        tiles.add(tile);
    }

    public void addTiles(List<Tile> tilesToAdd) {
        tiles.addAll(tilesToAdd);
    }

    public void addPlacedLetter(char letter) {
        placedLetters.add(letter);
    }

    public List<Character> getPlacedLetters() {
        return new ArrayList<>(placedLetters);
    }

    public void setScore(int score) {
        this.score = score;
    }

    public int getScore() {
        return score;
    }

    public void addFormedWord(String word) {
        formedWords.add(word);
    }

    public void setFormedWords(List<String> words) {
        this.formedWords = new ArrayList<>(words);
    }

    public List<String> getFormedWords() {
        return new ArrayList<>(formedWords);
    }

    public boolean isBlankMove() {
        return type == Type.PASS || tiles.isEmpty();
    }


    public void setMetadata(String key, Object value) {
        metadata.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T getMetadata(String key) {
        return (T) metadata.get(key);
    }


    public boolean hasMetadata(String key) {
        return metadata.containsKey(key);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(player.getName()).append(": ");

        switch (type) {
            case PLACE:
                sb.append("Placed ");
                for (int i = 0; i < tiles.size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(tiles.get(i).getLetter());
                }
                sb.append(" at (").append(startRow + 1).append(", ").append(startCol + 1).append(")");
                sb.append(" ").append(direction == Direction.HORIZONTAL ? "horizontally" : "vertically");
                if (!formedWords.isEmpty()) {
                    sb.append(" forming: ");
                    for (int i = 0; i < formedWords.size(); i++) {
                        if (i > 0) sb.append(", ");
                        sb.append(formedWords.get(i));
                    }
                }
                sb.append(" for ").append(score).append(" points");
                break;

            case EXCHANGE:
                sb.append("Exchanged ").append(tiles.size()).append(" tiles");
                break;

            case PASS:
                sb.append("Passed");
                break;
        }

        return sb.toString();
    }
}