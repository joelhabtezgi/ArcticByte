
package se.lu.ics.Data;

import se.lu.ics.Models.Project;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ProjectDao {
    // Hjälpmetod för att bygga Project från ResultSet
    private Project mapToProject(ResultSet rs) throws SQLException {
        return new Project(
                rs.getInt("ProjectID"), //PK från databasen
                rs.getInt("ProjectNo"), // unikt nummer för projektet som används i UI
                rs.getString("ProjectName"),
                rs.getString("StartDate"),
                rs.getString("EndDate")
        );
    }

    // Koppla konsult till projekt
    public boolean assignToProject(int employeeId, int projectId) {
        String query = "INSERT INTO Work (EmployeeID, ProjectID, Hours) VALUES (?, ?, 0)";
        try (Connection conn = connectionHandler.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, employeeId);
            stmt.setInt(2, projectId);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            throw new DaoException("Fel vid koppling av konsult till projekt", e);
        }
    }
    private ConnectionHandler connectionHandler;

    public ProjectDao() throws IOException {
        this.connectionHandler = new ConnectionHandler();
    }

    // Hjälpmetod för att bygga Project från ResultSet




    // Hämta alla projekt
    public List<Project> findAll() {
        String query = "SELECT ProjectID, ProjectNo, ProjectName, StartDate, EndDate FROM Project";
        List<Project> projects = new ArrayList<>();

        try (Connection conn = connectionHandler.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                projects.add(mapToProject(rs));
            }

        } catch (SQLException e) {
            throw new DaoException("Fel vid hämtning av alla projekt", e);
        }
        return projects;
    }

    // Hämta projekt via ID
    public Project findById(int id) {
        String query = "SELECT ProjectID, ProjectNo, ProjectName, StartDate, EndDate FROM Project WHERE ProjectID = ?";
        Project project = null;

        try (Connection conn = connectionHandler.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                project = mapToProject(rs);
            }

        } catch (SQLException e) {
            throw new DaoException("Fel vid hämtning av projekt med ID " + id, e);
        }
        return project;
    }

    
    
    // Lägg till nytt projekt
    public void save(Project project) {
        String query = "INSERT INTO Project (ProjectNo, ProjectName, StartDate, EndDate) VALUES (?, ?, ?, ?)";

        try (Connection conn = connectionHandler.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setInt(1, project.getProjectNo());
            stmt.setString(2, project.getProjectName());
            stmt.setString(3, project.getStartDate());
            stmt.setString(4, project.getEndDate());
            stmt.executeUpdate();

        } catch (SQLException e) {
            throw new DaoException("Fel vid sparande av projekt", e);
        }
    }

    // Uppdatera projekt
    public void update(Project project) {
        String query = "UPDATE Project SET ProjectNo = ?, ProjectName = ?, StartDate = ?, EndDate = ? WHERE ProjectID = ?";

        try (Connection conn = connectionHandler.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setInt(1, project.getProjectNo());
            stmt.setString(2, project.getProjectName());
            stmt.setString(3, project.getStartDate());
            stmt.setString(4, project.getEndDate());
            stmt.setInt(5, project.getProjectID());
            stmt.executeUpdate();

        } catch (SQLException e) {
            throw new DaoException("Fel vid uppdatering av projekt", e);
        }
    }

    // Ta bort projekt
    public void delete(int id) {
        String query = "DELETE FROM Project WHERE ProjectID = ?";

        try (Connection conn = connectionHandler.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setInt(1, id);
            stmt.executeUpdate();

        } catch (SQLException e) {
            throw new DaoException("Fel vid borttagning av projekt", e);
        }
    }

    public Project findByProjectNo(int projectNo) {
        String query = "SELECT ProjectID, ProjectNo, ProjectName, StartDate, EndDate FROM Project WHERE ProjectNo = ?";
        Project project = null;
    
        try (Connection conn = connectionHandler.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
    
            stmt.setInt(1, projectNo);
            ResultSet rs = stmt.executeQuery();
    
            if (rs.next()) {
                project = mapToProject(rs);
            }
    
        } catch (SQLException e) {
            throw new DaoException("Fel vid hämtning av projekt med ProjectNo " + projectNo, e);
        }
        return project;
    }

    // -------------------
    // EXTRA FUNKTIONER (A-nivå)
    // -------------------

    // A-nivå: Kolla om projektet tar mer än 60 % av resurserna
    public boolean isProjectOverSixtyPercentResources(int projectId) {
        // OBS: Denna metod förutsätter att det finns en tabell ProjectAssignment 
        // med kolumnerna EmployeeID, ProjectID, WorkedHours.
        String query =
            "SELECT CAST(SUM(pa.WorkedHours) AS FLOAT) / " +
            "(SELECT SUM(WorkedHours) FROM ProjectAssignment) * 100 AS ResourceShare " +
            "FROM ProjectAssignment pa " +
            "WHERE pa.ProjectID = ?";

        try (Connection conn = connectionHandler.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setInt(1, projectId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                double share = rs.getDouble("ResourceShare");
                return share > 60.0;
            }

        } catch (SQLException e) {
            throw new DaoException("Error when checking resource share for project " + projectId, e);
        }
        return false;
    }

    // Hämta alla projekt som involverar alla konsulter (A-nivå)
    public List<Project> findProjectsWithAllEmployees() {
        List<Project> projects = new ArrayList<>();
        String query =
                "SELECT p.ProjectID, p.ProjectNo, p.ProjectName, p.StartDate, p.EndDate " +
                "\nFROM Project p " +
                "\nJOIN ProjectAssignment pa ON p.ProjectID = pa.ProjectID " +
                "\nGROUP BY p.ProjectID, p.ProjectNo, p.ProjectName, p.StartDate, p.EndDate " +
                "\nHAVING COUNT(DISTINCT pa.EmployeeID) = (SELECT COUNT(*) FROM Employee)";
        try (Connection conn = connectionHandler.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                projects.add(mapToProject(rs));
            }
        } catch (SQLException e) {
            throw new DaoException("Error when getting projects involving all consultants", e);
        }
        return projects;
    }

    // Returns all projects assigned to a given employee
    public List<Project> findProjectsForEmployee(int employeeId) {
        List<Project> projects = new ArrayList<>();
        String query = "SELECT p.ProjectID, p.ProjectNo, p.ProjectName, p.StartDate, p.EndDate " +
                "FROM Project p JOIN Work w ON p.ProjectID = w.ProjectID WHERE w.EmployeeID = ?";
        try (Connection conn = connectionHandler.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, employeeId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                projects.add(mapToProject(rs));
            }
        } catch (SQLException e) {
            throw new DaoException("Error when getting assigned projects for consultant", e);
        }
        return projects;
    }

    // Removes all project assignments for a given employee
    public void deleteAssignmentsForEmployee(int employeeId) {
        String query = "DELETE FROM Work WHERE EmployeeID = ?";
        try (Connection conn = connectionHandler.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, employeeId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new DaoException("Error when deleting project assignments for consultant", e);
        }
    }
}