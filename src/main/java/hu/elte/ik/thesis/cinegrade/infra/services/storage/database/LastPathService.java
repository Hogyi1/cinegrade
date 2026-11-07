package hu.elte.ik.thesis.cinegrade.infra.services.storage.database;

import hu.elte.ik.thesis.cinegrade.domain.enums.ErrorCode;
import hu.elte.ik.thesis.cinegrade.domain.exceptions.CineGradeException;
import hu.elte.ik.thesis.cinegrade.infra.database.AppDatabase;
import hu.elte.ik.thesis.cinegrade.infra.database.dao.app.SettingsDao;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

public class LastPathService {

    private final SettingsDao settingsDao;
    private static final String EXPORT_LAST_FOLDER = "export.last_folder";
    private static final String EXPORT_LAST_LUT_FOLDER = "export.last_lut_folder";
    private static final String IMPORT_LAST_FOLDER = "import.last_folder";
    private static final String APP_PROJECT_PATH = "app.project_path";

    public LastPathService(Connection connection) {
        settingsDao = new SettingsDao(connection);
    }

    public LastPathService() {
        settingsDao = new SettingsDao(AppDatabase.getInstance().getConnection());
    }

    public Optional<Path> get(String key) {
        try {
            return Optional.ofNullable(settingsDao.get(key))
                    .filter(value -> !value.isBlank())
                    .map(Path::of)
                    .filter(Files::isDirectory);
        } catch (SQLException ex) {
            throw new CineGradeException(ErrorCode.DB_RECORD_NOT_FOUND, key);
        }
    }

    public void set(String key, Path path) {
        try {
            settingsDao.set(key, path.toString());
        } catch (SQLException ex) {
            throw new CineGradeException(ErrorCode.DB_EXECUTION_FAILED, ex, "set: " + key);
        }
    }

    public Optional<Path> getExportLastFolder() {
        return get(EXPORT_LAST_FOLDER);
    }

    public Optional<Path> getExportLastLutFolder() {
        return get(EXPORT_LAST_LUT_FOLDER);
    }

    public Optional<Path> getImportLastFolder() {
        return get(IMPORT_LAST_FOLDER);
    }

    public Optional<Path> getAppProjectPath() {
        return get(APP_PROJECT_PATH);
    }

    public void setExportLastFolder(Path folder) {
        set(EXPORT_LAST_FOLDER, folder);
    }

    public void setExportLastLutFolder(Path folder) {
        set(EXPORT_LAST_LUT_FOLDER, folder);
    }

    public void setImportLastFolder(Path folder) {
        set(IMPORT_LAST_FOLDER, folder);
    }

    public void setAppProjectPath(Path path) {
        set(APP_PROJECT_PATH, path);
    }
}
