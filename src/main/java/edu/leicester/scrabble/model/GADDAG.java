package edu.leicester.scrabble.model;

import java.awt.*;
import java.util.*;
import java.util.List;


public class GADDAG {
    // Delimiter character used in GADDAG to mark the transition from reversed prefix to suffix
    private static final char DELIMITER = '+';

    // Root node of the GADDAG trie
    private final Node root;

    public GADDAG() {
        this.root = new Node();
    }

    public void insert(String word) {
        word = word.toUpperCase();

        if (word.length() < 2) {
            // Only insert words with at least 2 letters (Scrabble rule)
            return;
        }

        // For each possible starting position in the word
        for (int i = 0; i < word.length(); i++) {
            // Build the sequence to insert into the GADDAG
            StringBuilder sequence = new StringBuilder();

            // Add the reverse of the prefix
            for (int j = i; j > 0; j--) {
                sequence.append(word.charAt(j - 1));
            }

            // Add the delimiter
            sequence.append(DELIMITER);

            // Add the suffix
            sequence.append(word.substring(i));

            // Insert the sequence into the trie
            insertSequence(sequence.toString());
        }

        // Also insert the word with just the delimiter at the start
        // This handles the case of playing a word from scratch
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

        // The simplest way to check is to see if the direct sequence exists
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

        // Convert rack to a map of letter -> count for efficient lookup
        Map<Character, Integer> rackMap = new HashMap<>();
        for (char c : rack.toUpperCase().toCharArray()) {
            rackMap.put(c, rackMap.getOrDefault(c, 0) + 1);
        }

        // Start traversal from the node corresponding to the anchor letter
        Node current = root.getChild(anchor);
        if (current == null) {
            return words;
        }

        // Perform depth-first search
        dfs(current, currentWord, rackMap, words, allowLeft, allowRight, false);

        return words;
    }

    public Set<String> getWordsFromPartial(String partialWord, String rack, boolean isPrefix) {
        Set<String> validWords = new HashSet<>();

        if (partialWord == null || partialWord.isEmpty() || rack == null) {
            return validWords;
        }

        partialWord = partialWord.toUpperCase();
        rack = rack.toUpperCase();

        // Convert rack to a map for easier tile management
        Map<Character, Integer> rackMap = new HashMap<>();
        for (char c : rack.toCharArray()) {
            rackMap.put(c, rackMap.getOrDefault(c, 0) + 1);
        }

        // Start node traversal based on whether partial word is prefix or suffix
        if (isPrefix) {
            // If partial word is a prefix, start from the delimiter
            String path = partialWord + DELIMITER;
            Node current = root;

            // Traverse to the node representing the partial word + delimiter
            for (char c : path.toCharArray()) {
                current = current.getChild(c);
                if (current == null) {
                    return validWords; // Partial word not found in GADDAG
                }
            }

            // From this node, find all valid suffixes using DFS
            StringBuilder wordBuilder = new StringBuilder(partialWord);
            findSuffixes(current, wordBuilder, rackMap, validWords);
        } else {
            // If partial word is a suffix, we need to find all valid prefixes
            // Reverse the partial word for GADDAG lookup
            StringBuilder reversedPartial = new StringBuilder(partialWord).reverse();
            String path = reversedPartial.toString() + DELIMITER;

            Node current = root;

            // Traverse to the node representing the reversed partial word + delimiter
            for (char c : path.toCharArray()) {
                current = current.getChild(c);
                if (current == null) {
                    return validWords; // Partial word not found in GADDAG
                }
            }

            // From this node, find all valid prefixes using DFS
            StringBuilder wordBuilder = new StringBuilder(partialWord);
            findPrefixes(current, wordBuilder, rackMap, validWords);
        }

        return validWords;
    }


    private void findSuffixes(Node node, StringBuilder wordBuilder, Map<Character, Integer> rack, Set<String> validWords) {
        // If this node marks a complete word, add it
        if (node.isWord()) {
            validWords.add(wordBuilder.toString());
        }

        // Try each child node (potential next letter)
        for (Map.Entry<Character, Node> entry : node.getChildren().entrySet()) {
            char letter = entry.getKey();

            // Skip the delimiter
            if (letter == DELIMITER) continue;

            // Check if we have this letter in our rack
            if (rack.getOrDefault(letter, 0) > 0) {
                // Use this letter
                rack.put(letter, rack.get(letter) - 1);
                wordBuilder.append(letter);

                // Recursively find more suffixes
                findSuffixes(entry.getValue(), wordBuilder, rack, validWords);

                // Backtrack
                wordBuilder.deleteCharAt(wordBuilder.length() - 1);
                rack.put(letter, rack.get(letter) + 1);
            }
        }
    }

    private void findPrefixes(Node node, StringBuilder wordBuilder, Map<Character, Integer> rack, Set<String> validWords) {
        // If this node marks a complete word, add it
        if (node.isWord()) {
            validWords.add(wordBuilder.toString());
        }

        // Try each child node (potential next letter)
        for (Map.Entry<Character, Node> entry : node.getChildren().entrySet()) {
            char letter = entry.getKey();

            // Skip the delimiter
            if (letter == DELIMITER) continue;

            // Check if we have this letter in our rack
            if (rack.getOrDefault(letter, 0) > 0) {
                // Use this letter
                rack.put(letter, rack.get(letter) - 1);
                wordBuilder.insert(0, letter);  // Add at beginning for prefixes

                // Recursively find more prefixes
                findPrefixes(entry.getValue(), wordBuilder, rack, validWords);

                // Backtrack
                wordBuilder.deleteCharAt(0);
                rack.put(letter, rack.get(letter) + 1);
            }
        }
    }

    public Map<String, Point> findValidWordsAt(Board board, int row, int col, String rack, Move.Direction direction) {
        Map<String, Point> validWords = new HashMap<>();

        // If the position already has a tile, it's not a valid placement
        if (board.getSquare(row, col).hasTile()) {
            return validWords;
        }

        // Get the partial words already on the board (if any)
        String[] partialWords = getPartialWordsAt(board, row, col, direction);
        String prefix = partialWords[0];
        String suffix = partialWords[1];

        // If there's no adjacent tiles, but board is not empty,
        // this placement wouldn't connect to existing words
        if (prefix.isEmpty() && suffix.isEmpty() && !board.isEmpty() &&
                !hasAdjacentTiles(board, row, col)) {
            return validWords;
        }

        // If board is empty, only allow center square placement
        if (board.isEmpty() && (row != 7 || col != 7)) {
            return validWords;
        }

        // Get all possible letters from rack that could be placed here
        for (char letter : getUniqueLetters(rack)) {
            // Form the word by combining prefix + letter + suffix
            String word = prefix + letter + suffix;

            // Only consider words with at least 2 letters
            if (word.length() >= 2 && contains(word)) {
                // Calculate the starting position of the word
                int startRow = direction == Move.Direction.HORIZONTAL ? row : row - prefix.length();
                int startCol = direction == Move.Direction.HORIZONTAL ? col - prefix.length() : col;

                // Add to valid words if it doesn't already exist
                validWords.put(word, new Point(startRow, startCol));
            }
        }

        return validWords;
    }

    private String[] getPartialWordsAt(Board board, int row, int col, Move.Direction direction) {
        StringBuilder prefix = new StringBuilder();
        StringBuilder suffix = new StringBuilder();

        if (direction == Move.Direction.HORIZONTAL) {
            // Get prefix (letters to the left)
            int c = col - 1;
            while (c >= 0 && board.getSquare(row, c).hasTile()) {
                prefix.insert(0, board.getSquare(row, c).getTile().getLetter());
                c--;
            }

            // Get suffix (letters to the right)
            c = col + 1;
            while (c < Board.SIZE && board.getSquare(row, c).hasTile()) {
                suffix.append(board.getSquare(row, c).getTile().getLetter());
                c++;
            }
        } else {  // VERTICAL
            // Get prefix (letters above)
            int r = row - 1;
            while (r >= 0 && board.getSquare(r, col).hasTile()) {
                prefix.insert(0, board.getSquare(r, col).getTile().getLetter());
                r--;
            }

            // Get suffix (letters below)
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

    public List<String> validateMove(Board board, Move move) {
        List<String> formedWords = new ArrayList<>();

        // Create a temporary board with the move applied
        Board tempBoard = new Board();

        // Copy existing board state
        for (int r = 0; r < Board.SIZE; r++) {
            for (int c = 0; c < Board.SIZE; c++) {
                Square square = board.getSquare(r, c);
                if (square.hasTile()) {
                    tempBoard.placeTile(r, c, square.getTile());
                }
            }
        }

        // Apply the move to the temporary board
        int row = move.getStartRow();
        int col = move.getStartCol();
        Move.Direction direction = move.getDirection();

        // Find squares where new tiles will be placed
        List<Point> newTilePositions = new ArrayList<>();
        int currentRow = row;
        int currentCol = col;

        for (Tile tile : move.getTiles()) {
            // Skip positions that already have tiles
            while (currentRow < Board.SIZE && currentCol < Board.SIZE &&
                    tempBoard.getSquare(currentRow, currentCol).hasTile()) {
                if (direction == Move.Direction.HORIZONTAL) {
                    currentCol++;
                } else {
                    currentRow++;
                }
            }

            // Place the tile on the temp board and record position
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

        // Get the main word formed by the move
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

        // Check for perpendicular words formed at each new tile position
        for (Point p : newTilePositions) {
            String crossWord;
            if (direction == Move.Direction.HORIZONTAL) {
                // Check for vertical cross word
                crossWord = getWordAt(tempBoard, findWordStart(tempBoard, p.x, p.y, false), p.y, false);
            } else {
                // Check for horizontal cross word
                crossWord = getWordAt(tempBoard, p.x, findWordStart(tempBoard, p.x, p.y, true), true);
            }

            // Add valid cross words
            if (crossWord.length() >= 2) {
                if (!contains(crossWord)) {
                    return new ArrayList<>(); // Invalid cross word, whole move is invalid
                }
                formedWords.add(crossWord);
            }
        }

        return formedWords;
    }

    private int findWordStart(Board board, int row, int col, boolean isHorizontal) {
        int position = isHorizontal ? col : row;

        // Look backwards until we find an empty square or the board edge
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


    private void dfs(Node node, StringBuilder currentWord, Map<Character, Integer> rack,
                     Set<String> words, boolean allowLeft, boolean allowRight, boolean passedDelimiter) {

        // If this node marks the end of a word and we've passed the delimiter, add it
        if (node.isWord() && passedDelimiter) {
            words.add(currentWord.toString());
        }

        // Try each child node
        for (Map.Entry<Character, Node> entry : node.getChildren().entrySet()) {
            char c = entry.getKey();
            Node child = entry.getValue();

            if (c == DELIMITER) {
                // We're switching from left to right extension
                if (allowLeft) {
                    dfs(child, currentWord, rack, words, allowLeft, allowRight, true);
                }
            } else if (!passedDelimiter && allowLeft) {
                // Extending to the left (before delimiter)
                if (rack.getOrDefault(c, 0) > 0) {
                    rack.put(c, rack.get(c) - 1);
                    currentWord.insert(0, c);
                    dfs(child, currentWord, rack, words, allowLeft, allowRight, passedDelimiter);
                    currentWord.deleteCharAt(0);
                    rack.put(c, rack.get(c) + 1);
                }
            } else if (passedDelimiter && allowRight) {
                // Extending to the right (after delimiter)
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