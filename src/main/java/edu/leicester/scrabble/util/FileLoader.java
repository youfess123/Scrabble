package edu.leicester.scrabble.util;

import edu.leicester.scrabble.model.Dictionary;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class FileLoader {
    public static InputStream loadFile(String path) throws IOException {
        // First try loading as a resource
        InputStream is = FileLoader.class.getResourceAsStream(path);

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

    public static InputStream loadDictionary(String dictionaryName) throws IOException {
        // Try to load from the dictionaries folder in resources
        String path = "/dictionaries/" + dictionaryName;
        if (!dictionaryName.toLowerCase().endsWith(".txt")) {
            path += ".txt";
        }

        return loadFile(path);
    }

    public static InputStream loadDefaultDictionary() throws IOException {
        return loadFile(ScrabbleConstants.DEFAULT_DICTIONARY);
    }

    public static boolean dictionaryExists(String dictionaryName) {
        try {
            InputStream is = loadDictionary(dictionaryName);
            is.close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static String[] listAvailableDictionaries() {
        // This would ideally list all files in the dictionaries resource folder,
        // but this is not trivially possible with resources. In a real implementation,
        // this would need to be handled differently, e.g., by having a manifest
        // file listing all available dictionaries.

        // For now, return a hardcoded list of known dictionaries
        return new String[] {
                "Dictionary.txt"
        };
    }
}
