package se.lu.ics.Data;

import se.lu.ics.Models.Employee;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class EmployeeDao {

    private final ConnectionHandler connectionHandler;

    // --- Constructor ---
    public EmployeeDao() throws IOException {
        this.connectionHandler = new ConnectionHandler();
    }

    // --- Helper: safe mapping ---
    private Employee mapToEmployee(ResultSet rs) throws SQLException {
        return new Employee(
                rs.getInt("EmployeeID"),
                rs.getString("FirstName"),
                rs.getString("LastName"),
                rs.getInt("TitleID"),
                getSafe(rs, "TitleName"),
                getSafeDouble(rs, "Salary"),
                getSafe(rs, "ProjectNames"),
                getSafe(rs, "EmployeeNo")
        );
    }

    private String getSafe(ResultSet rs, String column) {
        try { return rs.getString(column); } catch (SQLException e) { return null; }
    }
    private double getSafeDouble(ResultSet rs, String column) {
        try { return rs.getDouble(column); } catch (SQLException e) { return 0.0; }
    }

    // --- Utility: generate employee number ---
    public static String generateEmployeeNo(String firstName) {
        String prefix = firstName.length() >= 2 ? firstName.substring(0, 2).toLowerCase() : firstName.toLowerCase();
        int num = new java.util.Random().nextInt(900) + 100; // 100–999
        return prefix + num;
    }

    // --- Base SELECT used by multiple queries ---
    private static final String BASE_SELECT =
            "SELECT e.EmployeeID, e.FirstName, e.LastName, e.TitleID, e.EmployeeNo, e.Salary, t.TitleName, " +
            "STUFF((SELECT ', ' + p.ProjectName " +
            "       FROM Work w JOIN Project p ON w.ProjectID = p.ProjectID " +
            "       WHERE w.EmployeeID = e.EmployeeID " +
            "       FOR XML PATH('')), 1, 2, '') AS ProjectNames " +
            "FROM Employee e " +
            "LEFT JOIN Title t ON e.TitleID = t.TitleID ";

    // --- Find all employees ---
    public List<Employee> findAll() {
        String query = BASE_SELECT;
        List<Employee> employees = new ArrayList<>();
        try (Connection conn = connectionHandler.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) employees.add(mapToEmployee(rs));
        } catch (SQLException e) {
            throw new DaoException("Error fetching all employees", e);
        }
        return employees;
    }

    // --- Find consultants in ≤3 projects ---
    public List<Employee> findConsultantsInThreeOrFewerProjects() {
        String query = BASE_SELECT +
                "LEFT JOIN Work w ON e.EmployeeID = w.EmployeeID " +
                "GROUP BY e.EmployeeID, e.FirstName, e.LastName, e.TitleID, e.EmployeeNo, t.TitleName, e.Salary " +
                "HAVING COUNT(DISTINCT w.ProjectID) <= 3";
        List<Employee> employees = new ArrayList<>();
        try (Connection conn = connectionHandler.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) employees.add(mapToEmployee(rs));
        } catch (SQLException e) {
            throw new DaoException("Error fetching consultants in three or fewer projects", e);
        }
        return employees;
    }

    // --- Find by ID ---
    public Employee findById(int id) {
        String query = BASE_SELECT + "WHERE e.EmployeeID = ?";
        try (Connection conn = connectionHandler.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return mapToEmployee(rs);
            }
        } catch (SQLException e) {
            throw new DaoException("Error fetching employee with ID " + id, e);
        }
        return null;
    }

    // --- Save employee (basic insert) ---
    public void save(Employee employee) {
        String query = "INSERT INTO Employee (FirstName, LastName, TitleID, Salary) VALUES (?, ?, ?, ?)";
        try (Connection conn = connectionHandler.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, employee.getFirstName());
            stmt.setString(2, employee.getLastName());
            stmt.setInt(3, employee.getTitleID());
            stmt.setDouble(4, employee.getSalary());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new DaoException("Error saving employee", e);
        }
    }

    // --- Save employee and return generated ID ---
    public int saveAndReturnId(Employee employee) {
        String query = "INSERT INTO Employee (FirstName, LastName, TitleID, Salary, EmployeeNo) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = connectionHandler.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, employee.getFirstName());
            stmt.setString(2, employee.getLastName());
            stmt.setInt(3, employee.getTitleID());
            stmt.setDouble(4, employee.getSalary());
            stmt.setString(5, employee.getEmployeeNo());

            int affected = stmt.executeUpdate();
            if (affected == 0) throw new DaoException("Failed to add employee, no rows affected.");

            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
            throw new DaoException("Failed to retrieve generated employee ID.");
        } catch (SQLException e) {
            throw new DaoException("Error saving employee", e);
        }
    }

    // --- Update employee ---
    public void update(Employee employee) {
        String query = "UPDATE Employee SET FirstName = ?, LastName = ?, TitleID = ?, Salary = ? WHERE EmployeeID = ?";
        try (Connection conn = connectionHandler.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, employee.getFirstName());
            stmt.setString(2, employee.getLastName());
            stmt.setInt(3, employee.getTitleID());
            stmt.setDouble(4, employee.getSalary());
            stmt.setInt(5, employee.getEmployeeID());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new DaoException("Error updating employee", e);
        }
    }

    // --- Delete employee ---
    public void delete(int employeeId) {
        String query = "DELETE FROM Employee WHERE EmployeeID = ?";
        try (Connection conn = connectionHandler.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, employeeId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new DaoException("Error deleting employee", e);
        }
    }

    // --- Find by Title ---
    public List<Employee> findByTitle(int titleID) {
        String query = BASE_SELECT + "WHERE e.TitleID = ?";
        List<Employee> employees = new ArrayList<>();
        try (Connection conn = connectionHandler.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, titleID);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) employees.add(mapToEmployee(rs));
            }
        } catch (SQLException e) {
            throw new DaoException("Error fetching employees with TitleID " + titleID, e);
        }
        return employees;
    }

    // --- Hardest working employee ---
    public Employee getHardestWorkingEmployee() {
        String query =
                "SELECT TOP 1 e.EmployeeID, e.FirstName, e.LastName, e.TitleID, e.EmployeeNo, e.Salary, " +
                "t.TitleName, NULL AS ProjectNames, SUM(pa.WorkedHours) AS TotalHours " +
                "FROM Employee e " +
                "JOIN Title t ON e.TitleID = t.TitleID " +
                "JOIN ProjectAssignment pa ON e.EmployeeID = pa.EmployeeID " +
                "GROUP BY e.EmployeeID, e.FirstName, e.LastName, e.TitleID, e.EmployeeNo, t.TitleName, e.Salary " +
                "ORDER BY TotalHours DESC";
        try (Connection conn = connectionHandler.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) return mapToEmployee(rs);
        } catch (SQLException e) {
            throw new DaoException("Error fetching hardest working employee", e);
        }
        return null;
    }

    // --- Employees by project ---
    public List<Employee> findByProject(int projectId) {
        String query = BASE_SELECT +
                "JOIN ProjectAssignment pa ON e.EmployeeID = pa.EmployeeID " +
                "WHERE pa.ProjectID = ?";
        List<Employee> employees = new ArrayList<>();
        try (Connection conn = connectionHandler.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, projectId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) employees.add(mapToEmployee(rs));
            }
        } catch (SQLException e) {
            throw new DaoException("Error fetching employees for ProjectID " + projectId, e);
        }
        return employees;
    }

    // --- Total hours for employee ---
    public int getTotalHoursForEmployee(int employeeId) {
        String query = "SELECT SUM(WorkedHours) AS TotalHours FROM ProjectAssignment WHERE EmployeeID = ?";
        try (Connection conn = connectionHandler.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, employeeId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getInt("TotalHours");
            }
        } catch (SQLException e) {
            throw new DaoException("Error calculating hours for employee " + employeeId, e);
        }
        return 0;
    }
}