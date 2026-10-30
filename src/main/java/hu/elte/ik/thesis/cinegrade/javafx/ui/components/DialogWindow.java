package hu.elte.ik.thesis.cinegrade.javafx.ui.components;

import hu.elte.ik.thesis.cinegrade.domain.theme.ThemeManager;
import javafx.geometry.Bounds;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

/**
 * Borderless, transparent window for the custom dialogs ({@link ConfirmDialog}, {@link CommentEditor}).
 * <p>
 * The content sits in a transparent host with some padding, so its rounded corners and drop shadow are not cut
 * off. The scene gets the app stylesheets like every other scene.
 */
public final class DialogWindow {

    // Room around the content for the drop shadow
    private static final double SHADOW_ROOM = 24;

    private DialogWindow() {
    }

    /**
     * @param owner the window the dialog belongs to (closes and minimizes with it)
     * @param modal blocks the owner while it is open
     */
    public static Stage create(Window owner, Parent content, boolean modal) {
        StackPane host = new StackPane(content);
        host.getStyleClass().add("dialog-host");
        host.setStyle("-fx-padding: " + SHADOW_ROOM + "px;");

        Scene scene = new Scene(host);
        scene.setFill(Color.TRANSPARENT);
        ThemeManager.setupStylesheet(scene);

        Stage stage = new Stage(StageStyle.TRANSPARENT);
        stage.initOwner(owner);
        stage.initModality(modal ? Modality.WINDOW_MODAL : Modality.NONE);
        stage.setResizable(false);
        stage.setScene(scene);
        return stage;
    }

    /**
     * Centers the stage over its owner. Call after the stage is shown (its size is known then).
     */
    public static void centerOnOwner(Stage stage) {
        Window owner = stage.getOwner();
        if (owner == null) {
            stage.centerOnScreen();
            return;
        }
        stage.setX(owner.getX() + (owner.getWidth() - stage.getWidth()) / 2);
        stage.setY(owner.getY() + (owner.getHeight() - stage.getHeight()) / 2);
    }

    /**
     * Places the stage right above the anchor node, horizontally centered on it and kept on the screen.
     * Call after the stage is shown.
     */
    public static void placeAbove(Stage stage, Node anchor) {
        Bounds bounds = anchor.localToScreen(anchor.getBoundsInLocal());
        if (bounds == null) {
            centerOnOwner(stage);
            return;
        }
        Rectangle2D screen = Screen.getScreensForRectangle(bounds.getMinX(), bounds.getMinY(), 1, 1).stream()
                .findFirst().orElse(Screen.getPrimary()).getVisualBounds();

        // The shadow room is transparent, so the visible box starts SHADOW_ROOM inside the stage
        double x = bounds.getMinX() + bounds.getWidth() / 2 - stage.getWidth() / 2;
        double y = bounds.getMinY() - stage.getHeight() + SHADOW_ROOM - 6;
        stage.setX(Math.max(screen.getMinX(), Math.min(x, screen.getMaxX() - stage.getWidth())));
        stage.setY(Math.max(screen.getMinY(), y));
    }
}
