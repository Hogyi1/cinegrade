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

    private static final String CREATE_TABLE_REFRESH_TOKENS = """
            CREATE TABLE IF NOT EXISTS refresh_tokens (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                refresh_token TEXT NOT NULL,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                last_used_at DATETIME DEFAULT CURRENT_TIMESTAMP
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
                refresh_token_id INTEGER NOT NULL REFERENCES refresh_tokens(id) ON DELETE CASCADE,
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
            new SchemaEntity("refresh_tokens", CREATE_TABLE_REFRESH_TOKENS),
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

    private static void executeStatement(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.execute();
        }
    }
}
