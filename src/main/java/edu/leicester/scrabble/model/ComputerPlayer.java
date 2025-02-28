package edu.leicester.scrabble.model;

import edu.leicester.scrabble.util.ScrabbleConstants;
import java.awt.Point;
import java.util.*;
import java.util.List;

public class ComputerPlayer {

    private final Player player;
    private final Random random;
    private int difficultyLevel;

    public ComputerPlayer(Player player, int difficultyLevel) {
        this.player = player;
        this.random = new Random();
        this.difficultyLevel = Math.max(1, Math.min(3, difficultyLevel));
        player.setComputer(true);
    }

    public Player getPlayer() {
        return player;
    }


    public Move generateMove(Game game) {
        try {
            System.out.println("Computer player generating move at difficulty " + difficultyLevel);
            if (player.getRack().size() == 0) {
                System.out.println("Computer has no tiles, passing");
                return Move.createPassMove(player);
            }
            List<Move> possibleMoves = new ArrayList<>();
            try {
                possibleMoves = findPossibleMoves(game);
                System.out.println("Found " + possibleMoves.size() + " possible moves");
            } catch (Exception e) {
                System.err.println("Error finding possible moves: " + e.getMessage());
                e.printStackTrace();
                return generateFallbackMove(game);
            }
            if (possibleMoves.isEmpty()) {
                System.out.println("Computer: No possible word placements found, trying fallback");
                return generateFallbackMove(game);
            }
            possibleMoves.sort(Comparator.comparing(Move::getScore).reversed());
            Move selectedMove;
            switch (difficultyLevel) {
                case 1:
                    selectedMove = possibleMoves.get(random.nextInt(possibleMoves.size()));
                    break;
                case 2:
                    int mediumCutoff = Math.max(1, possibleMoves.size() / 2);
                    selectedMove = possibleMoves.get(random.nextInt(mediumCutoff));
                    break;
                case 3:
                    int hardCutoff = Math.min(3, possibleMoves.size());
                    selectedMove = possibleMoves.get(random.nextInt(hardCutoff));
                    break;
                default:
                    int defaultCutoff = Math.max(1, possibleMoves.size() / 2);
                    selectedMove = possibleMoves.get(random.nextInt(defaultCutoff));
            }
            System.out.println("Computer selected move with score: " + selectedMove.getScore());
            System.out.println("Words formed: " + String.join(", ", selectedMove.getFormedWords()));
            return selectedMove;
        } catch (Exception e) {
            System.err.println("Error generating computer move: " + e.getMessage());
            e.printStackTrace();
            return Move.createPassMove(player);
        }
    }

    private Move generateFallbackMove(Game game) {
        try {
            if (game.getTileBag().getTileCount() >= 7) {
                System.out.println("Computer: Generating exchange move");
                List<Tile> tilesToExchange = selectOptimalTilesToExchange(game);
                if (!tilesToExchange.isEmpty()) {
                    System.out.println("Computer exchanging " + tilesToExchange.size() + " tiles");
                    System.out.print("Exchanging tiles: ");
                    for (Tile t : tilesToExchange) {
                        System.out.print(t.getLetter() + " ");
                    }
                    System.out.println();
                    return Move.createExchangeMove(player, tilesToExchange);
                }
            }
            System.out.println("Computer: Generating pass move");
            return Move.createPassMove(player);
        } catch (Exception e) {
            System.err.println("Error generating fallback move: " + e.getMessage());
            e.printStackTrace();
            return Move.createPassMove(player);
        }
    }

    private List<Tile> selectOptimalTilesToExchange(Game game) {
        Rack rack = player.getRack();
        List<Tile> availableTiles = new ArrayList<>(rack.getTiles());
        List<Tile> tilesToExchange = new ArrayList<>();
        Map<Tile, Double> tileScores = new HashMap<>();
        Map<Character, Integer> letterCounts = new HashMap<>();
        for (Tile tile : availableTiles) {
            char letter = tile.getLetter();
            letterCounts.put(letter, letterCounts.getOrDefault(letter, 0) + 1);
        }
        for (Tile tile : availableTiles) {
            char letter = tile.getLetter();
            double score = 0;
            if (tile.getValue() >= 8) {
                score -= 10;
            } else if (tile.getValue() >= 4) {
                score -= 5;
            }
            if (!isVowel(letter) && letterCounts.get(letter) > 2) {
                score -= 8;
            }
            if (isVowel(letter)) {
                int vowelCount = countVowels(availableTiles);
                if (vowelCount <= 2) {
                    score += 10;
                } else if (vowelCount > 3) {
                    score -= 5;
                }
            }
            if (letter == 'Q' || letter == 'Z' || letter == 'X' || letter == 'J') {
                score -= 7;
            }
            tileScores.put(tile, score);
        }
        availableTiles.sort(Comparator.comparing(tile -> tileScores.getOrDefault(tile, 0.0)));
        int numToExchange;
        switch (difficultyLevel) {
            case 1:
                numToExchange = Math.min(4, availableTiles.size());
                break;
            case 2:
                numToExchange = Math.min(3, availableTiles.size());
                break;
            case 3:
                numToExchange = Math.min(2, availableTiles.size());
                break;
            default:
                numToExchange = Math.min(3, availableTiles.size());
        }
        for (int i = 0; i < numToExchange; i++) {
            if (i < availableTiles.size() && tileScores.getOrDefault(availableTiles.get(i), 0.0) < 0) {
                tilesToExchange.add(availableTiles.get(i));
            }
        }
        return tilesToExchange;
    }

    private List<Move> findPossibleMoves(Game game) {
        List<Move> possibleMoves = new ArrayList<>();
        Board board = game.getBoard();
        Dictionary dictionary = game.getDictionary();
        Rack rack = player.getRack();
        String rackLetters = getTilesAsString(rack.getTiles());
        if (board.isEmpty()) {
            findMovesForEmptyBoard(game, possibleMoves);
            return possibleMoves;
        }
        List<Point> anchorPoints = findAnchorPoints(board);//anchor points are just empty square next to a tile
        System.out.println("Found " + anchorPoints.size() + " anchor points");
        for (Point anchor : anchorPoints) {
            int row = anchor.x;
            int col = anchor.y;
            findPlacementsAt(game, row, col, Move.Direction.HORIZONTAL, possibleMoves);
            findPlacementsAt(game, row, col, Move.Direction.VERTICAL, possibleMoves);
        }
        return possibleMoves;
    }

    private void findMovesForEmptyBoard(Game game, List<Move> possibleMoves) {
        Dictionary dictionary = game.getDictionary();
        Rack rack = player.getRack();
        String rackLetters = getTilesAsString(rack.getTiles());
        Set<String> validWords = findValidWordsFromRack(rackLetters, dictionary);
        for (String word : validWords) {
            if (word.length() < 2)
                continue;
            for (int offset = 0; offset < word.length(); offset++) {
                int startCol = ScrabbleConstants.CENTER_SQUARE - offset;
                if (startCol >= 0 && startCol + word.length() <= Board.SIZE) {
                    Move move = Move.createPlaceMove(player, ScrabbleConstants.CENTER_SQUARE, startCol, Move.Direction.HORIZONTAL);
                    List<Tile> tilesForWord = getTilesForWord(word, rack.getTiles());
                    if (tilesForWord != null) {
                        move.addTiles(tilesForWord);
                        int score = calculateWordScore(word, ScrabbleConstants.CENTER_SQUARE, startCol, true, game.getBoard());
                        move.setScore(score);
                        List<String> formedWords = new ArrayList<>();
                        formedWords.add(word);
                        move.setFormedWords(formedWords);
                        possibleMoves.add(move);
                    }
                }
            }
            for (int offset = 0; offset < word.length(); offset++) {
                int startRow = ScrabbleConstants.CENTER_SQUARE - offset;
                if (startRow >= 0 && startRow + word.length() <= Board.SIZE) {
                    Move move = Move.createPlaceMove(player, startRow, ScrabbleConstants.CENTER_SQUARE, Move.Direction.VERTICAL);
                    List<Tile> tilesForWord = getTilesForWord(word, rack.getTiles());
                    if (tilesForWord != null) {
                        move.addTiles(tilesForWord);
                        int score = calculateWordScore(word, startRow, ScrabbleConstants.CENTER_SQUARE, false, game.getBoard());
                        move.setScore(score);
                        List<String> formedWords = new ArrayList<>();
                        formedWords.add(word);
                        move.setFormedWords(formedWords);
                        possibleMoves.add(move);
                    }
                }
            }
        }
    }

    private void findPlacementsAt(Game game, int row, int col, Move.Direction direction, List<Move> possibleMoves) {
        Board board = game.getBoard();
        Dictionary dictionary = game.getDictionary();
        Rack rack = player.getRack();
        String rackLetters = getTilesAsString(rack.getTiles());
        Board tempBoard = new Board();
        for (int r = 0; r < Board.SIZE; r++) {
            for (int c = 0; c < Board.SIZE; c++) {
                if (board.getSquare(r, c).hasTile()) {
                    tempBoard.placeTile(r, c, board.getSquare(r, c).getTile());
                }
            }
        }
        String[] partialWords = getPartialWords(board, row, col, direction);
        String prefix = partialWords[0];
        String suffix = partialWords[1];
        for (char letter : getUniqueLetters(rackLetters)) {
            findWordsWithLetterAt(game, row, col, letter, direction, possibleMoves);
        }
    }

    private String[] getPartialWords(Board board, int row, int col, Move.Direction direction) {
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
        return new String[] { prefix.toString(), suffix.toString() };
    }

    private void findWordsWithLetterAt(Game game, int row, int col, char letter, Move.Direction direction, List<Move> possibleMoves) {
        Board board = game.getBoard();
        Dictionary dictionary = game.getDictionary();
        Rack rack = player.getRack();
        String[] partialWords = getPartialWords(board, row, col, direction);
        String prefix = partialWords[0];
        String suffix = partialWords[1];
        String completeWord = prefix + letter + suffix;
        if (completeWord.length() >= 2 && dictionary.isValidWord(completeWord)) {
            int startRow, startCol;
            if (direction == Move.Direction.HORIZONTAL) {
                startRow = row;
                startCol = col - prefix.length();
            } else {
                startRow = row - prefix.length();
                startCol = col;
            }
            Move move = Move.createPlaceMove(player, startRow, startCol, direction);
            List<Tile> tilesNeeded = new ArrayList<>();
            Tile tilePlaced = null;
            for (Tile tile : rack.getTiles()) {
                if (tile.getLetter() == letter && !tilesNeeded.contains(tile)) {
                    tilePlaced = tile;
                    tilesNeeded.add(tile);
                    break;
                }
            }
            if (tilePlaced != null) {
                Board tempBoard = new Board();
                for (int r = 0; r < Board.SIZE; r++) {
                    for (int c = 0; c < Board.SIZE; c++) {
                        if (board.getSquare(r, c).hasTile()) {
                            tempBoard.placeTile(r, c, board.getSquare(r, c).getTile());
                        }
                    }
                }
                tempBoard.placeTile(row, col, tilePlaced);
                List<String> formedWords = new ArrayList<>();
                formedWords.add(completeWord);
                String crossWord;
                if (direction == Move.Direction.HORIZONTAL) {
                    crossWord = getWordAt(tempBoard, row, col, false);
                } else {
                    crossWord = getWordAt(tempBoard, row, col, true);
                }
                if (crossWord.length() >= 2) {
                    if (dictionary.isValidWord(crossWord)) {
                        formedWords.add(crossWord);
                    } else {
                        return;
                    }
                }
                move.setFormedWords(formedWords);
                move.addTiles(tilesNeeded);
                int score = calculateMoveScore(move, board, tilePlaced, row, col);
                move.setScore(score);
                possibleMoves.add(move);
                extendPlacement(game, move, tempBoard, row, col, direction, possibleMoves);
            }
        }
    }

    private void extendPlacement(Game game, Move baseMove, Board tempBoard, int row, int col, Move.Direction direction, List<Move> possibleMoves) {
        Board board = game.getBoard();
        Dictionary dictionary = game.getDictionary();
        Rack rack = player.getRack();
        List<Tile> remainingTiles = new ArrayList<>(rack.getTiles());
        for (Tile t : baseMove.getTiles()) {
            remainingTiles.remove(t);
        }
        if (remainingTiles.isEmpty()) {
            return;
        }
        int nextRow = row;
        int nextCol = col;
        if (direction == Move.Direction.HORIZONTAL) {
            nextCol = col + 1;
        } else {
            nextRow = row + 1;
        }
        if (nextRow >= Board.SIZE || nextCol >= Board.SIZE || tempBoard.getSquare(nextRow, nextCol).hasTile()) {
            return;
        }
        for (Tile tile : remainingTiles) {
            Board extendedBoard = new Board();
            for (int r = 0; r < Board.SIZE; r++) {
                for (int c = 0; c < Board.SIZE; c++) {
                    if (tempBoard.getSquare(r, c).hasTile()) {
                        extendedBoard.placeTile(r, c, tempBoard.getSquare(r, c).getTile());
                    }
                }
            }
            extendedBoard.placeTile(nextRow, nextCol, tile);
            List<String> formedWords = new ArrayList<>();
            String mainWord;
            if (direction == Move.Direction.HORIZONTAL) {
                mainWord = getWordAt(extendedBoard, baseMove.getStartRow(), baseMove.getStartCol(), true);
            } else {
                mainWord = getWordAt(extendedBoard, baseMove.getStartRow(), baseMove.getStartCol(), false);
            }
            if (mainWord.length() < 2 || !dictionary.isValidWord(mainWord)) {
                continue;
            }
            formedWords.add(mainWord);
            String crossWord;
            if (direction == Move.Direction.HORIZONTAL) {
                crossWord = getWordAt(extendedBoard, nextRow, nextCol, false);
            } else {
                crossWord = getWordAt(extendedBoard, nextRow, nextCol, true);
            }
            if (crossWord.length() >= 2) {
                if (!dictionary.isValidWord(crossWord)) {
                    continue;
                }
                formedWords.add(crossWord);
            }
            Move extendedMove = Move.createPlaceMove(player, baseMove.getStartRow(), baseMove.getStartCol(), direction);
            List<Tile> allTiles = new ArrayList<>(baseMove.getTiles());
            allTiles.add(tile);
            extendedMove.addTiles(allTiles);
            extendedMove.setFormedWords(formedWords);
            int score = calculateMoveScore(extendedMove, board, new Point(nextRow, nextCol));
            extendedMove.setScore(score);
            possibleMoves.add(extendedMove);
            extendPlacement(game, extendedMove, extendedBoard, nextRow, nextCol, direction, possibleMoves);
        }
    }

    private String getWordAt(Board board, int row, int col, boolean isHorizontal) {
        StringBuilder word = new StringBuilder();
        int startRow = row;
        int startCol = col;
        if (isHorizontal) {
            while (startCol > 0 && board.getSquare(row, startCol - 1).hasTile()) {
                startCol--;
            }
        } else {
            while (startRow > 0 && board.getSquare(startRow - 1, col).hasTile()) {
                startRow--;
            }
        }
        int currentRow = startRow;
        int currentCol = startCol;
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

    private List<Point> findAnchorPoints(Board board) {
        List<Point> anchorPoints = new ArrayList<>();
        for (int row = 0; row < Board.SIZE; row++) {
            for (int col = 0; col < Board.SIZE; col++) {
                if (board.getSquare(row, col).hasTile()) {
                    continue;
                }
                if (hasAdjacentTile(board, row, col)) {
                    anchorPoints.add(new Point(row, col));
                }
            }
        }
        return anchorPoints;
    }

    private boolean hasAdjacentTile(Board board, int row, int col) {
        if (row > 0 && board.getSquare(row - 1, col).hasTile())
            return true;
        if (row < Board.SIZE - 1 && board.getSquare(row + 1, col).hasTile())
            return true;
        if (col > 0 && board.getSquare(row, col - 1).hasTile())
            return true;
        if (col < Board.SIZE - 1 && board.getSquare(row, col + 1).hasTile())
            return true;
        return false;
    }

    private Set<String> findValidWordsFromRack(String rackLetters, Dictionary dictionary) {
        Set<String> validWords = new HashSet<>();
        for (int len = 2; len <= rackLetters.length(); len++) {
            findWordsOfLength(rackLetters, len, "", dictionary, validWords);
        }
        return validWords;
    }

    private void findWordsOfLength(String letters, int length, String current, Dictionary dictionary, Set<String> validWords) {
        if (current.length() == length) {
            if (dictionary.isValidWord(current)) {
                validWords.add(current);
            }
            return;
        }
        for (int i = 0; i < letters.length(); i++) {
            char letter = letters.charAt(i);
            findWordsOfLength(letters.substring(0, i) + letters.substring(i + 1), length, current + letter, dictionary, validWords);
        }
    }

    private List<Tile> getTilesForWord(String word, List<Tile> rackTiles) {
        Map<Character, List<Tile>> availableTiles = new HashMap<>();
        for (Tile tile : rackTiles) {
            char letter = tile.getLetter();
            if (!availableTiles.containsKey(letter)) {
                availableTiles.put(letter, new ArrayList<>());
            }
            availableTiles.get(letter).add(tile);
        }
        List<Tile> result = new ArrayList<>();
        for (char c : word.toCharArray()) {
            if (availableTiles.containsKey(c) && !availableTiles.get(c).isEmpty()) {
                Tile tile = availableTiles.get(c).remove(0);
                result.add(tile);
            } else {
                if (availableTiles.containsKey('*') && !availableTiles.get('*').isEmpty()) {
                    Tile blankTile = availableTiles.get('*').remove(0);
                    Tile letterTile = Tile.createBlankTile(c);
                    result.add(letterTile);
                } else {
                    return null;
                }
            }
        }
        return result;
    }

    private int calculateWordScore(String word, int startRow, int startCol, boolean isHorizontal, Board board) {
        int score = 0;
        int wordMultiplier = 1;
        int row = startRow;
        int col = startCol;
        for (int i = 0; i < word.length(); i++) {
            Square square = board.getSquare(row, col);
            char letter = word.charAt(i);
            int letterValue = TileBag.getPointValue(letter);
            if (!square.hasTile() && !square.isSquareTypeUsed()) {
                if (square.getSquareType() == Square.SquareType.DOUBLE_LETTER) {
                    letterValue *= 2;
                } else if (square.getSquareType() == Square.SquareType.TRIPLE_LETTER) {
                    letterValue *= 3;
                }
                if (square.getSquareType() == Square.SquareType.DOUBLE_WORD || square.getSquareType() == Square.SquareType.CENTER) {
                    wordMultiplier *= 2;
                } else if (square.getSquareType() == Square.SquareType.TRIPLE_WORD) {
                    wordMultiplier *= 3;
                }
            }
            score += letterValue;
            if (isHorizontal) {
                col++;
            } else {
                row++;
            }
        }
        score *= wordMultiplier;
        if (word.length() == 7) {
            score += ScrabbleConstants.BINGO_BONUS;
        }
        return score;
    }

    private int calculateMoveScore(Move move, Board board, Tile newTile, int row, int col) {
        int totalScore = 0;
        for (String word : move.getFormedWords()) {
            Point wordStart = findWordStart(board, word, row, col);
            if (wordStart == null)
                continue;
            boolean isHorizontal = isWordHorizontal(board, word, wordStart.x, wordStart.y, row, col);
            int wordScore = calculateWordScore(word, wordStart.x, wordStart.y, isHorizontal, board);
            totalScore += wordScore;
        }
        if (move.getTiles().size() == 7) {
            totalScore += ScrabbleConstants.BINGO_BONUS;
        }
        return totalScore;
    }

    private int calculateMoveScore(Move move, Board board, Point newTilePos) {
        int totalScore = 0;
        for (String word : move.getFormedWords()) {
            boolean isHorizontal = move.getDirection() == Move.Direction.HORIZONTAL;
            int wordScore = calculateWordScore(word, move.getStartRow(), move.getStartCol(), isHorizontal, board);
            totalScore += wordScore;
        }
        if (move.getTiles().size() == 7) {
            totalScore += ScrabbleConstants.BINGO_BONUS;
        }
        return totalScore;
    }

    private Point findWordStart(Board board, String word, int tileRow, int tileCol) {
        for (int col = Math.max(0, tileCol - word.length() + 1); col <= tileCol; col++) {
            if (col + word.length() <= Board.SIZE) {
                String horizontalWord = getWordAt(board, tileRow, col, true);
                if (horizontalWord.equals(word)) {
                    return new Point(tileRow, col);
                }
            }
        }
        for (int row = Math.max(0, tileRow - word.length() + 1); row <= tileRow; row++) {
            if (row + word.length() <= Board.SIZE) {
                String verticalWord = getWordAt(board, row, tileCol, false);
                if (verticalWord.equals(word)) {
                    return new Point(row, tileCol);
                }
            }
        }
        return null;
    }

    private boolean isWordHorizontal(Board board, String word, int startRow, int startCol, int tileRow, int tileCol) {
        return startRow == tileRow;
    }

    private Set<Character> getUniqueLetters(String letters) {
        Set<Character> uniqueLetters = new HashSet<>();
        for (char c : letters.toCharArray()) {
            uniqueLetters.add(c);
        }
        return uniqueLetters;
    }

    private String getTilesAsString(List<Tile> tiles) {
        StringBuilder sb = new StringBuilder();
        for (Tile tile : tiles) {
            sb.append(tile.getLetter());
        }
        return sb.toString();
    }

    private String removeLetterFromString(String letters, char letter) {
        int index = letters.indexOf(letter);
        if (index == -1)
            return letters;
        return letters.substring(0, index) + letters.substring(index + 1);
    }

    private boolean isVowel(char letter) {
        letter = Character.toUpperCase(letter);
        return letter == 'A' || letter == 'E' || letter == 'I' || letter == 'O' || letter == 'U';
    }

    private int countVowels(List<Tile> tiles) {
        int count = 0;
        for (Tile tile : tiles) {
            if (isVowel(tile.getLetter())) {
                count++;
            }
        }
        return count;
    }
}
