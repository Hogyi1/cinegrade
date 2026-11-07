package hu.elte.ik.thesis.cinegrade.app.managers.presets;

import hu.elte.ik.thesis.cinegrade.domain.objects.EditState;
import hu.elte.ik.thesis.cinegrade.domain.objects.PhotoEntry;
import hu.elte.ik.thesis.cinegrade.domain.objects.Preset;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import lombok.Getter;

import static hu.elte.ik.thesis.cinegrade.app.managers.presets.PresetManager.BUILT_IN_CATEGORY;

import java.util.List;
import java.util.function.Predicate;

@Getter
public class PresetStage {
    private final ObservableList<Preset> masterEntries =
            FXCollections.observableArrayList(p -> new Observable[]{p.nameProperty(), p.categoryProperty()});
    private final FilteredList<Preset> filteredEntries = new FilteredList<>(masterEntries);
    private final ObservableList<String> categories = FXCollections.observableArrayList();
    private final StringProperty nameProperty = new SimpleStringProperty("");

    public PresetStage() {
        filteredEntries.predicateProperty().bind(Bindings.createObjectBinding(() -> {
            String nameFilter = nameProperty.get().trim().toLowerCase();
            return (Predicate<Preset>) entry -> nameFilter.isEmpty() || entry.getName().toLowerCase().contains(nameFilter);
        }, nameProperty));
    }

    public void setAll(List<String> categoryNames, List<Preset> presets) {
        categories.setAll(categoryNames);
        masterEntries.setAll(presets);
    }

    public void add(Preset preset) {
        addCategory(preset.getCategory());
        masterEntries.add(preset);
    }

    public void remove(Preset preset) {
        masterEntries.remove(preset);
    }

    public void addCategory(String name) {
        if (find(name) == null) {
            categories.add(name);
        }
    }

    /**
     * The categories a preset can be saved or moved into (no built-in ones).
     */
    public List<String> userCategories() {
        return categories.stream().filter(category -> !category.equalsIgnoreCase(BUILT_IN_CATEGORY)).toList();
    }

    public void removeCategory(String name) {
        categories.removeIf(category -> category.equalsIgnoreCase(name));
    }

    /**
     * The existing spelling of a category name (names ignore case, like in the DB), or {@code null}.
     */
    public String find(String name) {
        for (String category : categories) {
            if (category.equalsIgnoreCase(name)) {
                return category;
            }
        }
        return null;
    }
}
