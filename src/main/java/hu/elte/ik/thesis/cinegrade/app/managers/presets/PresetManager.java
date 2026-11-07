package hu.elte.ik.thesis.cinegrade.app.managers.presets;

import hu.elte.ik.thesis.cinegrade.app.managers.editing.EditingManager;
import hu.elte.ik.thesis.cinegrade.domain.enums.ErrorCode;
import hu.elte.ik.thesis.cinegrade.domain.error.ErrorHandler;
import hu.elte.ik.thesis.cinegrade.domain.exceptions.CineGradeException;
import hu.elte.ik.thesis.cinegrade.domain.objects.EditState;
import hu.elte.ik.thesis.cinegrade.domain.objects.Preset;
import hu.elte.ik.thesis.cinegrade.engine.GradeParam;
import hu.elte.ik.thesis.cinegrade.infra.database.TransactionRunner;
import hu.elte.ik.thesis.cinegrade.infra.services.storage.database.PresetService;
import javafx.application.Platform;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class PresetManager {

    public static final String DEFAULT_CATEGORY = "User presets";
    public static final String BUILT_IN_CATEGORY = "Built-in";
    private static final int VERSION_NUMBER = 1;
    private final PresetStage viewModel;
    private final EditingManager editingManager;
    private final PresetService presetService;
    private final TransactionRunner appTx;

    public PresetManager(EditingManager editingManager, PresetService presetService, PresetStage presetStage, TransactionRunner appTx) {
        this.viewModel = presetStage;
        this.editingManager = editingManager;
        this.presetService = presetService;
        this.appTx = appTx;

        loadAll();
    }

    public PresetStage getPresetStage() {
        return viewModel;
    }

    private void loadAll() {
        appTx.read(c -> {
                    List<String> categories = new ArrayList<>(presetService.loadCategories());
                    List<Preset> presets = new ArrayList<>(presetService.loadPresets());
                    presets.addAll(presetService.loadBuiltIns());
                    // a broken one is skipped, it must not hide the others
                    presets.removeIf(p -> {
                        try {
                            validate(p);
                            return false;
                        } catch (CineGradeException ex) {
                            return true;
                        }
                    });
                    // built-in packs are not in the DB; they come first, then the saved categories
                    List<String> ordered = new ArrayList<>();
                    for (Preset p : presets) {
                        if (p.isBuiltIn() && ordered.stream().noneMatch(p.getCategory()::equalsIgnoreCase)) {
                            ordered.add(p.getCategory());
                        }
                    }
                    for (String category : categories) {
                        if (ordered.stream().noneMatch(category::equalsIgnoreCase)) {
                            ordered.add(category);
                        }
                    }
                    categories = ordered;
                    return new Loaded(categories, presets);
                }).thenAcceptAsync(loaded -> viewModel.setAll(loaded.categories(), loaded.presets()), Platform::runLater)
                .exceptionally(this::fail);
    }

    public void createFromCurrent(String name, String category, String author, String description, Set<GradeParam> included, boolean includeWb) {
        EditState live = viewModel.getCurrentEditStateProperty().get();
        if (live == null || name == null || name.isBlank()) {
            return;
        }
        Preset preset = new Preset(UUID.randomUUID(), VERSION_NUMBER, name.strip(), category(category), Instant.now());
        preset.setAuthor(author);
        preset.setDescription(description);
        preset.setEditState(new EditState(null, live));
        preset.setIncluded(new HashSet<>(included));
        preset.setIncludeWb(includeWb);
        appTx.execute(c -> presetService.save(preset))
                .thenRunAsync(() -> viewModel.add(preset), Platform::runLater)
                .exceptionally(this::fail);
    }

    public void rename(Preset preset, String name) {
        if (name == null || name.isBlank()) {
            return;
        }
        preset.setName(name.strip());
        save(preset);
    }

    public void moveTo(Preset preset, String category) {
        preset.setCategory(category(category));
        save(preset);
    }

    /**
     * Deletes a category that has no presets; User presets always stays.
     */
    public void deleteCategory(String name) {
        if (name == null || name.equalsIgnoreCase(DEFAULT_CATEGORY)
                || viewModel.getMasterEntries().stream().anyMatch(p -> p.getCategory().equalsIgnoreCase(name))) {
            return;
        }
        appTx.execute(c -> presetService.deleteCategory(name))
                .thenRunAsync(() -> viewModel.removeCategory(name), Platform::runLater)
                .exceptionally(this::fail);
    }

    public void remove(Preset preset) {
        appTx.execute(c -> presetService.delete(preset.getId()))
                .thenRunAsync(() -> viewModel.remove(preset), Platform::runLater)
                .exceptionally(this::fail);
    }

    /**
     * Always added as a new preset, into the category written in the file (created if it does not exist).
     */
    public void importFile(Path file) {
        appTx.inTransaction(c -> {
            Preset p = presetService.read(file);
            validate(p);
            p.setId(UUID.randomUUID());
            p.setCategory(category(p.getCategory()));
            presetService.save(p);
            return p;
        }).thenAcceptAsync(viewModel::add, Platform::runLater).exceptionally(this::fail);
    }

    public void exportFile(Preset preset, Path file) {
        appTx.execute(c -> presetService.write(preset, file)).exceptionally(this::fail);
    }

    public void apply(Preset preset) {
        editingManager.applyPreset(preset);
    }

    private void save(Preset preset) {
        preset.setUpdatedAt(Instant.now());
        appTx.execute(c -> presetService.save(preset))
                .thenRunAsync(() -> viewModel.addCategory(preset.getCategory()), Platform::runLater)
                .exceptionally(this::fail);
    }

    /**
     * Plain text: stripped, empty goes to User presets, and an existing name keeps its spelling.
     */
    private String category(String name) {
        // a built-in category is read-only, so it falls back to User presets too
        if (name == null || name.isBlank() || BUILT_IN_CATEGORY.equalsIgnoreCase(name.strip())) {
            return DEFAULT_CATEGORY;
        }
        String existing = viewModel.find(name.strip());
        return existing != null ? existing : name.strip();
    }

    /**
     * Files and rows can come from someone else: missing fields, unknown names, values out of range.
     */
    private void validate(Preset p) {
        if (p.getId() == null || p.getName() == null || p.getName().isBlank() || p.getEditState() == null) {
            throw new CineGradeException(ErrorCode.PRESET_LOAD_FAILED, p.getName());
        }
        if (p.getCategory() == null || p.getCategory().isBlank()) {
            p.setCategory(DEFAULT_CATEGORY);
        }
        p.getIncluded().remove(null);
        for (GradeParam g : p.getIncluded()) {
            g.write(p.getEditState(), g.read(p.getEditState()));
        }
    }

    private Void fail(Throwable throwable) {
        ErrorHandler.getInstance().handle(throwable);
        return null;
    }

    private record Loaded(List<String> categories, List<Preset> presets) {
    }
}
