// [AI-GENERATED]
package hu.elte.ik.thesis.cinegrade.javafx.ui.components;

import hu.elte.ik.thesis.cinegrade.domain.enums.ErrorCode;
import hu.elte.ik.thesis.cinegrade.domain.exceptions.CineGradeException;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import lombok.Getter;

import java.io.IOException;
import java.util.Collection;

/**
 * Save-as-preset dialog: name, category, author (read-only), description and the settings to include.
 * <p>
 * Only the form: it does not know about {@code Preset} or {@code GradeParam}, the caller reads the fields and the
 * check boxes after {@link #showAndWait} returned {@code true}:
 * <pre>{@code
 * PresetDialog dialog = new PresetDialog();
 * dialog.setAuthor(username);
 * dialog.setCategories(categoryNames);
 * if (dialog.showAndWait(window)) { dialog.getName(); dialog.getExposureCheck().isSelected(); ... }
 * }</pre>
 */
@Getter
public class PresetDialog extends VBox {

    private static final String FXML_PATH = "/fxmls/presetDialog.fxml";

    @FXML
    private Label titleLabel;
    @FXML
    private TextField nameField;
    @FXML
    private ComboBox<String> categoryBox;
    @FXML
    private TextField authorField;
    @FXML
    private TextField descriptionField;

    @FXML
    private CheckBox whiteBalanceCheck;
    @FXML
    private CheckBox exposureCheck;
    @FXML
    private CheckBox contrastCheck;
    @FXML
    private CheckBox highlightsCheck;
    @FXML
    private CheckBox shadowsCheck;
    @FXML
    private CheckBox whitesCheck;
    @FXML
    private CheckBox blacksCheck;
    @FXML
    private CheckBox vibranceCheck;
    @FXML
    private CheckBox saturationCheck;
    @FXML
    private CheckBox colorMixerCheck;
    @FXML
    private CheckBox hueCheck;
    @FXML
    private CheckBox hslSaturationCheck;
    @FXML
    private CheckBox luminanceCheck;

    @FXML
    private Button cancelButton;
    @FXML
    private Button saveButton;

    private boolean saved;

    public PresetDialog() {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(FXML_PATH));
        loader.setRoot(this);
        loader.setController(this);
        try {
            loader.load();
        } catch (IOException exception) {
            throw new CineGradeException(ErrorCode.REQ_RESOURCE_MISSING, exception, FXML_PATH);
        }
    }

    @FXML
    private void initialize() {
        saveButton.disableProperty().bind(Bindings.createBooleanBinding(
                () -> nameField.getText() == null || nameField.getText().isBlank(), nameField.textProperty()));

        // Color mixer parent: sets all three children, and shows indeterminate when only some are checked
        colorMixerCheck.setOnAction(e -> {
            boolean on = colorMixerCheck.isSelected();
            hueCheck.setSelected(on);
            hslSaturationCheck.setSelected(on);
            luminanceCheck.setSelected(on);
        });
        hueCheck.selectedProperty().addListener((obs, was, on) -> syncColorMixer());
        hslSaturationCheck.selectedProperty().addListener((obs, was, on) -> syncColorMixer());
        luminanceCheck.selectedProperty().addListener((obs, was, on) -> syncColorMixer());
    }

    private void syncColorMixer() {
        int checked = (hueCheck.isSelected() ? 1 : 0) + (hslSaturationCheck.isSelected() ? 1 : 0) + (luminanceCheck.isSelected() ? 1 : 0);
        colorMixerCheck.setIndeterminate(checked == 1 || checked == 2);
        colorMixerCheck.setSelected(checked == 3);
    }

    /**
     * Shows the dialog over the owner and waits.
     *
     * @return {@code true} if Save was pressed, {@code false} on cancel, Escape or closing it
     */
    public boolean showAndWait(Window owner) {
        saved = false;
        Stage stage = DialogWindow.create(owner, this, true);
        stage.setTitle(titleLabel.getText());

        cancelButton.setOnAction(e -> stage.close());
        saveButton.setOnAction(e -> {
            saved = true;
            stage.close();
        });

        stage.setOnShown(e -> {
            DialogWindow.centerOnOwner(stage);
            nameField.requestFocus();
        });
        stage.showAndWait();
        return saved;
    }

    public String getName() {
        return nameField.getText().strip();
    }

    /**
     * @return the picked or typed category, empty if none (the preset goes to User presets)
     */
    public String getCategory() {
        String text = categoryBox.getEditor().getText();
        return text == null ? "" : text.strip();
    }

    /**
     * The saved categories to pick from; the first one is preselected.
     */
    public void setCategories(Collection<String> names) {
        categoryBox.getItems().setAll(names);
        if (!names.isEmpty()) {
            categoryBox.getEditor().setText(names.iterator().next());
        }
    }

    public String getAuthor() {
        return authorField.getText();
    }

    public void setAuthor(String author) {
        authorField.setText(author);
    }

    public String getDescription() {
        return descriptionField.getText().strip();
    }
}
