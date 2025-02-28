module edu.leicester.scrabble {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;

    requires org.controlsfx.controls;
    requires org.kordamp.bootstrapfx.core;
    requires eu.hansolo.tilesfx;
    requires java.desktop;
    requires jdk.compiler;

    opens edu.leicester.scrabble to javafx.fxml;
    exports edu.leicester.scrabble;
}