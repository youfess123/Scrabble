package edu.leicester.scrabble.model;

public class Player {

    private final String name;
    private int score;
    private final Rack rack;
    private boolean isComputer;

    public Player(String name, boolean isComputer) {
        this.name = name;
        this.score = 0;
        this.rack= new Rack();
        this.isComputer = isComputer;
    }

    public Player(String name) {
        this(name, false);
    }
    public String getName() {
        return name;
    }

    public int getScore() {
        return score;
    }

    public void addScore(int points) {
        this.score += points;
    }

    public Rack getRack() {
        return rack;
    }

    public boolean isComputer() {
        return isComputer;
    }

    public void setComputer(boolean isComputer) {
        this.isComputer = isComputer;
    }

    public boolean isOutOfTiles() {
        return rack.isEmpty();
    }

    public int getRackValue() {
        return rack.getTotalValue();
    }

    @Override
    public String toString() {
        return name + " (Score: " + score + ")";
    }
}
