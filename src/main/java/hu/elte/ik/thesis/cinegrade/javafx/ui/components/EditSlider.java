// [AI-GENERATED]
package hu.elte.ik.thesis.cinegrade.javafx.ui.components;

import hu.elte.ik.thesis.cinegrade.app.managers.editing.EditingManager;
import hu.elte.ik.thesis.cinegrade.domain.objects.EditState;
import hu.elte.ik.thesis.cinegrade.domain.enums.ErrorCode;
import hu.elte.ik.thesis.cinegrade.domain.exceptions.CineGradeException;
import hu.elte.ik.thesis.cinegrade.engine.GradeParam;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import lombok.Getter;
import lombok.Setter;

import java.io.IOException;
import java.util.Locale;
import java.util.regex.Pattern;


/**
 * One row of the edit panel: name, slider, typed value and unit of a {@link GradeParam}.
 * <p>
 * It talks to the {@link EditingManager} by itself, so adding a row
 * is just {@code new EditSlider(GradeParam.EXPOSURE, editingManager)}: it follows the selected photo's edit, previews
 * on every value change (the viewport renderer keeps only the latest) and commits once the value is final: mouse
 * released, Enter / focus lost in the field, a keyboard step or a double-click reset.
 * {@link #show} and {@link #setValue} (loading another photo) do neither.
 */
@Getter
@Setter
public class EditSlider extends HBox {

    private static final String FXML_PATH = "/fxmls/editSlider.fxml";
    private static final Pattern NUMBER = Pattern.compile("-?(([1-9][0-9]*)|0)?(\\.[0-9]*)?");

    private final GradeParam param;
    private final EditingManager editingManager;
    private Runnable onChanged = () -> {
    };
    @FXML
    private Label nameLabel;
    @FXML
    private Slider slider;
    @FXML
    private TextField valueField;
    @FXML
    private Label unitLabel;
    private boolean silent;
    private boolean mousePressed;
    private float lastCommitted;
    private float defaultValue;

    public EditSlider(GradeParam param, EditingManager editingManager) {
        this.param = param;
        this.editingManager = editingManager;

        FXMLLoader loader = new FXMLLoader(getClass().getResource(FXML_PATH));
        loader.setRoot(this);
        loader.setController(this);
        try {
            loader.load();
        } catch (IOException exception) {
            throw new CineGradeException(ErrorCode.REQ_RESOURCE_MISSING, exception, FXML_PATH);
        }

        nameLabel.setText(param.getName());
        unitLabel.setText(param.getUnit());
        // per parameter (e.g. param-temperature, param-hue-red): the CSS colors the track by it
        getStyleClass().add("param-" + param.name().toLowerCase(Locale.ROOT).replace('_', '-'));

        lastCommitted = param.getDefVal();
        defaultValue = param.getDefVal();

        setupSlider();
        setupField();

        editingManager.getViewModel().currentEditStateProperty().addListener((obs, oldState, newState) -> show(newState));
        show(editingManager.getViewModel().currentEditStateProperty().get());
    }

    /**
     * Shows this param of the edit, the double-click reset goes to the photo's own default (the as-shot white
     * balance). No edit: the param's default. Nothing is previewed or committed.
     */
    public void show(EditState editState) {
        setDefaultValue(editState == null ? param.getDefVal() : param.read(editState.defaults()));
        setValue(editState == null ? param.getDefVal() : param.read(editState));
    }

    /**
     * Shows a value without firing any callback (e.g. the newly selected photo's edit).
     */
    public void setValue(float value) {
        silent = true;
        try {
            slider.setValue(param.toSliderPosition(value));
        } finally {
            silent = false;
        }
        lastCommitted = value();
        valueField.setText(String.format(Locale.ROOT, "%.0f", value()));
    }

    public void reset() {
        slider.setValue(param.toSliderPosition(defaultValue));
        commit();
    }

    /**
     * The parameter value at the slider's current position.
     */
    private float value() {
        return param.fromSliderPosition(slider.getValue());
    }

    private void setupSlider() {
        // the slider holds a track position (see GradeParam#toSliderPosition), everything outside gets the value
        slider.setMin(param.toSliderPosition(param.getMinVal()));
        slider.setMax(param.toSliderPosition(param.getMaxVal()));
        slider.setValue(param.toSliderPosition(param.getDefVal()));
        slider.setBlockIncrement(param.getIncrement());

        slider.valueProperty().addListener((obs, oldValue, newValue) -> {
            valueField.setText(String.format(Locale.ROOT, "%.0f", value()));
            if (!silent) {
                editingManager.preview(param, value());
                onChanged.run();
                if (!mousePressed) {
                    commit();
                }
            }
        });

        // mouse instead of valueChanging: a track click/drag changes the value without setting valueChanging
        slider.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> mousePressed = true);
        slider.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> {
            mousePressed = false;
            commit();
        });

        slider.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
                reset();
            }
        });
    }

    private void setupField() {
        valueField.setOnAction(e -> applyText());
        valueField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                valueField.setText(String.format(Locale.ROOT, "%.0f", value()));
                slider.requestFocus();
            }
        });
        valueField.focusedProperty().addListener((obs, wasFocused, focused) -> {
            if (focused) {
                Platform.runLater(valueField::selectAll);
            } else {
                applyText();
            }
        });

        valueField.setTextFormatter(new TextFormatter<String>(change ->
                NUMBER.matcher(change.getControlNewText()).matches() ? change : null));
        valueField.setText(String.format(Locale.ROOT, "%.0f", value()));
    }

    private void applyText() {
        try {
            float typed = Float.parseFloat(valueField.getText());
            slider.setValue(param.toSliderPosition(Math.clamp(typed, param.getMinVal(), param.getMaxVal())));
        } catch (NumberFormatException ignored) {

        }
        valueField.setText(String.format(Locale.ROOT, "%.0f", value()));
    }

    private void commit() {
        float value = value();
        if (value != lastCommitted) {
            lastCommitted = value;
            editingManager.commit(param, value);
            onChanged.run();
        }
    }
}
