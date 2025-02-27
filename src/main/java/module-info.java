module edu.leicester.scrabble {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;

    requires org.controlsfx.controls;
    requires org.kordamp.bootstrapfx.core;
    requires eu.hansolo.tilesfx;

    opens edu.leicester.scrabble to javafx.fxml;
    exports edu.leicester.scrabble;
}