package edu.leicester.scrabble.model;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

public class Dictionary {
    private final GADDAG gaddag;
    private final Set<String> wordSet;
    private final String dictionaryName;

    public Dictionary(String filePath) throws IOException {
        this.gaddag = new GADDAG();
        this.wordSet = new HashSet<>();
        this.dictionaryName = extractDictionaryName(filePath);

        loadFromFile(filePath);
    }

    public Dictionary(InputStream inputStream, String name) throws IOException {
        this.gaddag = new GADDAG();
        this.wordSet = new HashSet<>();
        this.dictionaryName = name;

        loadFromInputStream(inputStream);
    }

    private String extractDictionaryName(String filePath) {
        // Extract the filename without the extension
        String filename = filePath.substring(filePath.lastIndexOf('/') + 1);
        if (filename.contains(".")) {
            filename = filename.substring(0, filename.lastIndexOf('.'));
        }
        return filename;
    }

    private void loadFromFile(String filePath) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                addWord(line);
            }
        }
    }

    private void loadFromInputStream(InputStream inputStream) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                addWord(line);
            }
        }
    }

    public void addWord(String word) {
        word = word.trim().toUpperCase();

        // Skip empty words or words with non-letter characters
        if (word.isEmpty() || !word.matches("[A-Z]+")) {
            return;
        }

        wordSet.add(word);
        gaddag.insert(word);
    }

    public boolean isValidWord(String word) {
        if (word == null || word.isEmpty()) {
            return false;
        }

        word = word.trim().toUpperCase();
        return wordSet.contains(word);
    }

    public GADDAG getGaddag() {
        return gaddag;
    }

    public String getName() {
        return dictionaryName;
    }

    public int getWordCount() {
        return wordSet.size();
    }

    public boolean isValidPrefix(String prefix) {
        // This is a simplified implementation. For a more efficient version,
        // the GADDAG would need to be modified to support prefix checking.
        if (prefix == null || prefix.isEmpty()) {
            return false;
        }

        prefix = prefix.trim().toUpperCase();

        for (String word : wordSet) {
            if (word.startsWith(prefix)) {
                return true;
            }
        }

        return false;
    }

    public Set<String> getWordsFrom(String rack, char anchor, boolean left, boolean right) {
        return gaddag.getWordsFrom(rack, anchor, left, right);
    }
}
