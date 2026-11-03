package se.lu.ics.Controllers;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import se.lu.ics.Data.EmployeeDao;
import se.lu.ics.Data.ProjectDao;
import se.lu.ics.Data.TitleDao;
import se.lu.ics.Models.Employee;
import se.lu.ics.Models.Project;

import java.io.IOException;
import java.util.List;

public class EmployeeEditController {

    // --- Data ---
    private EmployeeDao employeeDao;
    private TitleDao titleDao;
    private ProjectDao projectDao;
    private Employee currentEmployee;
    private List<Project> allProjects;
    private List<Project> assignedProjects;

    // --- FXML elements ---
    @FXML private TextField txtFirstName;
    @FXML private TextField txtLastName;
    @FXML private TextField txtSalary;
    @FXML private TextField txtTitle;
    @FXML private VBox vboxProjects;
    @FXML private Label lblUpdateStatus;
    @FXML private Button btnUpdate;
    @FXML private Button btnBack;

    // --- Initialization ---
    /**
     * Initializes DAOs for Employee, Title and Project.
     * Displays an alert if initialization fails.
     */
    @FXML
    public void initialize() {
        try {
            employeeDao = new EmployeeDao();
            titleDao = new TitleDao();
            projectDao = new ProjectDao();
        } catch (IOException e) {
            showAlert("Database error", e.getMessage());
        }

        // Connect event handlers (alternative: via FXML onAction)
        btnUpdate.setOnAction(e -> handleUpdate());
        btnBack.setOnAction(e -> handleBack());
    }

    // --- Public API ---
    /**
     * Loads an employee into the form and populates assigned projects.
     * @param emp The employee to edit
     */
    public void setEmployee(Employee emp) {
        if (emp == null) return;

        this.currentEmployee = emp;
        txtFirstName.setText(emp.getFirstName());
        txtLastName.setText(emp.getLastName());
        txtSalary.setText(String.valueOf(emp.getSalary()));
        txtTitle.setText(emp.getTitleName());

        loadProjects(emp);
    }

    // --- Event handlers ---
    /**
     * Validates input, updates employee information and project assignments.
     */
    private void handleUpdate() {
        if (currentEmployee == null) {
            showAlert("Update error", "No consultant loaded.");
            return;
        }

        String firstName = txtFirstName.getText().trim();
        String lastName  = txtLastName.getText().trim();
        String salaryStr = txtSalary.getText().trim();
        String titleName = txtTitle.getText().trim();

        if (firstName.isEmpty() || lastName.isEmpty() || salaryStr.isEmpty() || titleName.isEmpty()) {
            showAlert("Validation error", "All fields must be filled.");
            return;
        }

        double salary;
        try {
            salary = Double.parseDouble(salaryStr);
        } catch (NumberFormatException e) {
            showAlert("Validation error", "Invalid salary format.");
            return;
        }

        int titleId = titleDao.getOrCreateTitleId(titleName);

        currentEmployee.setFirstName(firstName);
        currentEmployee.setLastName(lastName);
        currentEmployee.setSalary(salary);
        currentEmployee.setTitleID(titleId);
        currentEmployee.setTitleName(titleName);

        employeeDao.update(currentEmployee);
        updateProjectAssignments();

        showStatus("Consultant updated.");
        clearForm();
    }

    /**
     * Navigates back to MainView.fxml.
     */
    private void handleBack() {
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/fxml/MainView.fxml"));
            btnBack.getScene().setRoot(root);
        } catch (IOException e) {
            showAlert("Navigation error", "Could not load main view: " + e.getMessage());
        }
    }

    // --- Helpers ---
    /**
     * Loads all projects and marks the ones assigned to the given employee.
     */
    private void loadProjects(Employee emp) {
        allProjects = projectDao.findAll();
        assignedProjects = projectDao.findProjectsForEmployee(emp.getEmployeeID());

        vboxProjects.getChildren().clear();

        for (Project p : allProjects) {
            CheckBox cb = new CheckBox(p.getProjectName());
            cb.setUserData(p);
            cb.setSelected(assignedProjects.stream()
                .anyMatch(ap -> ap.getProjectID() == p.getProjectID()));
            vboxProjects.getChildren().add(cb);
        }
    }

    /**
     * Updates project assignments for the current employee.
     */
    private void updateProjectAssignments() {
        projectDao.deleteAssignmentsForEmployee(currentEmployee.getEmployeeID());
        vboxProjects.getChildren().stream()
            .filter(node -> node instanceof CheckBox cb && cb.isSelected())
            .map(node -> (Project) ((CheckBox) node).getUserData())
            .forEach(p -> projectDao.assignToProject(currentEmployee.getEmployeeID(), p.getProjectID()));
    }

    /**
     * Clears all text fields and project selections.
     */
    private void clearForm() {
        txtFirstName.clear();
        txtLastName.clear();
        txtSalary.clear();
        txtTitle.clear();
        vboxProjects.getChildren().clear();
    }

    /**
     * Displays a status message in the update status label.
     */
    private void showStatus(String msg) {
        lblUpdateStatus.setText(msg);
    }

    /**
     * Displays an error alert with a given title and message.
     */
    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}
