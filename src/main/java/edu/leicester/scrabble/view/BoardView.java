package edu.leicester.scrabble.view;

import edu.leicester.scrabble.controller.GameController;
import edu.leicester.scrabble.model.Board;
import edu.leicester.scrabble.model.Move;
import edu.leicester.scrabble.model.Square;
import edu.leicester.scrabble.model.Tile;
import edu.leicester.scrabble.util.ScrabbleConstants;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

public class BoardView extends GridPane {

    private final GameController controller;
    private final SquareView[][] squareViews;

    public BoardView(GameController controller) {
        this.controller = controller;
        this.squareViews = new SquareView[Board.SIZE][Board.SIZE];

        setHgap(2);
        setVgap(2);
        setPadding(new Insets(5));
        setBorder(new Border(new BorderStroke(Color.BLACK, BorderStrokeStyle.SOLID, CornerRadii.EMPTY, BorderWidths.DEFAULT)));

        initializeSquares();
    }

    private void initializeSquares() {
        Board board = controller.getBoard();
        for (int row = 0; row < Board.SIZE; row++) {
            for (int col = 0; col < Board.SIZE; col++) {
                Square square = board.getSquare(row, col);
                SquareView squareView = new SquareView(square, row, col);
                squareViews[row][col] = squareView;
                add(squareView, col, row);
            }
        }
    }

    public void updateBoard() {
        Board board = controller.getBoard();
        for (int row = 0; row < Board.SIZE; row++) {
            for (int col = 0; col < Board.SIZE; col++) {
                squareViews[row][col].update();
            }
        }
    }

    private class SquareView extends StackPane {
        private final Square square;
        private final int row;
        private final int col;
        private final Label label;
        private final Label valueLabel; // Label to show tile point value
        private final Label premiumLabel;
        private boolean isTemporaryTile = false;

        public SquareView(Square square, int row, int col) {
            this.square = square;
            this.row = row;
            this.col = col;

            setPrefSize(ScrabbleConstants.SQUARE_SIZE, ScrabbleConstants.SQUARE_SIZE);
            setBorder(new Border(new BorderStroke(Color.DARKGRAY, BorderStrokeStyle.SOLID, CornerRadii.EMPTY, new BorderWidths(0.5))));

            label = new Label();
            label.setFont(Font.font("Arial", FontWeight.BOLD, 16));
            label.setAlignment(Pos.CENTER);

            valueLabel = new Label(); // Label for tile value
            valueLabel.setFont(Font.font("Arial", 8));
            valueLabel.setTextFill(Color.BLACK);
            valueLabel.setAlignment(Pos.BOTTOM_RIGHT);
            valueLabel.setTranslateX(10);
            valueLabel.setTranslateY(12);

            premiumLabel = new Label();
            premiumLabel.setFont(Font.font("Arial", 10));
            premiumLabel.setAlignment(Pos.CENTER);

            getChildren().addAll(premiumLabel, label, valueLabel);
            setAlignment(Pos.CENTER);

            setupDropTarget();
            update();
        }

        public void update() {
            isTemporaryTile = controller.hasTemporaryTileAt(row, col);
            if (isTemporaryTile) {
                Tile tempTile = controller.getTemporaryTileAt(row, col);
                if (tempTile != null) {
                    label.setText(String.valueOf(tempTile.getLetter()));

                    // Show point value for temporary tile
                    valueLabel.setText(String.valueOf(tempTile.getValue()));

                    // Special styling for blank tiles
                    if (tempTile.isBlank()) {
                        label.setTextFill(Color.BLUE);
                        valueLabel.setText("0");
                    } else {
                        label.setTextFill(Color.BLACK);
                    }

                    label.setStyle("-fx-background-color: #FFAA00; -fx-padding: 5; -fx-background-radius: 3;");
                    premiumLabel.setText("");
                    setBackground(new Background(new BackgroundFill(Color.LIGHTYELLOW, CornerRadii.EMPTY, Insets.EMPTY)));
                    setBorder(new Border(new BorderStroke(Color.ORANGE, BorderStrokeStyle.SOLID, CornerRadii.EMPTY, new BorderWidths(2))));
                    return;
                }
            }
            updateBackground();
            if (square.hasTile()) {
                Tile tile = square.getTile();
                label.setText(String.valueOf(tile.getLetter()));

                // Show point value for placed tile
                valueLabel.setText(String.valueOf(tile.getValue()));

                // Special styling for blank tiles
                if (tile.isBlank()) {
                    label.setTextFill(Color.BLUE);
                    valueLabel.setText("0");
                } else {
                    label.setTextFill(Color.BLACK);
                }

                label.setStyle("-fx-background-color: #CD7F32; -fx-padding: 5; -fx-background-radius: 3;");
                premiumLabel.setText("");
                setBorder(new Border(new BorderStroke(Color.DARKGRAY, BorderStrokeStyle.SOLID, CornerRadii.EMPTY, new BorderWidths(0.5))));
            } else {
                label.setText("");
                valueLabel.setText("");
                label.setStyle("");
                setBorder(new Border(new BorderStroke(Color.DARKGRAY, BorderStrokeStyle.SOLID, CornerRadii.EMPTY, new BorderWidths(0.5))));
                switch (square.getSquareType()) {
                    case DOUBLE_LETTER:
                        premiumLabel.setText("DL");
                        premiumLabel.setTextFill(Color.WHITE);
                        break;
                    case TRIPLE_LETTER:
                        premiumLabel.setText("TL");
                        premiumLabel.setTextFill(Color.WHITE);
                        break;
                    case DOUBLE_WORD:
                        premiumLabel.setText("DW");
                        premiumLabel.setTextFill(Color.WHITE);
                        break;
                    case TRIPLE_WORD:
                        premiumLabel.setText("TW");
                        premiumLabel.setTextFill(Color.WHITE);
                        break;
                    case CENTER:
                        premiumLabel.setText("★");
                        premiumLabel.setTextFill(Color.BLACK);
                        break;
                    default:
                        premiumLabel.setText("");
                        break;
                }
            }
        }

        private void updateBackground() {
            Color backgroundColor;
            if (square.hasTile()) {
                backgroundColor = Color.BURLYWOOD;
            } else {
                switch (square.getSquareType()) {
                    case DOUBLE_LETTER:
                        backgroundColor = Color.LIGHTBLUE;
                        break;
                    case TRIPLE_LETTER:
                        backgroundColor = Color.BLUE;
                        break;
                    case DOUBLE_WORD:
                        backgroundColor = Color.LIGHTPINK;
                        break;
                    case TRIPLE_WORD:
                        backgroundColor = Color.RED;
                        break;
                    case CENTER:
                        backgroundColor = Color.LIGHTPINK;
                        break;
                    default:
                        backgroundColor = Color.BEIGE;
                        break;
                }
            }
            setBackground(new Background(new BackgroundFill(backgroundColor, CornerRadii.EMPTY, Insets.EMPTY)));
        }

        private boolean isValidPlacement(int row, int col) {
            Board board = controller.getBoard();
            if (board.getSquare(row, col).hasTile() || controller.hasTemporaryTileAt(row, col)) {
                return false;
            }
            if (board.isEmpty() && controller.getTemporaryPlacements().isEmpty()) {
                return row == ScrabbleConstants.CENTER_SQUARE && col == ScrabbleConstants.CENTER_SQUARE;
            }
            if (!controller.getTemporaryPlacements().isEmpty()) {
                return controller.isValidTemporaryPlacement(row, col);
            }
            return board.hasAdjacentTile(row, col);
        }

        private void setupDropTarget() {
            setOnDragOver(event -> {
                if (event.getGestureSource() != this && isValidPlacement(row, col)) {
                    event.acceptTransferModes(TransferMode.MOVE);
                    setStyle("-fx-border-color: gold; -fx-border-width: 2;");
                    if (!controller.getTemporaryPlacements().isEmpty()) {
                        Move.Direction direction = controller.determineDirection();
                        if (direction != null) {
                            setStyle("-fx-border-color: gold; -fx-border-width: 2;");
                        }
                    }
                }
                event.consume();
            });

            setOnDragEntered(event -> {
                if (event.getGestureSource() != this && isValidPlacement(row, col)) {
                    setStyle("-fx-border-color: gold; -fx-border-width: 2;");
                }
                event.consume();
            });

            setOnDragExited(event -> {
                setStyle("-fx-border-color: darkgray; -fx-border-width: 0.5;");
                event.consume();
            });

            setOnDragDropped(event -> {
                Dragboard db = event.getDragboard();
                boolean success = false;
                if (db.hasString() && isValidPlacement(row, col)) {
                    try {
                        int tileIndex = Integer.parseInt(db.getString());
                        success = controller.placeTileTemporarily(tileIndex, row, col);
                    } catch (NumberFormatException e) {
                        System.out.println("Invalid drag data: " + e.getMessage());
                    }
                }
                event.setDropCompleted(success);
                event.consume();
            });
        }
    }
}