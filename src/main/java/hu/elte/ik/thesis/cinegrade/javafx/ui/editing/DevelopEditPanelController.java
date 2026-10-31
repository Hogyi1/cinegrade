package hu.elte.ik.thesis.cinegrade.javafx.ui.editing;

import hu.elte.ik.thesis.cinegrade.app.managers.editing.EditingManager;
import hu.elte.ik.thesis.cinegrade.domain.editing.EditState;
import hu.elte.ik.thesis.cinegrade.engine.GradeParam;
import hu.elte.ik.thesis.cinegrade.javafx.ui.components.EditPane;
import hu.elte.ik.thesis.cinegrade.javafx.ui.components.EditSlider;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Right panel of the development page: histogram and the adjustment groups.
 */
public class DevelopEditPanelController {

    private static final Logger logger = LogManager.getLogger(DevelopEditPanelController.class);
    private final EditingManager editingManager;
    private final Map<GradeParam, EditSlider> sliders = new EnumMap<>(GradeParam.class);

    @FXML
    private VBox editPanelRoot;
    @FXML
    private Label cameraExifLabel;
    @FXML
    private Canvas histogramCanvas;
    @FXML
    private VBox panelsContainer;
    @FXML
    private VBox curveContent;
    @FXML
    private ToggleButton curveRgbBtn;
    @FXML
    private ToggleButton curveRedBtn;
    @FXML
    private ToggleButton curveGreenBtn;
    @FXML
    private ToggleButton curveBlueBtn;
    @FXML
    private Canvas curveCanvas;
    @FXML
    private Button resetCurveBtn;

    public DevelopEditPanelController(EditingManager editingManager) {
        this.editingManager = editingManager;
    }

    @FXML
    private void initialize() {
        buildPanes();
        editingManager.currentEditStateProperty().addListener((obs, oldState, newState) -> showEditState(newState));
        showEditState(editingManager.currentEditStateProperty().get());
    }

    /**
     * One EditPane per GradeParam.Section, one header per Group, one EditSlider per param.
     * The tone curve is not a GradeParam, its body comes from the FXML.
     */
    private void buildPanes() {
        Map<GradeParam.Section, EditPane> panes = new EnumMap<>(GradeParam.Section.class);
        for (GradeParam.Section section : GradeParam.Section.values()) {
            EditPane pane = new EditPane(section.getTitle());
            pane.setExpanded(section == GradeParam.Section.BASIC);
            pane.enabledProperty().addListener((obs, wasOn, on) -> editingManager.setSectionEnabled(section, on));
            panes.put(section, pane);
        }

        for (GradeParam.Group group : GradeParam.Group.values()) {
            List<Node> rows = new ArrayList<>();
            for (GradeParam param : GradeParam.values()) {
                if (param.getGroup() != group) {
                    continue;
                }
                EditSlider slider = new EditSlider(param,
                        value -> editingManager.preview(param, value),
                        value -> editingManager.commit(param, value));
                sliders.put(param, slider);
                rows.add(group == GradeParam.Group.WHEELS ? wheelCard(param, slider) : slider);
            }
            panes.get(group.getSection()).addGroup(group.getTitle(), rows);
        }

        // TODO: wire the curve eye once the tone curve is in EditState / the shader
        EditPane curvePane = new EditPane("Tone Curve");
        curvePane.addGroup(null, List.of(curveContent));

        panelsContainer.getChildren().setAll(
                panes.get(GradeParam.Section.BASIC),
                curvePane,
                panes.get(GradeParam.Section.COLOR_WHEELS),
                panes.get(GradeParam.Section.DETAIL));
    }

    // [AI-GENERATED METHOD]

    /**
     * Wheel card: title, the disc (placeholder until the ColorWheel component exists) and the level slider.
     */
    private VBox wheelCard(GradeParam param, EditSlider slider) {
        String range = switch (param) {
            case LIFT -> "SHADOWS";
            case GAMMA -> "MIDTONES";
            case GAIN -> "HIGHLIGHTS";
            default -> param.getName().toUpperCase();
        };
        Label title = new Label(range + " (" + param.getName().toUpperCase() + ")");
        title.getStyleClass().add("wheel-title");

        StackPane disc = new StackPane();
        disc.getStyleClass().add("wheel-disc-placeholder");

        VBox card = new VBox(title, disc, slider);
        card.getStyleClass().add("wheel-card");
        return card;
    }

    // [AI-GENERATED METHOD]

    /**
     * Shows the selected photo's edit on the sliders without firing preview/commit. No photo: panel disabled.
     */
    private void showEditState(EditState editState) {
        panelsContainer.setDisable(editState == null);
        for (EditSlider slider : sliders.values()) {
            GradeParam param = slider.getParam();
            slider.setValue(editState == null ? param.getDefVal() : param.read(editState));
        }
    }
}
