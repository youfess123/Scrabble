package edu.leicester.scrabble.util;

public class ScrabbleConstants {
    public static final int BOARD_SIZE = 15;
    public static final int CENTER_SQUARE = 7; // 0-indexed, middle of a 15x15 board

    public static final int RACK_CAPACITY = 7;

    public static final int BINGO_BONUS = 50; // Bonus for using all 7 tiles

    public static final int MIN_PLAYERS = 2;
    public static final int MAX_PLAYERS = 4;
    public static final int CONSECUTIVE_PASSES_TO_END = 6; // 3 rounds of all players passing

    public static final String DEFAULT_DICTIONARY = "src/main/resources/edu/leicester/scrabble/Dictionary.txt";

    public static final int AI_EASY = 1;
    public static final int AI_MEDIUM = 2;
    public static final int AI_HARD = 3;

    public static final int SQUARE_SIZE = 40;
    public static final int TILE_SIZE = 36;

}
