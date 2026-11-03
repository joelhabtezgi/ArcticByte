package se.lu.ics.Data;

import se.lu.ics.Models.Title;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class TitleDao {
    private final ConnectionHandler connectionHandler;

    public TitleDao() throws IOException {
        this.connectionHandler = new ConnectionHandler();
    }

    // --- Helper ---
    private Title mapToTitle(ResultSet rs) throws SQLException {
        return new Title(
                rs.getInt("TitleID"),
                rs.getString("TitleName")
        );
    }

    // --- CRUD ---
    public List<Title> findAll() {
        String query = "SELECT TitleID, TitleName FROM Title";
        List<Title> titles = new ArrayList<>();
        try (Connection conn = connectionHandler.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) titles.add(mapToTitle(rs));
        } catch (SQLException e) {
            throw new DaoException("Error fetching all titles", e);
        }
        return titles;
    }

    public Title findById(int id) {
        String query = "SELECT TitleID, TitleName FROM Title WHERE TitleID = ?";
        try (Connection conn = connectionHandler.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? mapToTitle(rs) : null;
        } catch (SQLException e) {
            throw new DaoException("Error fetching title with ID " + id, e);
        }
    }

    public void save(Title title) {
        String query = "INSERT INTO Title (TitleName) VALUES (?)";
        try (Connection conn = connectionHandler.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, title.getTitleName());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new DaoException("Error saving title", e);
        }
    }

    public void update(Title title) {
        String query = "UPDATE Title SET TitleName = ? WHERE TitleID = ?";
        try (Connection conn = connectionHandler.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, title.getTitleName());
            stmt.setInt(2, title.getTitleID());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new DaoException("Error updating title", e);
        }
    }

    public void delete(int id) {
        String query = "DELETE FROM Title WHERE TitleID = ?";
        try (Connection conn = connectionHandler.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new DaoException("Error deleting title", e);
        }
    }

    // --- Utility ---
    public int getOrCreateTitleId(String titleName) {
        String selectQuery = "SELECT TitleID FROM Title WHERE TitleName = ?";
        String insertQuery = "INSERT INTO Title (TitleName, TitleNo) VALUES (?, ?)";
        try (Connection conn = connectionHandler.getConnection();
             PreparedStatement selectStmt = conn.prepareStatement(selectQuery)) {

            // Try to fetch existing TitleID
            selectStmt.setString(1, titleName);
            ResultSet rs = selectStmt.executeQuery();
            if (rs.next()) return rs.getInt("TitleID");

            // If not found, insert new
            try (PreparedStatement insertStmt = conn.prepareStatement(insertQuery, Statement.RETURN_GENERATED_KEYS)) {
                insertStmt.setString(1, titleName);
                insertStmt.setInt(2, 0); // Temporary TitleNo
                insertStmt.executeUpdate();
                ResultSet generatedKeys = insertStmt.getGeneratedKeys();
                if (generatedKeys.next()) {
                    int generatedId = generatedKeys.getInt(1);
                    // Update TitleNo to match TitleID
                    String updateQuery = "UPDATE Title SET TitleNo = ? WHERE TitleID = ?";
                    try (PreparedStatement updateStmt = conn.prepareStatement(updateQuery)) {
                        updateStmt.setInt(1, generatedId);
                        updateStmt.setInt(2, generatedId);
                        updateStmt.executeUpdate();
                    }
                    return generatedId;
                }
            }
        } catch (SQLException e) {
            throw new DaoException("Error in getOrCreateTitleId", e);
        }
        return -1;
    }
}
