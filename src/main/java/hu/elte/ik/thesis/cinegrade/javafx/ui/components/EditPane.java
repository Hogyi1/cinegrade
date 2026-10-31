// [AI-GENERATED]
package hu.elte.ik.thesis.cinegrade.javafx.ui.components;

import hu.elte.ik.thesis.cinegrade.domain.enums.ErrorCode;
import hu.elte.ik.thesis.cinegrade.domain.exceptions.CineGradeException;
import javafx.beans.property.BooleanProperty;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.util.List;

/**
 * Collapsible section of the edit panel with an eye toggle in its title.
 * <p>
 * It is only a container: whoever builds it adds the rows (sliders, wheels, the curve) with {@link #addGroup},
 * each group under a small header. The eye only reports its state ({@link #enabledProperty()}); while it is off
 * the content is disabled (dimmed, not editable) and the eye is crossed out (:bypassed).
 */
public class EditPane extends TitledPane {

    private static final String FXML_PATH = "/fxmls/editPane.fxml";
    private static final PseudoClass BYPASSED = PseudoClass.getPseudoClass("bypassed");

    @FXML
    private ToggleButton eyeToggle;
    @FXML
    private VBox body;

    public EditPane(String title) {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(FXML_PATH));
        loader.setRoot(this);
        loader.setController(this);
        try {
            loader.load();
        } catch (IOException exception) {
            throw new CineGradeException(ErrorCode.REQ_RESOURCE_MISSING, exception, FXML_PATH);
        }

        setText(title);
        eyeToggle.addEventHandler(MouseEvent.ANY, MouseEvent::consume);
        eyeToggle.selectedProperty().addListener((obs, wasOn, on) -> pseudoClassStateChanged(BYPASSED, !on));
        // off: nothing in the body can be changed (and it dims like any disabled control); the eye is the title
        // graphic, not in the body, so it stays clickable
        body.disableProperty().bind(eyeToggle.selectedProperty().not());
    }

    /**
     * True while the eye is on (the section is applied).
     */
    public BooleanProperty enabledProperty() {
        return eyeToggle.selectedProperty();
    }

    /**
     * Adds rows under a small header; a null or blank title adds them without one.
     */
    public void addGroup(String title, List<? extends Node> rows) {
        addGroup(title, null, rows);
    }

    /**
     * Same, with a node at the right end of the header (e.g. the white balance eyedropper).
     */
    public void addGroup(String title, Node trailing, List<? extends Node> rows) {
        if (title != null && !title.isBlank()) {
            Label header = new Label(title.toUpperCase());
            header.getStyleClass().add("edit-pane-group");
            if (trailing == null) {
                body.getChildren().add(header);
            } else {
                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                HBox headerRow = new HBox(header, spacer, trailing);
                headerRow.getStyleClass().add("edit-pane-group-row");
                body.getChildren().add(headerRow);
            }
        }
        body.getChildren().addAll(rows);
    }
}
