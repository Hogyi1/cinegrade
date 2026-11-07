package hu.elte.ik.thesis.cinegrade.app.managers.catalog;

import hu.elte.ik.thesis.cinegrade.domain.listener.ErrorListener;
import hu.elte.ik.thesis.cinegrade.domain.listener.HistoryListener;
import lombok.Setter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.LinkedList;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;

public class HistoryManager {
    private static final int MAX = 100;
    private static Logger logger = LogManager.getLogger(HistoryManager.class);
    private final Set<HistoryListener> listeners = new CopyOnWriteArraySet<>();
    private final LinkedList<HistoryEntry> undoStack = new LinkedList<>() {
        @Override
        public boolean add(HistoryEntry entry) {
            if (size() >= MAX) {
                removeFirst();
            }
            return super.add(entry);
        }
    };
    private final LinkedList<HistoryEntry> redoStack = new LinkedList<>() {
        @Override
        public boolean add(HistoryEntry entry) {
            if (size() >= MAX) {
                removeFirst();
            }
            return super.add(entry);
        }
    };
    private volatile boolean replaying;

    public void registerListener(HistoryListener listener) {
        logger.info("Registered listener for errors: {}", listener);
        listeners.add(listener);
    }

    public void notifyListeners(String text, boolean isUndo) {
        listeners.forEach(listener -> listener.onHistory(text, isUndo));
    }

    public void record(HistoryEntry entry) {
        if (replaying)
            return;
        undoStack.addLast(entry);
        if (undoStack.size() > MAX) undoStack.removeFirst();
        redoStack.clear();
        notifyListeners(entry.description(), false);
    }

    public void undo() {
        if (undoStack.isEmpty()) {
            notifyListeners("Nothing to undo", true);
            return;
        }
        HistoryEntry entry = undoStack.removeLast();
        replay(entry.undo());
        redoStack.addLast(entry);
        notifyListeners("Undo: " + entry.description(), true);
    }

    public void redo() {
        if (redoStack.isEmpty()) {
            notifyListeners("Nothing to redo", false);
            return;
        }
        HistoryEntry entry = redoStack.removeLast();
        replay(entry.redo());
        undoStack.addLast(entry);
        notifyListeners("Redo: " + entry.description(), false);
    }

    /**
     * The photo left the catalog: its steps can't be undone anymore.
     */
    public void forgetPhoto(String photoId) {
        undoStack.removeIf(entry -> entry.photoIds().contains(photoId));
        redoStack.removeIf(entry -> entry.photoIds().contains(photoId));
    }

    private void replay(Runnable action) {
        replaying = true;
        try {
            action.run();
        } finally {
            replaying = false;
        }
    }

    public record HistoryEntry(String description, Set<String> photoIds, Runnable undo, Runnable redo) {
    }
}
