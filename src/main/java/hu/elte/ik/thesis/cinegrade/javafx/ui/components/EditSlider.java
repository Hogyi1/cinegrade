// [AI-GENERATED]
package hu.elte.ik.thesis.cinegrade.javafx.ui.components;

import hu.elte.ik.thesis.cinegrade.domain.enums.ErrorCode;
import hu.elte.ik.thesis.cinegrade.domain.exceptions.CineGradeException;
import hu.elte.ik.thesis.cinegrade.engine.GradeParam;
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

import java.io.IOException;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * One row of the edit panel: name, slider, typed value and unit of a {@link GradeParam}.
 * <p>
 * onPreview fires on every value change (the viewport renderer keeps only the latest), onCommitted once the value
 * is final: mouse released, Enter / focus lost in the field, a keyboard step or a double-click reset.
 * {@link #setValue} (loading another photo) fires neither.
 */
public class EditSlider extends HBox {

    private static final String FXML_PATH = "/fxmls/editSlider.fxml";
    private static final Pattern NUMBER = Pattern.compile("-?(([1-9][0-9]*)|0)?(\\.[0-9]*)?");

    @Getter
    private final GradeParam param;
    private final Consumer<Float> onPreview;
    private final Consumer<Float> onCommitted;
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

    public EditSlider(GradeParam param, Consumer<Float> onPreview, Consumer<Float> onCommitted) {
        this.param = param;
        this.onPreview = onPreview;
        this.onCommitted = onCommitted;

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
        unitLabel.setVisible(!param.getUnit().isEmpty());
        unitLabel.managedProperty().bind(unitLabel.visibleProperty());

        slider.setMin(param.getMinVal());
        slider.setMax(param.getMaxVal());
        slider.setValue(param.getDefVal());
        slider.setBlockIncrement((param.getMaxVal() - param.getMinVal()) / 100.0);
        lastCommitted = param.getDefVal();

        valueField.setTextFormatter(new TextFormatter<String>(change ->
                NUMBER.matcher(change.getControlNewText()).matches() ? change : null));
        showValue(slider.getValue());

        setupSlider();
        setupField();
    }

    /**
     * Shows a value without firing any callback (e.g. the newly selected photo's edit).
     */
    public void setValue(float value) {
        silent = true;
        try {
            slider.setValue(value);
        } finally {
            silent = false;
        }
        lastCommitted = (float) slider.getValue();
        showValue(slider.getValue());
    }

    public void reset() {
        slider.setValue(param.getDefVal());
        commit();
    }

    private void setupSlider() {
        slider.valueProperty().addListener((obs, oldValue, newValue) -> {
            showValue(newValue.doubleValue());
            if (silent) {
                return;
            }
            onPreview.accept(newValue.floatValue());
            // keyboard steps have no release, they are final right away
            if (!mousePressed) {
                commit();
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
                showValue(slider.getValue());
                slider.requestFocus();
            }
        });
        valueField.focusedProperty().addListener((obs, wasFocused, focused) -> {
            if (!focused) {
                applyText();
            }
        });
    }

    private void applyText() {
        try {
            float typed = Float.parseFloat(valueField.getText());
            slider.setValue(Math.clamp(typed, param.getMinVal(), param.getMaxVal()));
        } catch (NumberFormatException ignored) {
            // "-", "." or empty: fall back to the current value
        }
        showValue(slider.getValue());
    }

    private void commit() {
        float value = (float) slider.getValue();
        if (value != lastCommitted) {
            lastCommitted = value;
            onCommitted.accept(value);
        }
    }

    private void showValue(double value) {
        int decimals = param.getMaxVal() - param.getMinVal() > 20 ? 0 : 2;
        valueField.setText(String.format(Locale.ROOT, "%." + decimals + "f", value));
    }
}
