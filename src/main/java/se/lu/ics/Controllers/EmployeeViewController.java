package se.lu.ics.Controllers;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import se.lu.ics.Data.EmployeeDao;
import se.lu.ics.Models.Employee;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

public class EmployeeViewController {

    // --- Data ---
    private EmployeeDao employeeDao;
    private List<Employee> cachedEmployees;

    // --- FXML elements ---
    @FXML private TableView<Employee> tblEmployees;
    @FXML private TableColumn<Employee, String> colEmployeeNo;
    @FXML private TableColumn<Employee, String> colFirstName;
    @FXML private TableColumn<Employee, String> colLastName;
    @FXML private TableColumn<Employee, Double> colSalary;
    @FXML private TableColumn<Employee, String> colTitle;
    @FXML private TableColumn<Employee, String> colProject;

    @FXML private TextField txtTitle;
    @FXML private TextField txtProject;
    @FXML private TextField txtEmployeeId;

    @FXML private Button btnLoadAll;
    @FXML private Button btnLoadThreeOrFewer;

    @FXML private Label lblTotalConsultants;
    @FXML private Label lblThreeOrFewer;

    // --- Initialization ---
    @FXML
    public void initialize() {
        try {
            employeeDao = new EmployeeDao();
        } catch (IOException e) {
            showAlert("Database error", e.getMessage());
            return;
        }

        setupTableColumns();
        setupFilters();

        loadEmployees();
    }

    private void setupTableColumns() {
        colEmployeeNo.setCellValueFactory(c -> prop(c.getValue().getEmployeeNo()));
        colFirstName.setCellValueFactory(c -> prop(c.getValue().getFirstName()));
        colLastName.setCellValueFactory(c -> prop(c.getValue().getLastName()));
        colSalary.setCellValueFactory(c -> new javafx.beans.property.SimpleDoubleProperty(c.getValue().getSalary()).asObject());
        colTitle.setCellValueFactory(c -> prop(c.getValue().getTitleName()));
        colProject.setCellValueFactory(c -> prop(c.getValue().getProjectNames()));

        tblEmployees.setStyle("-fx-background-color: #f8f9fa; -fx-border-color: #bdc3c7; -fx-border-radius: 8; -fx-background-radius: 8;");
        tblEmployees.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
    }

    private void setupFilters() {
        txtEmployeeId.textProperty().addListener((o, oldVal, newVal) -> filterEmployees());
        txtTitle.textProperty().addListener((o, oldVal, newVal) -> filterEmployees());
        txtProject.textProperty().addListener((o, oldVal, newVal) -> filterEmployees());
    }

    // --- Data handling ---
    private void loadEmployees() {
        cachedEmployees = employeeDao.findAll();
        tblEmployees.setItems(FXCollections.observableArrayList(cachedEmployees));
        lblTotalConsultants.setText("Total consultants: " + cachedEmployees.size());
    }

    private void filterEmployees() {
        String employeeNoFilter = txtEmployeeId.getText().toLowerCase();
        String titleFilter = txtTitle.getText().toLowerCase();
        String projectFilter = txtProject.getText().toLowerCase();
        List<Employee> filtered = cachedEmployees.stream()
            .filter(emp -> (employeeNoFilter.isEmpty() || (emp.getEmployeeNo() != null && emp.getEmployeeNo().toLowerCase().contains(employeeNoFilter)))
                && (titleFilter.isEmpty() || textContains(emp.getTitleName(), titleFilter))
                && (projectFilter.isEmpty() || textContains(emp.getProjectNames(), projectFilter)))
            .collect(Collectors.toList());
        tblEmployees.setItems(FXCollections.observableArrayList(filtered));
    }

    private boolean textContains(String text, String filter) {
        return text != null && text.toLowerCase().contains(filter);
    }

    // --- Event handlers ---
    @FXML
    private void handleLoadAll() {
        loadEmployees();
        lblTotalConsultants.setVisible(true);
        lblThreeOrFewer.setVisible(false);
    }

    @FXML
    private void handleLoadThreeOrFewer() {
        List<Employee> filtered = employeeDao.findConsultantsInThreeOrFewerProjects();
        tblEmployees.setItems(FXCollections.observableArrayList(filtered));
        lblThreeOrFewer.setText("Consultants in ≤3 projects: " + filtered.size());
        lblThreeOrFewer.setVisible(true);
        lblTotalConsultants.setVisible(false);
    }

    @FXML
    private void handleGoToAddDelete() {
        switchScene("/fxml/EmployeeAddView.fxml");
    }

    @FXML
    private void handleGoToEdit() {
        Employee selected = tblEmployees.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("Error", "No consultant selected for editing.");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/EmployeeEditView.fxml"));
            Parent root = loader.load();
            loader.<EmployeeEditController>getController().setEmployee(selected);
            tblEmployees.getScene().setRoot(root);
        } catch (Exception e) {
            showAlert("Error", "Could not load EmployeeEdit view: " + e.getMessage());
        }
    }

    private void switchScene(String fxmlPath) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent root = loader.load();
            tblEmployees.getScene().setRoot(root);
        } catch (Exception e) {
            showAlert("Error", "Could not load view: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // --- Helpers ---
    private javafx.beans.property.SimpleStringProperty prop(String value) {
        return new javafx.beans.property.SimpleStringProperty(value);
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}
