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
import lombok.Getter;

import java.io.IOException;
import java.util.List;

/**
 * Collapsible section of the edit panel with an eye toggle in its title.
 * <p>
 * It is only a container: whoever builds it adds the rows to {@link #getBody()}. The eye only reports its state
 * ({@link #enabledProperty()}); while it is off the content is disabled (dimmed, not editable) and the eye is
 * crossed out (:bypassed).
 */
@Getter
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
        body.disableProperty().bind(eyeToggle.selectedProperty().not());
    }

    public BooleanProperty enabledProperty() {
        return eyeToggle.selectedProperty();
    }
}
