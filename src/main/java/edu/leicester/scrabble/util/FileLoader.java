package edu.leicester.scrabble.util;


import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class FileLoader {
    public static InputStream loadFile(String path) throws IOException {
        InputStream is = FileLoader.class.getResourceAsStream(path);

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

    public static InputStream loadDefaultDictionary() throws IOException {
        return loadFile(ScrabbleConstants.DEFAULT_DICTIONARY);
    }
}