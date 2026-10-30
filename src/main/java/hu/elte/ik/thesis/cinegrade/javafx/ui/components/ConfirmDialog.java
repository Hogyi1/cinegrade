package hu.elte.ik.thesis.cinegrade.javafx.ui.components;

import hu.elte.ik.thesis.cinegrade.domain.enums.ErrorCode;
import hu.elte.ik.thesis.cinegrade.domain.exceptions.CineGradeException;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.IOException;

/**
 * Confirmation panel: title, message and a cancel / confirm button pair. Enter confirms, Escape cancels.
 * <p>
 * Built from {@code /fxmls/confirmDialog.fxml} with {@code fx:root}, so it can also be placed in FXML. Most callers
 * only need {@link #show}:
 * <pre>{@code
 * if (ConfirmDialog.show(window, "Delete catalog", "This can't be undone.", "Delete", true)) { ... }
 * }</pre>
 */
public class ConfirmDialog extends VBox {

    private static final String FXML_PATH = "/fxmls/confirmDialog.fxml";

    @FXML
    private StackPane iconBadge;
    @FXML
    private Label titleLabel;
    @FXML
    private Label messageLabel;
    @FXML
    private Button cancelButton;
    @FXML
    private Button confirmButton;

    private boolean confirmed;

    public ConfirmDialog() {
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
     * @param danger red confirm button and warning icon, for anything that deletes
     */
    public ConfirmDialog(String title, String message, String confirmText, boolean danger) {
        this();
        setTitle(title);
        setMessage(message);
        setConfirmText(confirmText);
        setDanger(danger);
    }

    /**
     * Shows the dialog over the owner and waits for an answer.
     *
     * @return {@code true} if the user confirmed, {@code false} on cancel, Escape or closing it
     */
    public static boolean show(Window owner, String title, String message, String confirmText, boolean danger) {
        ConfirmDialog dialog = new ConfirmDialog(title, message, confirmText, danger);
        Stage stage = DialogWindow.create(owner, dialog, true);
        stage.setTitle(title);

        dialog.cancelButton.setOnAction(e -> stage.close());
        dialog.confirmButton.setOnAction(e -> {
            dialog.confirmed = true;
            stage.close();
        });

        stage.setOnShown(e -> {
            DialogWindow.centerOnOwner(stage);
            dialog.cancelButton.requestFocus();
        });
        stage.showAndWait();
        return dialog.confirmed;
    }

    public void setTitle(String title) {
        titleLabel.setText(title);
    }

    public void setMessage(String message) {
        messageLabel.setText(message);
    }

    public void setConfirmText(String text) {
        confirmButton.setText(text);
    }

    public void setCancelText(String text) {
        cancelButton.setText(text);
    }

    public void setDanger(boolean danger) {
        getStyleClass().remove("danger");
        if (danger) {
            getStyleClass().add("danger");
        }
        iconBadge.setVisible(danger);
        iconBadge.setManaged(danger);
    }
}
