package edu.leicester.scrabble.view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * Dialog for selecting a letter for a blank tile
 */
public class BlankTileDialog {
    private char selectedLetter = '\0';

    /**
     * Shows a dialog to select a letter for a blank tile
     *
     * @return The selected letter or '\0' if cancelled
     */
    public char showAndWait() {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.UTILITY);
        dialog.setTitle("Choose Letter");
        dialog.setResizable(false);

        Label instructionLabel = new Label("Select a letter for your blank tile:");
        instructionLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        FlowPane lettersPane = new FlowPane();
        lettersPane.setHgap(5);
        lettersPane.setVgap(5);
        lettersPane.setAlignment(Pos.CENTER);
        lettersPane.setPadding(new Insets(10));

        // Create a button for each letter in the alphabet
        for (char c = 'A'; c <= 'Z'; c++) {
            Button letterButton = new Button(Character.toString(c));
            letterButton.setPrefSize(40, 40);
            letterButton.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
            final char letter = c;
            letterButton.setOnAction(e -> {
                selectedLetter = letter;
                dialog.close();
            });
            lettersPane.getChildren().add(letterButton);
        }

        Button cancelButton = new Button("Cancel");
        cancelButton.setOnAction(e -> {
            selectedLetter = '\0';
            dialog.close();
        });

        VBox root = new VBox(15);
        root.setPadding(new Insets(15));
        root.setAlignment(Pos.CENTER);
        root.getChildren().addAll(instructionLabel, lettersPane, cancelButton);

        Scene scene = new Scene(root);
        dialog.setScene(scene);
        dialog.showAndWait();

        return selectedLetter;
    }
}