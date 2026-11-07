package hu.elte.ik.thesis.cinegrade.javafx.ui.components;

import hu.elte.ik.thesis.cinegrade.domain.enums.ErrorCode;
import hu.elte.ik.thesis.cinegrade.domain.exceptions.CineGradeException;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * Small comment box that pops up above a photo. Ctrl+Enter saves, Escape or clicking elsewhere cancels.
 * <p>
 * Built from {@code /fxmls/commentEditor.fxml} with {@code fx:root}. Use {@link #show}.
 */
public class CommentEditor extends VBox {

    private static final String FXML_PATH = "/fxmls/commentEditor.fxml";

    @FXML
    private Label titleLabel;
    @FXML
    private TextArea commentArea;
    @FXML
    private Button cancelButton;
    @FXML
    private Button saveButton;

    public CommentEditor() {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(FXML_PATH));
        loader.setRoot(this);
        loader.setController(this);
        try {
            loader.load();
        } catch (IOException exception) {
            throw new CineGradeException(ErrorCode.REQ_RESOURCE_MISSING, exception, FXML_PATH);
        }
    }

    /**
     * Opens the editor above the anchor.
     *
     * @param anchor  the node it points at (the photo's cell)
     * @param title   shown on top, the file name
     * @param comment the current comment, may be {@code null}
     * @param onSave  gets the new text (possibly blank) when the user saves
     */
    public static void show(Node anchor, String title, String comment, Consumer<String> onSave) {
        CommentEditor editor = new CommentEditor();
        editor.titleLabel.setText(title);
        editor.commentArea.setText(comment != null ? comment : "");

        Stage stage = DialogWindow.create(anchor.getScene().getWindow(), editor, false);

        Runnable save = () -> {
            stage.close();
            onSave.accept(editor.commentArea.getText());
        };
        editor.saveButton.setOnAction(e -> save.run());
        editor.cancelButton.setOnAction(e -> stage.close());
        editor.commentArea.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ENTER && e.isShortcutDown()) {
                save.run();
                e.consume();
            } else if (e.getCode() == KeyCode.ESCAPE) {
                stage.close();
                e.consume();
            }
        });
        // Clicking anywhere else closes it without saving
        stage.focusedProperty().addListener((obs, was, focused) -> {
            if (!focused) {
                stage.close();
            }
        });

        stage.setOnShown(e -> {
            DialogWindow.placeAbove(stage, anchor);
            Platform.runLater(() -> {
                editor.commentArea.requestFocus();
                editor.commentArea.end();
            });
        });
        stage.show();
    }
}
