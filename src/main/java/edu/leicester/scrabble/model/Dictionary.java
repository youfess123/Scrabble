package edu.leicester.scrabble.model;

import edu.leicester.scrabble.util.ScrabbleConstants;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class Dictionary {
    private final GADDAG gaddag;
    private final Set<String> wordSet;
    private final String dictionaryName;
    private final Map<String, Integer> wordScoreCache;

    private static final String DEFAULT_DICTIONARY = "/dictionaries/Dictionary.txt";

    public Dictionary(String filePath) throws IOException {
        this.gaddag = new GADDAG();
        this.wordSet = new HashSet<>();
        this.wordScoreCache = new HashMap<>();
        this.dictionaryName = extractDictionaryName(filePath);
        loadFromFile(filePath);
        System.out.println("Loaded dictionary '" + dictionaryName + "' with " + wordSet.size() + " words");
    }

    public Dictionary(InputStream inputStream, String name) throws IOException {
        this.gaddag = new GADDAG();
        this.wordSet = new HashSet<>();
        this.wordScoreCache = new HashMap<>();
        this.dictionaryName = name;
        loadFromInputStream(inputStream);
        System.out.println("Loaded dictionary '" + dictionaryName + "' with " + wordSet.size() + " words");
    }

    public static InputStream loadDefaultDictionary() throws IOException {
        return loadFile(ScrabbleConstants.DEFAULT_DICTIONARY);
    }

    public static InputStream loadFile(String path) throws IOException {
        InputStream is = Dictionary.class.getResourceAsStream(path);

        // If not found as a resource, try as an external file
        if (is == null) {
            File file = new File(path);
            if (file.exists() && file.isFile()) {
                is = new FileInputStream(file);
            } else {
                throw new IOException("File not found: " + path);
            }
        }

        return is;
    }

    private String extractDictionaryName(String filePath) {
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

    public String getName() {
        return dictionaryName;
    }
}
