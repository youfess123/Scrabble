package edu.leicester.scrabble.model;

import java.awt.*;
import java.util.*;
import java.util.List;


public class GADDAG {
    private static final char DELIMITER = '+';

    private final Node root;

    public GADDAG() {
        this.root = new Node();
    }

    public void insert(String word) {
        word = word.toUpperCase();

        if (word.length() < 2) {
            return;
        }

        for (int i = 0; i < word.length(); i++) {
            StringBuilder sequence = new StringBuilder();

            for (int j = i; j > 0; j--) {
                sequence.append(word.charAt(j - 1));
            }

            sequence.append(DELIMITER);

            sequence.append(word.substring(i));

            insertSequence(sequence.toString());
        }

        insertSequence(DELIMITER + word);
    }

    private void insertSequence(String sequence) {
        Node current = root;

        for (int i = 0; i < sequence.length(); i++) {
            char c = sequence.charAt(i);
            current = current.getOrCreateChild(c);
        }

        current.setWord(true);
    }

    public boolean contains(String word) {
        word = word.toUpperCase();

        if (word.isEmpty()) {
            return false;
        }

        Node current = root;
        String sequence = DELIMITER + word;

        for (int i = 0; i < sequence.length(); i++) {
            current = current.getChild(sequence.charAt(i));
            if (current == null) {
                return false;
            }
        }

        return current.isWord();
    }

    public Set<String> getWordsFrom(String rack, char anchor, boolean allowLeft, boolean allowRight) {
        Set<String> words = new HashSet<>();
        StringBuilder currentWord = new StringBuilder();
        currentWord.append(anchor);

        Map<Character, Integer> rackMap = new HashMap<>();
        for (char c : rack.toUpperCase().toCharArray()) {
            rackMap.put(c, rackMap.getOrDefault(c, 0) + 1);
        }

        Node current = root.getChild(anchor);
        if (current == null) {
            return words;
        }

        dfs(current, currentWord, rackMap, words, allowLeft, allowRight, false);

        return words;
    }



    private void findSuffixes(Node node, StringBuilder wordBuilder, Map<Character, Integer> rack, Set<String> validWords) {
        if (node.isWord()) {
            validWords.add(wordBuilder.toString());
        }

        for (Map.Entry<Character, Node> entry : node.getChildren().entrySet()) {
            char letter = entry.getKey();

            if (letter == DELIMITER) continue;

            if (rack.getOrDefault(letter, 0) > 0) {
                rack.put(letter, rack.get(letter) - 1);
                wordBuilder.append(letter);

                findSuffixes(entry.getValue(), wordBuilder, rack, validWords);

                wordBuilder.deleteCharAt(wordBuilder.length() - 1);
                rack.put(letter, rack.get(letter) + 1);
            }
        }
    }

    private void findPrefixes(Node node, StringBuilder wordBuilder, Map<Character, Integer> rack, Set<String> validWords) {
        if (node.isWord()) {
            validWords.add(wordBuilder.toString());
        }

        for (Map.Entry<Character, Node> entry : node.getChildren().entrySet()) {
            char letter = entry.getKey();

            if (letter == DELIMITER) continue;

            if (rack.getOrDefault(letter, 0) > 0) {
                rack.put(letter, rack.get(letter) - 1);
                wordBuilder.insert(0, letter);

                findPrefixes(entry.getValue(), wordBuilder, rack, validWords);

                wordBuilder.deleteCharAt(0);
                rack.put(letter, rack.get(letter) + 1);
            }
        }
    }

    public Map<String, Point> findValidWordsAt(Board board, int row, int col, String rack, Move.Direction direction) {
        Map<String, Point> validWords = new HashMap<>();

        if (board.getSquare(row, col).hasTile()) {
            return validWords;
        }

        String[] partialWords = getPartialWordsAt(board, row, col, direction);
        String prefix = partialWords[0];
        String suffix = partialWords[1];

        if (prefix.isEmpty() && suffix.isEmpty() && !board.isEmpty() &&
                !hasAdjacentTiles(board, row, col)) {
            return validWords;
        }

        if (board.isEmpty() && (row != 7 || col != 7)) {
            return validWords;
        }

        for (char letter : getUniqueLetters(rack)) {
            String word = prefix + letter + suffix;

            if (word.length() >= 2 && contains(word)) {
                int startRow = direction == Move.Direction.HORIZONTAL ? row : row - prefix.length();
                int startCol = direction == Move.Direction.HORIZONTAL ? col - prefix.length() : col;

                validWords.put(word, new Point(startRow, startCol));
            }
        }

        return validWords;
    }

    private String[] getPartialWordsAt(Board board, int row, int col, Move.Direction direction) {
        StringBuilder prefix = new StringBuilder();
        StringBuilder suffix = new StringBuilder();

        if (direction == Move.Direction.HORIZONTAL) {
            int c = col - 1;
            while (c >= 0 && board.getSquare(row, c).hasTile()) {
                prefix.insert(0, board.getSquare(row, c).getTile().getLetter());
                c--;
            }

            c = col + 1;
            while (c < Board.SIZE && board.getSquare(row, c).hasTile()) {
                suffix.append(board.getSquare(row, c).getTile().getLetter());
                c++;
            }
        } else {
            int r = row - 1;
            while (r >= 0 && board.getSquare(r, col).hasTile()) {
                prefix.insert(0, board.getSquare(r, col).getTile().getLetter());
                r--;
            }

            r = row + 1;
            while (r < Board.SIZE && board.getSquare(r, col).hasTile()) {
                suffix.append(board.getSquare(r, col).getTile().getLetter());
                r++;
            }
        }

        return new String[] {prefix.toString(), suffix.toString()};
    }

    private boolean hasAdjacentTiles(Board board, int row, int col) {
        // Check all four adjacent positions
        if (row > 0 && board.getSquare(row - 1, col).hasTile()) return true;
        if (row < Board.SIZE - 1 && board.getSquare(row + 1, col).hasTile()) return true;
        if (col > 0 && board.getSquare(row, col - 1).hasTile()) return true;
        if (col < Board.SIZE - 1 && board.getSquare(row, col + 1).hasTile()) return true;

        return false;
    }

    private Set<Character> getUniqueLetters(String rack) {
        Set<Character> letters = new HashSet<>();
        for (char c : rack.toCharArray()) {
            letters.add(Character.toUpperCase(c));
        }
        return letters;
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

    public List<String> validateMove(Board board, Move move) {
        List<String> formedWords = new ArrayList<>();

        Board tempBoard = new Board();

        for (int r = 0; r < Board.SIZE; r++) {
            for (int c = 0; c < Board.SIZE; c++) {
                Square square = board.getSquare(r, c);
                if (square.hasTile()) {
                    tempBoard.placeTile(r, c, square.getTile());
                }
            }
        }

        int row = move.getStartRow();
        int col = move.getStartCol();
        Move.Direction direction = move.getDirection();

        List<Point> newTilePositions = new ArrayList<>();
        int currentRow = row;
        int currentCol = col;

        for (Tile tile : move.getTiles()) {
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

                // Move to next position
                if (direction == Move.Direction.HORIZONTAL) {
                    currentCol++;
                } else {
                    currentRow++;
                }
            }
        }

        String mainWord;
        if (direction == Move.Direction.HORIZONTAL) {
            mainWord = getWordAt(tempBoard, row, findWordStart(tempBoard, row, col, true), true);
        } else {
            mainWord = getWordAt(tempBoard, findWordStart(tempBoard, row, col, false), col, false);
        }
        // Validate the main word
        if (mainWord.length() < 2 || !contains(mainWord)) {
            return formedWords; // Invalid main word
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
                if (!contains(crossWord)) {
                    return new ArrayList<>();
                }
                formedWords.add(crossWord);
            }
        }

        return formedWords;
    }

    private String getWordAt(Board board, int row, int col, boolean isHorizontal) {
        StringBuilder word = new StringBuilder();

        int currentRow = row;
        int currentCol = col;

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


    private void dfs(Node node, StringBuilder currentWord, Map<Character, Integer> rack,
                     Set<String> words, boolean allowLeft, boolean allowRight, boolean passedDelimiter) {

        if (node.isWord() && passedDelimiter) {
            words.add(currentWord.toString());
        }

        for (Map.Entry<Character, Node> entry : node.getChildren().entrySet()) {
            char c = entry.getKey();
            Node child = entry.getValue();

            if (c == DELIMITER) {
                if (allowLeft) {
                    dfs(child, currentWord, rack, words, allowLeft, allowRight, true);
                }
            } else if (!passedDelimiter && allowLeft) {
                if (rack.getOrDefault(c, 0) > 0) {
                    rack.put(c, rack.get(c) - 1);
                    currentWord.insert(0, c);
                    dfs(child, currentWord, rack, words, allowLeft, allowRight, passedDelimiter);
                    currentWord.deleteCharAt(0);
                    rack.put(c, rack.get(c) + 1);
                }
            } else if (passedDelimiter && allowRight) {
                if (rack.getOrDefault(c, 0) > 0) {
                    rack.put(c, rack.get(c) - 1);
                    currentWord.append(c);
                    dfs(child, currentWord, rack, words, allowLeft, allowRight, passedDelimiter);
                    currentWord.deleteCharAt(currentWord.length() - 1);
                    rack.put(c, rack.get(c) + 1);
                }
            }
        }
    }

    private static class Node {
        private final Map<Character, Node> children;
        private boolean isWord;

        public Node() {
            this.children = new HashMap<>();
            this.isWord = false;
        }

        public Map<Character, Node> getChildren() {
            return children;
        }

        public Node getChild(char c) {
            return children.get(c);
        }

        public Node getOrCreateChild(char c) {
            return children.computeIfAbsent(c, k -> new Node());
        }

        public boolean isWord() {
            return isWord;
        }

        public void setWord(boolean isWord) {
            this.isWord = isWord;
        }
    }
}