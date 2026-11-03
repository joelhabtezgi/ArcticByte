package se.lu.ics.Controllers;

import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import se.lu.ics.Data.EmployeeDao;
import se.lu.ics.Data.ProjectDao;
import se.lu.ics.Data.TitleDao;
import se.lu.ics.Models.Employee;
import se.lu.ics.Models.Project;

import java.util.List;
import java.util.Optional;

public class EmployeeAddController {
    @FXML private TextField txtFirstName;
    @FXML private TextField txtLastName;
    @FXML private TextField txtSalary;
    @FXML private TextField txtTitle;
    @FXML private ListView<Project> lstProjects;
    @FXML private TableView<Employee> tblEmployees;
    @FXML private TableColumn<Employee, String> colEmployeeNo;
    @FXML private TableColumn<Employee, String> colFirstName;
    @FXML private TableColumn<Employee, String> colLastName;
    @FXML private TableColumn<Employee, Double> colSalary;
    @FXML private Label lblAddStatus;
    @FXML private Label lblDeleteStatus;
    @FXML private Button btnBack;
    @FXML private TableColumn<Employee, String> colTitle;
    @FXML private TableColumn<Employee, String> colProject;

    private EmployeeDao employeeDao;
    private TitleDao titleDao;
    private ProjectDao projectDao;

    @FXML
    public void initialize() {
        try {
            employeeDao = new EmployeeDao();
            titleDao = new TitleDao();
            projectDao = new ProjectDao();
        } catch (Exception e) {
            showAlert("Error", "Could not initialize database connection: " + e.getMessage());
            e.printStackTrace(); // Print full stack trace for debugging
            return;
        }
        // Initiera tabellkolumner
    colEmployeeNo.setCellValueFactory(new PropertyValueFactory<>("employeeNo"));
        colFirstName.setCellValueFactory(new PropertyValueFactory<>("firstName"));
        colLastName.setCellValueFactory(new PropertyValueFactory<>("lastName"));
    // Show title name in colTitle
    colTitle.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().getTitleName()));
    colSalary.setCellValueFactory(new PropertyValueFactory<>("salary"));
    // Show project names in colProject
    colProject.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().getProjectNames()));
    // TableView aesthetics
    tblEmployees.setStyle("-fx-background-color: #f8f9fa; -fx-border-color: #bdc3c7; -fx-border-radius: 8; -fx-background-radius: 8;");
    tblEmployees.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);

        // Ladda data
        loadEmployees();
        // Ladda projekt till ListView
        List<Project> projects = projectDao.findAll();
        lstProjects.setItems(FXCollections.observableArrayList(projects));
        lstProjects.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
    }

    private void loadEmployees() {
        List<Employee> employees = employeeDao.findAll();
        tblEmployees.setItems(FXCollections.observableArrayList(employees));
    }




    // Projects are now entered as text, no need to load them

    @FXML
    private void handleAddConsultant(ActionEvent event) {
        lblAddStatus.setText("");
        String firstName = txtFirstName.getText().trim();
        String lastName = txtLastName.getText().trim();
        String salaryStr = txtSalary.getText().trim();
        String titleText = txtTitle.getText().trim();
        List<Project> selectedProjects = lstProjects.getSelectionModel().getSelectedItems();

        // Validering
        if (firstName.isEmpty() || lastName.isEmpty() || salaryStr.isEmpty() || titleText.isEmpty() || selectedProjects.isEmpty()) {
            lblAddStatus.setText("All fields must be filled and at least one project selected.");
            return;
        }

        double salary;
        try {
            salary = Double.parseDouble(salaryStr);
        } catch (NumberFormatException e) {
            lblAddStatus.setText("Salary needs to be a digit.");
            return;
        }
        if (salary < 0) {
            lblAddStatus.setText("Salary cannot be negative.");
            return;
        }

        // Spara titeln i Title-tabellen om den inte finns, och hämta dess ID
        int titleId = titleDao.getOrCreateTitleId(titleText);

        String employeeNo = EmployeeDao.generateEmployeeNo(firstName);
        Employee employee = new Employee(firstName, lastName, titleId, salary, employeeNo);
        int employeeId = employeeDao.saveAndReturnId(employee);
        if (employeeId > 0) {
            employee.setEmployeeNo(employeeNo); // Set generated EmployeeNo

            boolean allAssigned = true;
            for (Project project : selectedProjects) {
                boolean assigned = projectDao.assignToProject(employeeId, project.getProjectID());
                if (!assigned) allAssigned = false;
            }

            loadEmployees(); // Reload list from database
            if (allAssigned) {
                lblAddStatus.setText("Consultant added and assigned to selected projects!");
            } else {
                lblAddStatus.setText("Consultant added, but could not be assigned to all projects.");
            }
            ExcelViewController.notifyDataChanged(); // Notifiera Excel-användare om dataändring
            clearAddForm();
        } else {
            lblAddStatus.setText("Failed to add consultant.");
        }
    }

    @FXML
    private void handleDeleteConsultant(ActionEvent event) {
        lblDeleteStatus.setText("");
        Employee selected = tblEmployees.getSelectionModel().getSelectedItem();
        if (selected == null) {
            lblDeleteStatus.setText("No consultant selected.");
            return;
        }
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Are you sure you want to delete the selected consultant?", ButtonType.YES, ButtonType.NO);
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.YES) {
            employeeDao.delete(selected.getEmployeeID());
            lblDeleteStatus.setText("Consultant deleted.");
            loadEmployees();
        }
    }

    private void clearAddForm() {
    txtFirstName.clear();
    txtLastName.clear();
    txtSalary.clear();
    txtTitle.clear();
    lstProjects.getSelectionModel().clearSelection();
    }

    @FXML
    private void handleBack(ActionEvent event) {
        // Byt ut hela scenen till MainView.fxml (återställ alla tabs)
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/fxml/MainView.fxml"));
            javafx.scene.Parent root = loader.load();
            javafx.scene.Scene scene = btnBack.getScene();
            scene.setRoot(root);
        } catch (Exception e) {
            showAlert("Error", "Could not load MainView: " + e.getMessage());
        }
    }
    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}