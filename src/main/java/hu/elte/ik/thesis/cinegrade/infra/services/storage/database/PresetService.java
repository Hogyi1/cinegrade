package hu.elte.ik.thesis.cinegrade.infra.services.storage.database;

import com.google.gson.*;
import hu.elte.ik.thesis.cinegrade.domain.enums.ErrorCode;
import hu.elte.ik.thesis.cinegrade.domain.exceptions.CineGradeException;
import hu.elte.ik.thesis.cinegrade.domain.objects.Preset;
import hu.elte.ik.thesis.cinegrade.infra.database.AppDatabase;
import hu.elte.ik.thesis.cinegrade.infra.database.dao.catalog.PresetDao;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static hu.elte.ik.thesis.cinegrade.app.managers.presets.PresetManager.BUILT_IN_CATEGORY;

public class PresetService {

    // Built-in presets: every .cgedit file in resources/presets
    private static final String BUILT_IN_DIR = "/presets";
    private static final String EXTENSION = ".cgedit";

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Instant.class, (JsonSerializer<Instant>) (v, t, c) -> new JsonPrimitive(v.toString()))
            .registerTypeAdapter(Instant.class, (JsonDeserializer<Instant>) (j, t, c) -> Instant.parse(j.getAsString()))
            .setPrettyPrinting()
            .create();
    private static final Logger logger = LogManager.getLogger(PresetService.class);
    private static final List<String> BUILT_IN_PATHS = List.of(
            "/presets/flat.cgedit",
            "/presets/colorful.cgedit",
            "/presets/bw_high_contrast.cgedit"
    );
    private final PresetDao dao;

    public PresetService() {
        dao = new PresetDao(AppDatabase.getInstance().getConnection());
    }

    public List<String> loadCategories() throws SQLException {
        return dao.findCategoryNames();
    }

    public void addCategory(String name) throws SQLException {
        dao.insertCategory(name);
    }

    public void deleteCategory(String name) throws SQLException {
        dao.deleteEmptyCategory(name);
    }

    public List<Preset> loadPresets() throws SQLException {
        List<Preset> presets = new ArrayList<>();
        for (PresetDao.PresetRow row : dao.findAll()) {
            try {
                Preset preset = GSON.fromJson(row.json(), Preset.class);
                preset.setCategory(row.category());   // the DB link wins over the name in the JSON
                presets.add(preset);
            } catch (JsonParseException ex) {
                logger.warn("Skipping a broken preset row", ex);
            }
        }
        return presets;
    }

    public List<Preset> loadBuiltIns() {
        List<Preset> presets = new ArrayList<>();
        for (String path : BUILT_IN_PATHS) {
            try (InputStream stream = PresetService.class.getResourceAsStream(path)) {
                if (stream == null) {
                    logger.warn("Built-in preset not found: {}", path);
                    continue;
                }
                Preset preset = GSON.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), Preset.class);
                if (preset == null) {
                    logger.warn("Skipping a broken built-in preset: {}", path);
                    continue;
                }
                preset.setBuiltIn(true);
                preset.setCategory(BUILT_IN_CATEGORY);
                presets.add(preset);
            } catch (IOException | JsonParseException ex) {
                logger.warn("Skipping a broken built-in preset: {}", path, ex);
            }
        }
        return presets;
    }

    /**
     * Saves the preset; its category is created first if it does not exist yet.
     */
    public void save(Preset preset) throws SQLException {
        dao.insertCategory(preset.getCategory());
        dao.upsert(preset, GSON.toJson(preset));
    }

    public Path write(Preset preset, Path file) {
        try {
            return Files.writeString(file, GSON.toJson(preset));
        } catch (IOException ex) {
            throw new CineGradeException(ErrorCode.PRESET_SAVE_FAILED, ex, file);
        }
    }

    public Preset read(Path file) {
        try {
            Preset preset = GSON.fromJson(Files.readString(file), Preset.class);
            if (preset == null) {
                throw new CineGradeException(ErrorCode.PRESET_LOAD_FAILED, file);
            }
            return preset;
        } catch (IOException | JsonParseException ex) {
            throw new CineGradeException(ErrorCode.PRESET_LOAD_FAILED, ex, file);
        }
    }

    public void delete(UUID id) throws SQLException {
        dao.delete(id);
    }
}
