package edu.leicester.scrabble.model;

import org.w3c.dom.Node;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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

    public Set<String> getWordsFrom(String rack, char anchor, boolean left, boolean right) {
        Set<String> words = new HashSet<>();
        StringBuilder currentWord = new StringBuilder();
        currentWord.append(anchor);

        // Convert rack to a map of letter -> count
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
        dfs(current, currentWord, rackMap, words, left, right, false);

        return words;
    }

    private void dfs(Node node, StringBuilder currentWord, Map<Character, Integer> rack,
                     Set<String> words, boolean left, boolean right, boolean passedDelimiter) {

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
                if (left) {
                    dfs(child, currentWord, rack, words, left, right, true);
                }
            } else if (!passedDelimiter && left) {
                // Extending to the left (before delimiter)
                if (rack.getOrDefault(c, 0) > 0) {
                    rack.put(c, rack.get(c) - 1);
                    currentWord.insert(0, c);
                    dfs(child, currentWord, rack, words, left, right, passedDelimiter);
                    currentWord.deleteCharAt(0);
                    rack.put(c, rack.get(c) + 1);
                }
            } else if (passedDelimiter && right) {
                // Extending to the right (after delimiter)
                if (rack.getOrDefault(c, 0) > 0) {
                    rack.put(c, rack.get(c) - 1);
                    currentWord.append(c);
                    dfs(child, currentWord, rack, words, left, right, passedDelimiter);
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
