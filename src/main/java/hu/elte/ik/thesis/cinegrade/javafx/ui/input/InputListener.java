package hu.elte.ik.thesis.cinegrade.javafx.ui.input;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Keyboard shortcuts (and simple clicks) of one area of the UI.
 * <p>
 * The handlers sit on the area's root, so they only see key events while the focus is inside it: a key event
 * bubbles from the focused node up to the scene, the deepest listener goes first and a matched shortcut is
 * consumed. That is how Ctrl+C means "comment" in the filmstrip but "copy settings" elsewhere on the develop page.
 */
public class InputListener {

    private final List<InputKeyAction> actions = new ArrayList<>();
    private final List<InputMouseAction> mouseActions = new ArrayList<>();
    private final Node target;
    // When the input applies at all, checked on every event
    private final BooleanSupplier activeWhen;
    // Manual switch on top of activeWhen, e.g. off while a dialog owns the keys
    private volatile boolean isActive = true;

    public InputListener(Node target) {
        this(target, () -> true);
    }

    public InputListener(Node target, BooleanSupplier activeWhen) {
        this.target = target;
        this.activeWhen = activeWhen;

        this.target.addEventHandler(KeyEvent.KEY_PRESSED, this::handleKeyPressed);
        this.target.addEventHandler(MouseEvent.MOUSE_CLICKED, this::handleMouseClicked);
    }


    public static BooleanSupplier focusedNotTyping(Node area) {
        return () -> {
            Scene scene = area.getScene();
            return scene != null && area.isFocusWithin() && !(scene.getFocusOwner() instanceof TextInputControl);
        };
    }

    public void activate() {
        isActive = true;
    }

    public void deactivate() {
        isActive = false;
    }

    public boolean isActive() {
        return isActive && activeWhen.getAsBoolean();
    }

    public void bind(InputKeyAction action) {
        actions.add(action);
    }

    public void bind(KeyCombination keyCombination, Runnable action) {
        actions.add(new InputKeyAction(keyCombination, action, () -> true));
    }

    public void bind(InputMouseAction action) {
        mouseActions.add(action);
    }

    private void handleKeyPressed(KeyEvent e) {
        if (!isActive()) {
            return;
        }
        for (InputKeyAction action : actions) {
            if (action.keyCombination().match(e) && action.constraint().getAsBoolean()) {
                action.action().run();
                e.consume();
                break;
            }
        }
    }

    private void handleMouseClicked(MouseEvent e) {
        if (!isActive()) {
            return;
        }
        for (InputMouseAction action : mouseActions) {
            if (e.getButton() == action.mouseButton()
                    && e.getClickCount() == action.clickCount()
                    && action.constraint().getAsBoolean()) {
                action.action().run();
                e.consume();
                break;
            }
        }
    }

    public record InputKeyAction(KeyCombination keyCombination, Runnable action, BooleanSupplier constraint) {
    }

    public record InputMouseAction(MouseButton mouseButton, int clickCount, Runnable action,
                                   BooleanSupplier constraint) {
    }
}
