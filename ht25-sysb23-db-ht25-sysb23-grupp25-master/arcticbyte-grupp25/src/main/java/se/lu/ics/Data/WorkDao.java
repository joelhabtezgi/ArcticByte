package se.lu.ics.Data;

import se.lu.ics.Models.Work;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class WorkDao {
    private final ConnectionHandler connectionHandler;

    public WorkDao() throws IOException {
        this.connectionHandler = new ConnectionHandler();
    }

    // --- Helpers ---
    private Work mapToWork(ResultSet rs) throws SQLException {
        return new Work(
                rs.getInt("ProjectID"),
                rs.getInt("EmployeeID"),
                rs.getInt("Hours")
        );
    }

    // --- CRUD ---
    public List<Work> findAll() {
        String query = "SELECT ProjectID, EmployeeID, Hours FROM Work";
        List<Work> works = new ArrayList<>();
        try (Connection conn = connectionHandler.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) works.add(mapToWork(rs));
        } catch (SQLException e) {
            throw new DaoException("Error fetching all work", e);
        }
        return works;
    }

    public void save(Work work) {
        String query = "INSERT INTO Work (ProjectID, EmployeeID, Hours) VALUES (?, ?, ?)";
        try (Connection conn = connectionHandler.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, work.getProjectID());
            stmt.setInt(2, work.getEmployeeID());
            stmt.setInt(3, work.getHours());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new DaoException("Error saving work", e);
        }
    }

    public void saveBatch(List<Work> works) {
        if (works.isEmpty()) return;
        String query = "INSERT INTO Work (ProjectID, EmployeeID, Hours) VALUES (?, ?, ?)";
        try (Connection conn = connectionHandler.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            conn.setAutoCommit(false);
            for (Work work : works) {
                stmt.setInt(1, work.getProjectID());
                stmt.setInt(2, work.getEmployeeID());
                stmt.setInt(3, work.getHours());
                stmt.addBatch();
            }
            stmt.executeBatch();
            conn.commit();
            conn.setAutoCommit(true);
        } catch (SQLException e) {
            throw new DaoException("Error batch saving work", e);
        }
    }

    public void updateHours(int projectId, int employeeId, int hours) {
        String query = "UPDATE Work SET Hours = ? WHERE ProjectID = ? AND EmployeeID = ?";
        try (Connection conn = connectionHandler.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, hours);
            stmt.setInt(2, projectId);
            stmt.setInt(3, employeeId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new DaoException("Error updating work hours", e);
        }
    }

    public void delete(int projectId, int employeeId) {
        String query = "DELETE FROM Work WHERE ProjectID = ? AND EmployeeID = ?";
        try (Connection conn = connectionHandler.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, projectId);
            stmt.setInt(2, employeeId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new DaoException("Error deleting work", e);
        }
    }

    // --- Project/Employee details ---
    public List<Integer> findEmployeesByProject(int projectId) {
        return getProjectEmployeeDetails(projectId).employeeIds;
    }

    public static class ProjectEmployeeDetails {
        public List<Integer> employeeIds = new ArrayList<>();
        public List<Integer> employeeHours = new ArrayList<>();
        public int totalProjectHours;
        public int employeeCount;
        public double averageHoursPerEmployee;
    }

    public ProjectEmployeeDetails getProjectEmployeeDetails(int projectId) {
        String query = """
            SELECT EmployeeID, Hours,
                   SUM(Hours) OVER() AS TotalProjectHours,
                   COUNT(*) OVER() AS EmployeeCount,
                   AVG(CAST(Hours AS FLOAT)) OVER() AS AvgHoursPerEmployee
            FROM Work
            WHERE ProjectID = ?
            ORDER BY Hours DESC, EmployeeID
            """;

        ProjectEmployeeDetails details = new ProjectEmployeeDetails();
        try (Connection conn = connectionHandler.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, projectId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                details.employeeIds.add(rs.getInt("EmployeeID"));
                details.employeeHours.add(rs.getInt("Hours"));
                if (details.totalProjectHours == 0) {
                    details.totalProjectHours = rs.getInt("TotalProjectHours");
                    details.employeeCount = rs.getInt("EmployeeCount");
                    details.averageHoursPerEmployee = rs.getDouble("AvgHoursPerEmployee");
                }
            }
        } catch (SQLException e) {
            throw new DaoException("Error fetching project details for project " + projectId, e);
        }
        return details;
    }

    // --- Statistics ---
    public int getTotalHoursByEmployee(int employeeId) {
        return getWorkStatistics(employeeId, null).employeeTotalHours;
    }

    public int getTotalHoursAllEmployees() {
        return getWorkStatistics(null, null).totalHours;
    }

    public int getHardestWorkingEmployeeId() {
        return getWorkStatistics(null, null).hardestWorkingEmployeeId;
    }

    public double getProjectResourceShare(int projectId) {
        return getWorkStatistics(null, projectId).projectResourceShare;
    }

    public static class WorkStatistics {
        public int totalHours;
        public int employeeTotalHours;
        public int hardestWorkingEmployeeId = -1;
        public int maxEmployeeHours;
        public double projectResourceShare;
    }

    public WorkStatistics getWorkStatistics(Integer employeeId, Integer projectId) {
        String query = """
            WITH EmployeeStats AS (
                SELECT EmployeeID, SUM(Hours) AS EmployeeHours
                FROM Work GROUP BY EmployeeID
            ),
            ProjectStats AS (
                SELECT ProjectID, SUM(Hours) AS ProjectHours
                FROM Work GROUP BY ProjectID
            ),
            OverallStats AS (SELECT SUM(Hours) AS TotalHours FROM Work)
            SELECT os.TotalHours,
                   ISNULL(es_specific.EmployeeHours, 0) AS SpecificEmployeeHours,
                   ISNULL(ps_specific.ProjectHours, 0) AS SpecificProjectHours,
                   hw.EmployeeID AS HardestWorkingEmployeeId,
                   hw.EmployeeHours AS MaxEmployeeHours,
                   CASE WHEN os.TotalHours > 0 AND ps_specific.ProjectHours IS NOT NULL
                        THEN CAST(ps_specific.ProjectHours AS FLOAT) / os.TotalHours * 100
                        ELSE 0 END AS ProjectResourceShare
            FROM OverallStats os
            CROSS JOIN (SELECT TOP 1 EmployeeID, EmployeeHours
                        FROM EmployeeStats ORDER BY EmployeeHours DESC) hw
            LEFT JOIN EmployeeStats es_specific ON es_specific.EmployeeID = ?
            LEFT JOIN ProjectStats ps_specific ON ps_specific.ProjectID = ?
            """;

        WorkStatistics stats = new WorkStatistics();
        try (Connection conn = connectionHandler.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setObject(1, employeeId);
            stmt.setObject(2, projectId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                stats.totalHours = rs.getInt("TotalHours");
                stats.employeeTotalHours = rs.getInt("SpecificEmployeeHours");
                stats.hardestWorkingEmployeeId = rs.getInt("HardestWorkingEmployeeId");
                stats.maxEmployeeHours = rs.getInt("MaxEmployeeHours");
                stats.projectResourceShare = rs.getDouble("ProjectResourceShare");
            }
        } catch (SQLException e) {
            throw new DaoException("Error fetching work statistics", e);
        }
        return stats;
    }

    // --- Detailed Info ---
    public static class DetailedWorkInfo {
        public int projectId;
        public String projectName;
        public int employeeId;
        public String employeeName;
        public int hours;
        public int totalProjectHours;
        public int totalEmployeeHours;
        public String employeeTitle;
    }

    public List<DetailedWorkInfo> getDetailedWorkInfo(Integer projectId, Integer employeeId) {
        String query = """
            SELECT w.ProjectID, p.ProjectName,
                   w.EmployeeID, CONCAT(e.FirstName, ' ', e.LastName) AS EmployeeName,
                   w.Hours, proj_stats.TotalProjectHours, emp_stats.TotalEmployeeHours,
                   t.Title AS EmployeeTitle
            FROM Work w
            JOIN Project p ON w.ProjectID = p.ProjectID
            JOIN Employee e ON w.EmployeeID = e.EmployeeID
            LEFT JOIN Title t ON e.TitleID = t.TitleID
            JOIN (SELECT ProjectID, SUM(Hours) AS TotalProjectHours FROM Work GROUP BY ProjectID) proj_stats
                 ON w.ProjectID = proj_stats.ProjectID
            JOIN (SELECT EmployeeID, SUM(Hours) AS TotalEmployeeHours FROM Work GROUP BY EmployeeID) emp_stats
                 ON w.EmployeeID = emp_stats.EmployeeID
            WHERE 1=1
            """ + (projectId != null ? "AND w.ProjectID = ? " : "") +
            (employeeId != null ? "AND w.EmployeeID = ? " : "") +
            "ORDER BY w.ProjectID, w.Hours DESC";

        List<DetailedWorkInfo> list = new ArrayList<>();
        try (Connection conn = connectionHandler.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            int idx = 1;
            if (projectId != null) stmt.setInt(idx++, projectId);
            if (employeeId != null) stmt.setInt(idx, employeeId);

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                DetailedWorkInfo info = new DetailedWorkInfo();
                info.projectId = rs.getInt("ProjectID");
                info.projectName = rs.getString("ProjectName");
                info.employeeId = rs.getInt("EmployeeID");
                info.employeeName = rs.getString("EmployeeName");
                info.hours = rs.getInt("Hours");
                info.totalProjectHours = rs.getInt("TotalProjectHours");
                info.totalEmployeeHours = rs.getInt("TotalEmployeeHours");
                info.employeeTitle = rs.getString("EmployeeTitle");
                list.add(info);
            }
        } catch (SQLException e) {
            throw new DaoException("Error fetching detailed work info", e);
        }
        return list;
    }

    // Convenience methods
    public List<DetailedWorkInfo> getAllDetailedWorkInfo() { return getDetailedWorkInfo(null, null); }
    public List<DetailedWorkInfo> getDetailedWorkInfoForProject(int projectId) { return getDetailedWorkInfo(projectId, null); }
    public List<DetailedWorkInfo> getDetailedWorkInfoForEmployee(int employeeId) { return getDetailedWorkInfo(null, employeeId); }

    // --- Projects with All Consultants ---
    public static class ProjectWithAllConsultants {
        public int projectId;
        public String projectName;
        public int projectNo;
        public int totalConsultants;
        public int totalHours;
        public double averageHoursPerConsultant;
    }

    /**
     * Retrieves information on projects that involve every consultant.
     * This finds projects where the number of distinct employees working on the project
     * equals the total number of employees in the system.
     */
    public List<ProjectWithAllConsultants> getProjectsWithAllConsultants() {
        String query = """
            WITH ProjectEmployeeCounts AS (
                SELECT w.ProjectID, COUNT(DISTINCT w.EmployeeID) as EmployeeCount, SUM(w.Hours) as TotalHours
                FROM Work w
                GROUP BY w.ProjectID
            ),
            TotalEmployeeCount AS (
                SELECT COUNT(DISTINCT EmployeeID) as TotalEmployees
                FROM Employee
            )
            SELECT pec.ProjectID, p.ProjectName, p.ProjectNo, 
                   pec.EmployeeCount as TotalConsultants, pec.TotalHours,
                   CAST(pec.TotalHours AS FLOAT) / pec.EmployeeCount as AverageHoursPerConsultant
            FROM ProjectEmployeeCounts pec
            JOIN Project p ON pec.ProjectID = p.ProjectID
            CROSS JOIN TotalEmployeeCount tec
            WHERE pec.EmployeeCount = tec.TotalEmployees
            ORDER BY pec.TotalHours DESC, p.ProjectName
            """;

        List<ProjectWithAllConsultants> projects = new ArrayList<>();
        try (Connection conn = connectionHandler.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                ProjectWithAllConsultants project = new ProjectWithAllConsultants();
                project.projectId = rs.getInt("ProjectID");
                project.projectName = rs.getString("ProjectName");
                project.projectNo = rs.getInt("ProjectNo");
                project.totalConsultants = rs.getInt("TotalConsultants");
                project.totalHours = rs.getInt("TotalHours");
                project.averageHoursPerConsultant = rs.getDouble("AverageHoursPerConsultant");
                projects.add(project);
            }
        } catch (SQLException e) {
            throw new DaoException("Error fetching projects with all consultants", e);
        }
        return projects;
    }

    /**
     * Gets detailed information about a specific project that involves all consultants,
     * including which employees work on it and their hours.
     */
    public List<DetailedWorkInfo> getDetailedInfoForProjectWithAllConsultants(int projectId) {
        // First verify this project actually has all consultants
        List<ProjectWithAllConsultants> allConsultantProjects = getProjectsWithAllConsultants();
        boolean hasAllConsultants = allConsultantProjects.stream()
                .anyMatch(p -> p.projectId == projectId);
        
        if (!hasAllConsultants) {
            return new ArrayList<>(); // Return empty list if project doesn't have all consultants
        }
        
        return getDetailedWorkInfoForProject(projectId);
    }
}
