// [AI-GENERATED]
package hu.elte.ik.thesis.cinegrade.infra.database.dao.catalog;

import hu.elte.ik.thesis.cinegrade.domain.objects.Preset;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Data access object for the {@code presets} and {@code preset_categories} tables of the application database
 * (presets are global, like in Lightroom).
 * <p>
 * A preset is stored as JSON text (the same as a {@code .cgedit} file) and points to its category by id; parsing the
 * JSON is the service's job. The DAO does not own the {@link Connection}; the caller is responsible for its lifecycle.
 */
public class PresetDao {
    /**
     * Insert-or-update keyed on {@code id}; the category is looked up by name, an update keeps {@code created_at}.
     */
    private static final String UPSERT_SQL = """
            INSERT INTO presets (id, category_id, name, edit_parameters_json, created_at)
            VALUES (?, (SELECT id FROM preset_categories WHERE name = ?), ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                category_id = excluded.category_id, name = excluded.name, edit_parameters_json = excluded.edit_parameters_json
            """;
    /**
     * Connection to the application database.
     */
    private final Connection connection;

    /**
     * One preset row: its JSON and the name of its category.
     */
    public record PresetRow(String json, String category) {
    }

    /**
     * Creates a DAO operating on the given connection.
     *
     * @param connection open connection to the application database
     */
    public PresetDao(Connection connection) {
        this.connection = connection;
    }

    /**
     * Reads every category name, in the order they were created ("User presets" first).
     *
     * @return the category names
     * @throws SQLException if the query fails
     */
    public List<String> findCategoryNames() throws SQLException {
        List<String> names = new ArrayList<>();
        try (PreparedStatement pstmt = connection.prepareStatement("SELECT name FROM preset_categories ORDER BY id");
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                names.add(rs.getString("name"));
            }
        }
        return names;
    }

    /**
     * Adds a category; nothing happens if one with the same name (ignoring case) exists.
     *
     * @param name the category name
     * @throws SQLException if the write fails
     */
    public void insertCategory(String name) throws SQLException {
        try (PreparedStatement pstmt = connection.prepareStatement("INSERT OR IGNORE INTO preset_categories (name) VALUES (?)")) {
            pstmt.setString(1, name);
            pstmt.executeUpdate();
        }
    }

    /**
     * Deletes a category, only if no preset points to it.
     *
     * @param name the category name (ignoring case)
     * @return {@code true} if it was deleted
     * @throws SQLException if the write fails
     */
    public boolean deleteEmptyCategory(String name) throws SQLException {
        String sql = """
                DELETE FROM preset_categories
                WHERE name = ? AND NOT EXISTS (SELECT 1 FROM presets p WHERE p.category_id = preset_categories.id)
                """;
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, name);
            return pstmt.executeUpdate() > 0;
        }
    }

    /**
     * Reads every preset with its category name, sorted by name.
     *
     * @return all presets, empty if there are none
     * @throws SQLException if the query fails
     */
    public List<PresetRow> findAll() throws SQLException {
        String sql = """
                SELECT p.edit_parameters_json AS json, c.name AS category
                FROM presets p JOIN preset_categories c ON c.id = p.category_id
                ORDER BY p.name COLLATE NOCASE
                """;
        List<PresetRow> rows = new ArrayList<>();
        try (PreparedStatement pstmt = connection.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                rows.add(new PresetRow(rs.getString("json"), rs.getString("category")));
            }
        }
        return rows;
    }

    /**
     * Inserts the preset, or updates its category, name and JSON if one with the same id exists.
     * Its category must exist already ({@link #insertCategory}).
     *
     * @param preset the preset to store (id, category, name and createdAt are read from it)
     * @param json   the whole preset as JSON
     * @throws SQLException if the write fails
     */
    public void upsert(Preset preset, String json) throws SQLException {
        try (PreparedStatement pstmt = connection.prepareStatement(UPSERT_SQL)) {
            pstmt.setString(1, preset.getId().toString());
            pstmt.setString(2, preset.getCategory());
            pstmt.setString(3, preset.getName());
            pstmt.setString(4, json);
            pstmt.setString(5, (preset.getCreatedAt() != null ? preset.getCreatedAt() : Instant.now()).toString());
            pstmt.executeUpdate();
        }
    }

    /**
     * Deletes a preset.
     *
     * @param id the preset's id
     * @return {@code true} if a row was deleted
     * @throws SQLException if the write fails
     */
    public boolean delete(UUID id) throws SQLException {
        try (PreparedStatement pstmt = connection.prepareStatement("DELETE FROM presets WHERE id = ?")) {
            pstmt.setString(1, id.toString());
            return pstmt.executeUpdate() > 0;
        }
    }
}
