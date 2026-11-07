// [CONTAINS AI-GENERATED CODE: Table SQL strings]
package hu.elte.ik.thesis.cinegrade.infra.database.schema;

import hu.elte.ik.thesis.cinegrade.domain.enums.ErrorCode;
import hu.elte.ik.thesis.cinegrade.domain.exceptions.CineGradeException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class AppSchemaInitializer {

    private static final Logger logger = LogManager.getLogger(AppSchemaInitializer.class);

    private AppSchemaInitializer() {
    }

    private record SchemaEntity(String name, String sql) {
    }

    // [AI-GENERATED TABLE STRINGS]
    private static final String CREATE_TABLE_APP_SETTINGS = """
            CREATE TABLE IF NOT EXISTS app_settings (
                key TEXT PRIMARY KEY,
                value TEXT
            );
            """;

    private static final String CREATE_TABLE_RECENT_CATALOGS = """
            CREATE TABLE IF NOT EXISTS recent_catalogs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                project_directory TEXT NOT NULL,
                project_file_path TEXT NOT NULL,
                catalog_name TEXT NOT NULL,
                last_opened_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP
            );
            """;

    // One layer of preset categories; a category stays even when it has no presets
    private static final String CREATE_TABLE_PRESET_CATEGORIES = """
            CREATE TABLE IF NOT EXISTS preset_categories (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL UNIQUE COLLATE NOCASE
            );
            """;

    // The first category, always there; new presets go here when no category is given
    private static final String SEED_USER_PRESETS = """
            INSERT OR IGNORE INTO preset_categories (name) VALUES ('User presets');
            """;

    private static final String CREATE_TABLE_PRESETS = """
            CREATE TABLE IF NOT EXISTS presets (
                id TEXT PRIMARY KEY,
                category_id INTEGER NOT NULL REFERENCES preset_categories(id),
                name TEXT NOT NULL,
                edit_parameters_json TEXT NOT NULL,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP
            );
            """;

    private static final String CREATE_TABLE_USER_SESSION = """
            CREATE TABLE IF NOT EXISTS user_session (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                username TEXT NOT NULL,
                email TEXT NOT NULL,
                avatar_url TEXT,
                auth_token TEXT NOT NULL,
                last_login DATETIME DEFAULT CURRENT_TIMESTAMP,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP
            );
            """;

    private static final String CREATE_INDEX_RECENT_CATALOGS_DATE = """
            CREATE INDEX IF NOT EXISTS idx_recent_catalogs_opened ON recent_catalogs(last_opened_at DESC);
            """;

    // A UNIQUE index instead of a column constraint, so migrated tables (ALTER TABLE ADD COLUMN can't add UNIQUE) get it too
    private static final String CREATE_INDEX_RECENT_CATALOGS_FILE = """
            CREATE UNIQUE INDEX IF NOT EXISTS idx_recent_catalogs_file ON recent_catalogs(project_file_path COLLATE NOCASE);
            """;

    private static final SchemaEntity[] TABLES = {
            new SchemaEntity("app_settings", CREATE_TABLE_APP_SETTINGS),
            new SchemaEntity("recent_catalogs", CREATE_TABLE_RECENT_CATALOGS),
            new SchemaEntity("preset_categories", CREATE_TABLE_PRESET_CATEGORIES),
            new SchemaEntity("preset_categories seed", SEED_USER_PRESETS),
            new SchemaEntity("presets", CREATE_TABLE_PRESETS),
            new SchemaEntity("user_session", CREATE_TABLE_USER_SESSION)
    };

    private static final SchemaEntity[] INDEXES = {
            new SchemaEntity("idx_recent_catalogs_opened", CREATE_INDEX_RECENT_CATALOGS_DATE),
            new SchemaEntity("idx_recent_catalogs_file", CREATE_INDEX_RECENT_CATALOGS_FILE)
    };

    public static void initialize(Connection connection) {
        logger.info("Started app schema initialization");

        logger.info("Initializing app tables..");
        for (SchemaEntity table : TABLES) {
            logger.info("Creating app table: {}", table.name());
            try {
                executeStatement(connection, table.sql());
            } catch (SQLException ex) {
                throw new CineGradeException(ErrorCode.FAILED_TO_CREATE_TABLE, ex, table.name());
            }
            logger.info("App table has successfully been created: {}", table.name());
        }

        migrateRecentCatalogs(connection);
        dropGlobalPresets(connection);

        logger.info("Initializing app indexes..");
        for (SchemaEntity index : INDEXES) {
            logger.info("Creating app index: {}", index.name());
            try {
                executeStatement(connection, index.sql());
            } catch (SQLException ex) {
                throw new CineGradeException(ErrorCode.FAILED_TO_CREATE_INDEX, ex, index.name());
            }
            logger.info("App index has successfully been created: {}", index.name());
        }

        logger.info("App schema initialization has successfully finished");
    }

    // [AI-GENERATED METHOD]
    // Old app databases stored only the project directory (catalog_path) and searched it for a .cgproj.
    // Rename it to project_directory and backfill project_file_path as <directory>/<name>.cgproj.
    private static void migrateRecentCatalogs(Connection connection) {
        try {
            if (columnExists(connection, "recent_catalogs", "catalog_path")) {
                logger.info("Migrating recent_catalogs.catalog_path -> project_directory");
                executeStatement(connection, "ALTER TABLE recent_catalogs RENAME COLUMN catalog_path TO project_directory;");
            }
            if (!columnExists(connection, "recent_catalogs", "project_file_path")) {
                logger.info("Migrating recent_catalogs: adding project_file_path");
                executeStatement(connection, "ALTER TABLE recent_catalogs ADD COLUMN project_file_path TEXT;");
                try (PreparedStatement pstmt = connection.prepareStatement(
                        "UPDATE recent_catalogs SET project_file_path = project_directory || ? || catalog_name || '.cgproj' WHERE project_file_path IS NULL;")) {
                    pstmt.setString(1, java.io.File.separator);
                    pstmt.executeUpdate();
                }
            }
        } catch (SQLException ex) {
            throw new CineGradeException(ErrorCode.DB_SCHEMA_INIT_FAILED, ex, "recent_catalogs migration");
        }
    }

    // [AI-GENERATED METHOD]
    // Old app databases have global_presets with an INTEGER id, replaced by presets (TEXT UUID id).
    // Nothing ever wrote to it, so it is dropped instead of copied; its index goes with it.
    private static void dropGlobalPresets(Connection connection) {
        try {
            executeStatement(connection, "DROP TABLE IF EXISTS global_presets;");
        } catch (SQLException ex) {
            throw new CineGradeException(ErrorCode.DB_SCHEMA_INIT_FAILED, ex, "global_presets migration");
        }
    }

    private static boolean columnExists(Connection connection, String table, String column) throws SQLException {
        try (java.sql.Statement stmt = connection.createStatement();
             java.sql.ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void executeStatement(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.execute();
        }
    }
}
