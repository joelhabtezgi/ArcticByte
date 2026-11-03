
package se.lu.ics.Data;

import se.lu.ics.Models.Milestone;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class MilestoneDao {
    private ConnectionHandler connectionHandler;

    public MilestoneDao() throws IOException {
        this.connectionHandler = new ConnectionHandler();
    }

    // Helper method to build Milestone from ResultSet
    private Milestone mapToMilestone(ResultSet rs) throws SQLException {
        return new Milestone(
                rs.getInt("MilestoneID"),
                rs.getInt("MilestoneNo"),      // Changed from getString to getInt
                rs.getString("MilestoneName"),
                rs.getString("MilestoneDate"),
                rs.getString("Status"),
                rs.getInt("ProjectID")
        );
    }


    // Get all milestones
    public List<Milestone> findAll() {
        String query = "SELECT MilestoneID, MilestoneNo, MilestoneName, MilestoneDate, Status, ProjectID FROM Milestone";
        List<Milestone> milestones = new ArrayList<>();

        try (Connection conn = connectionHandler.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                milestones.add(mapToMilestone(rs));
            }

        } catch (SQLException e) {
            throw new DaoException("Error retrieving all milestones", e);
        }
        return milestones;
    }

    // Get milestone by ID
    public Milestone findById(int id) {
        String query = "SELECT MilestoneID, MilestoneNo, MilestoneName, MilestoneDate, Status, ProjectID FROM Milestone WHERE MilestoneID = ?";
        Milestone milestone = null;

        try (Connection conn = connectionHandler.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                milestone = mapToMilestone(rs);
            }

        } catch (SQLException e) {
            throw new DaoException("Error retrieving milestone with ID " + id, e);
        }
        return milestone;
    }

    // Add new milestone
    public void save(Milestone milestone) {
        String query = "INSERT INTO Milestone (MilestoneName, MilestoneDate, Status, ProjectID) VALUES (?, ?, ?, ?)";

        try (Connection conn = connectionHandler.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, milestone.getMilestoneName());
            stmt.setString(2, milestone.getDate());
            stmt.setString(3, milestone.getStatus());
            stmt.setInt(4, milestone.getProjectID());
            stmt.executeUpdate();

        } catch (SQLException e) {
            System.err.println("SQL ERROR in save(): " + e.getMessage());
            throw new DaoException("Error saving milestone: " + e.getMessage(), e);
        }
    }

    // Add new milestone and return with generated ID
    public Milestone saveAndReturn(Milestone milestone) throws DaoException {
        String query = "INSERT INTO Milestone (MilestoneNo, MilestoneName, MilestoneDate, Status, ProjectID) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = connectionHandler.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setInt(1, milestone.getMilestoneNo());
            stmt.setString(2, milestone.getMilestoneName());
            stmt.setString(3, milestone.getDate());
            stmt.setString(4, milestone.getStatus());
            stmt.setInt(5, milestone.getProjectID());
            
            int affectedRows = stmt.executeUpdate();
            if (affectedRows == 0) {
                throw new DaoException("Saving milestone failed, no rows affected.");
            }

            // Get the generated ID
            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    int generatedId = generatedKeys.getInt(1);
                    // Returnera en ny Milestone med det genererade ID:t
                    return new Milestone(generatedId, milestone.getMilestoneNo(), milestone.getMilestoneName(), 
                                       milestone.getDate(), milestone.getStatus(), milestone.getProjectID());
                } else {
                    throw new DaoException("Failed to retrieve generated ID for milestone.");
                }
            }

        } catch (SQLException e) {
            throw new DaoException("Error saving milestone", e);
        }
    }

    // Update milestone
    public void update(Milestone milestone) {
        String query = "UPDATE Milestone SET MilestoneName = ?, MilestoneDate = ?, Status = ?, ProjectID = ? WHERE MilestoneID = ?";

        try (Connection conn = connectionHandler.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, milestone.getMilestoneName());
            stmt.setString(2, milestone.getDate());
            stmt.setString(3, milestone.getStatus());
            stmt.setInt(4, milestone.getProjectID());
            stmt.setInt(5, milestone.getMilestoneID());
            stmt.executeUpdate();

        } catch (SQLException e) {
            System.err.println("SQL ERROR in update(): " + e.getMessage());
            throw new DaoException("Error updating milestone: " + e.getMessage(), e);
        }
    }

    // Ta bort milestone (B-nivå)
    public void delete(int id) {
        String query = "DELETE FROM Milestone WHERE MilestoneID = ?";

        try (Connection conn = connectionHandler.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setInt(1, id);
            stmt.executeUpdate();

        } catch (SQLException e) {
            throw new DaoException("Error deleting milestone", e);
        }
    }


    // Get milestones for a project, sorted by date
    public List<Milestone> findByProjectSorted(int projectId) {
        String query = "SELECT MilestoneID, MilestoneNo, MilestoneName, MilestoneDate, Status, ProjectID " +
                       "FROM Milestone WHERE ProjectID = ? ORDER BY MilestoneDate ASC";
        List<Milestone> milestones = new ArrayList<>();

        try (Connection conn = connectionHandler.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setInt(1, projectId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                milestones.add(mapToMilestone(rs));
            }

        } catch (SQLException e) {
            throw new DaoException("Error retrieving milestones for project " + projectId, e);
        }
        return milestones;
    }

    // B-level: Count number of milestones for a project
    public int countByProject(int projectId) {
        String query = "SELECT COUNT(*) AS MilestoneCount FROM Milestone WHERE ProjectID = ?";
        int count = 0;

        try (Connection conn = connectionHandler.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setInt(1, projectId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                count = rs.getInt("MilestoneCount");
            }

        } catch (SQLException e) {
            throw new DaoException("Error counting milestones for project " + projectId, e);
        }
        return count;
    }
}