package se.lu.ics.Controllers;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import se.lu.ics.Data.ProjectDao;
import se.lu.ics.Models.Project;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class ProjectViewController {

    // --- FXML fields ---
    @FXML private TextField txtProjectNo;
    @FXML private TextField txtProjectName;
    @FXML private DatePicker dpStartDate;
    @FXML private DatePicker dpEndDate;

    @FXML private TableView<Project> tableProject;
    @FXML private TableColumn<Project, String> columnProjectNo;
    @FXML private TableColumn<Project, String> columnProjectName;
    @FXML private TableColumn<Project, String> columnStartDate;
    @FXML private TableColumn<Project, String> columnEndDate;

    @FXML private Button btnAddProject;
    @FXML private Button btnUpdateProject;
    @FXML private Button btnDeleteProject;
    @FXML private Button btnGenerateNew;
    @FXML private Button btnSearchProject;

    // --- Data ---
    private ProjectDao projectDao;
    private final ObservableList<Project> projects = FXCollections.observableArrayList();

    // --- State ---
    private boolean projectsLoaded = false;
    private boolean isLoading = false;

    private static final int PROJECT_OFFSET = 2000;

    // --- Initialization ---
    /**
     * Initializes DAO, sets up table, form, and loads projects.
     */
    @FXML
    private void initialize() {
        try {
            projectDao = new ProjectDao();
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Database error", e.getMessage());
            disableForm();
            return;
        }

        setupTableColumns();
        setupForm();
        setupEventListeners();
        loadProjects();
        clearForm();
    }

    private void setupTableColumns() {
        columnProjectNo.setCellValueFactory(c ->
            new javafx.beans.property.SimpleStringProperty(generateProjectNo(c.getValue().getProjectID())));
        columnProjectName.setCellValueFactory(c ->
            new javafx.beans.property.SimpleStringProperty(c.getValue().getProjectName()));
        columnStartDate.setCellValueFactory(c ->
            new javafx.beans.property.SimpleStringProperty(c.getValue().getStartDate()));
        columnEndDate.setCellValueFactory(c ->
            new javafx.beans.property.SimpleStringProperty(c.getValue().getEndDate()));
        tableProject.setItems(projects);
    }

    private void setupForm() {
        txtProjectNo.setEditable(true);
        showEditButtons(false);
    }

    private void setupEventListeners() {
        tableProject.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
            if (newSel != null) {
                populateForm(newSel);
                showEditButtons(true);
            } else {
                showEditButtons(false);
            }
        });

        txtProjectNo.setOnAction(this::handleSearchProject);

        txtProjectNo.textProperty().addListener((obs, oldText, newText) -> {
            if (newText != null && newText.startsWith("PR")) {
                boolean found = searchAndPopulate(newText.trim());
                if (!found) showEditButtons(false);
            }
        });
    }

    // --- Event handlers ---
    /**
     * Handles adding a new project.
     */
    @FXML
    private void handleAddProject() {
        String noStr = txtProjectNo.getText();
        String name = txtProjectName.getText();
        LocalDate start = dpStartDate.getValue();
        LocalDate end = dpEndDate.getValue();

        if (noStr.isBlank()) { showAlert(Alert.AlertType.WARNING, "Validation", "ProjectNo is required."); return; }
        if (name == null || name.isBlank()) { showAlert(Alert.AlertType.WARNING, "Validation", "ProjectName is required."); return; }
        if (start == null) { showAlert(Alert.AlertType.WARNING, "Validation", "StartDate is required."); return; }
        if (end != null && end.isBefore(start)) {
            showAlert(Alert.AlertType.WARNING, "Validation", "End date cannot be before start date."); return;
        }

        try {
            Integer projectId = findProjectId(noStr);
            if (projectId == null) {
                showAlert(Alert.AlertType.WARNING, "Validation", "Invalid project number format."); return;
            } else if (projectId != -1) {
                showAlert(Alert.AlertType.WARNING, "Validation", "Project number already exists."); return;
            } else {
                int maxId = projects.stream().mapToInt(Project::getProjectID).max().orElse(0);
                projectId = maxId + 1;
            }

            Project project = new Project(projectId, projectId, name.trim(), start.toString(), end != null ? end.toString() : null);
            projectDao.save(project);
            forceReloadProjects();
            clearForm();

            showAlert(Alert.AlertType.INFORMATION, "Success", "Project added: " + generateProjectNo(projectId));
        } catch (Exception ex) {
            showAlert(Alert.AlertType.ERROR, "Add failed", ex.getMessage());
        }
    }

    /**
     * Handles updating an existing project.
     */
    @FXML
    private void handleUpdateProject() {
        Project selected = tableProject.getSelectionModel().getSelectedItem();
        if (selected == null) { showAlert(Alert.AlertType.WARNING, "No selection", "Select a project to update."); return; }

        String name = txtProjectName.getText();
        LocalDate start = dpStartDate.getValue();
        LocalDate end = dpEndDate.getValue();

        if (name == null || name.isBlank()) { showAlert(Alert.AlertType.WARNING, "Validation", "ProjectName is required."); return; }
        if (start == null) { showAlert(Alert.AlertType.WARNING, "Validation", "StartDate is required."); return; }
        if (end != null && end.isBefore(start)) {
            showAlert(Alert.AlertType.WARNING, "Validation", "End date cannot be before start date."); return;
        }

        selected.setProjectName(name.trim());
        selected.setStartDate(start.toString());
        selected.setEndDate(end != null ? end.toString() : null);

        try {
            projectDao.update(selected);
            forceReloadProjects();
            selectByProjectId(selected.getProjectID());
            showAlert(Alert.AlertType.INFORMATION, "Success", "Project updated.");
        } catch (Exception ex) {
            showAlert(Alert.AlertType.ERROR, "Update failed", ex.getMessage());
        }
    }

    /**
     * Handles deleting a project after confirmation.
     */
    @FXML
    private void handleDeleteProject() {
        Project selected = tableProject.getSelectionModel().getSelectedItem();
        if (selected == null) { showAlert(Alert.AlertType.WARNING, "No selection", "Select a project to delete."); return; }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "This cannot be undone.", ButtonType.OK, ButtonType.CANCEL);
        confirm.setHeaderText("Delete project \"" + selected.getProjectName() + "\"?");
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) return;

        try {
            projectDao.delete(selected.getProjectID());
            forceReloadProjects();
            clearForm();
            showAlert(Alert.AlertType.INFORMATION, "Success", "Project deleted.");
        } catch (Exception ex) {
            showAlert(Alert.AlertType.ERROR, "Delete failed", ex.getMessage());
        }
    }

    /**
     * Handles searching for a project.
     */
    @FXML
    private void handleSearchProject(javafx.event.ActionEvent event) {
        String searchText = txtProjectNo.getText();
        if (searchText.isBlank()) {
            displayAllProjects();
            return;
        }
        List<Project> matches = searchProjects(searchText.trim());
        displaySearchResults(matches, searchText.trim());
    }

    /**
     * Generates next available project number.
     */
    @FXML
    private void handleGenerateNew(javafx.event.ActionEvent event) {
        String next = generateNextProjectNo();
        txtProjectNo.setText(next);
        txtProjectName.requestFocus();
        showEditButtons(false);
        showAlert(Alert.AlertType.INFORMATION, "Generated", "New Project No: " + next);
    }

    /**
     * Clears the form fields and resets the table selection.
     */
    @FXML
    private void clearForm() {
        txtProjectNo.clear();
        txtProjectName.clear();
        dpStartDate.setValue(null);
        dpEndDate.setValue(null);
        showEditButtons(false);
        tableProject.getSelectionModel().clearSelection();
    }

    /**
     * Displays all projects in the table.
     */
    @FXML
    private void displayAllProjects() {
        forceReloadProjects();
        clearForm();
    }

    // --- Helpers ---
    private void loadProjects() {
        if (projectsLoaded || isLoading) return;
        isLoading = true;

        Task<ObservableList<Project>> task = new Task<>() {
            @Override protected ObservableList<Project> call() {
                return FXCollections.observableArrayList(projectDao.findAll());
            }
        };

        task.setOnSucceeded(e -> Platform.runLater(() -> {
            projects.setAll(task.getValue());
            tableProject.getSelectionModel().clearSelection();
            projectsLoaded = true;
            isLoading = false;
        }));

        task.setOnFailed(e -> Platform.runLater(() -> {
            isLoading = false;
            Throwable ex = task.getException();
            projects.clear();
            tableProject.getSelectionModel().clearSelection();
            projectsLoaded = true;
            showAlert(Alert.AlertType.ERROR, "Load Error", "Failed to load projects: " + ex.getMessage());
        }));

        new Thread(task).start();
    }

    private void forceReloadProjects() {
        projectsLoaded = false;
        loadProjects();
    }

    private void populateForm(Project project) {
        txtProjectNo.setText(generateProjectNo(project.getProjectID()));
        txtProjectName.setText(project.getProjectName());
        dpStartDate.setValue(parseDate(project.getStartDate()));
        dpEndDate.setValue(parseDate(project.getEndDate()));
    }

    private LocalDate parseDate(String dateStr) {
        try { return dateStr != null ? LocalDate.parse(dateStr) : null; }
        catch (Exception e) { return null; }
    }

    private void showEditButtons(boolean show) {
        btnUpdateProject.setVisible(show);
        btnDeleteProject.setVisible(show);
    }

    private void disableForm() {
        txtProjectNo.setDisable(true);
        txtProjectName.setDisable(true);
        dpStartDate.setDisable(true);
        dpEndDate.setDisable(true);
        btnAddProject.setDisable(true);
        btnUpdateProject.setDisable(true);
        btnDeleteProject.setDisable(true);
    }

    private String generateProjectNo(int projectId) {
        return "PR" + (projectId + PROJECT_OFFSET);
    }

    private Integer findProjectId(String projectNo) {
        if (projectNo == null || !projectNo.startsWith("PR")) return null;
        try {
            int realId = Integer.parseInt(projectNo.substring(2)) - PROJECT_OFFSET;
            return projects.stream().anyMatch(p -> p.getProjectID() == realId) ? realId : -1;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String generateNextProjectNo() {
        int maxId = projects.stream().mapToInt(Project::getProjectID).max().orElse(0);
        return generateProjectNo(maxId + 1);
    }

    private boolean searchAndPopulate(String projectNo) {
        Integer id = findProjectId(projectNo);
        if (id == null || id == -1) return false;
        Project project = projects.stream().filter(p -> p.getProjectID() == id).findFirst().orElse(null);
        if (project != null) {
            tableProject.getSelectionModel().select(project);
            populateForm(project);
            return true;
        }
        return false;
    }

    private List<Project> searchProjects(String text) {
        boolean hasWildcard = text.contains("%");
        return projects.stream()
            .filter(p -> matches(p, text, hasWildcard))
            .collect(Collectors.toList());
    }

    private boolean matches(Project p, String text, boolean wildcard) {
        String projectNo = generateProjectNo(p.getProjectID()).toLowerCase();
        String name = p.getProjectName() != null ? p.getProjectName().toLowerCase() : "";
        String search = text.toLowerCase();
        if (wildcard) {
            String regex = search.replace("%", ".*");
            return projectNo.matches(regex) || name.matches(regex);
        }
        return projectNo.contains(search) || name.contains(search);
    }

    private void displaySearchResults(List<Project> matches, String searchText) {
        projects.setAll(matches);
        clearForm();
        if (matches.isEmpty()) {
            showAlert(Alert.AlertType.INFORMATION, "Search Results", "No projects found for: " + searchText);
        } else {
            Project first = matches.get(0);
            tableProject.getSelectionModel().select(first);
            populateForm(first);
        }
    }

    private void selectByProjectId(int projectId) {
        projects.stream()
            .filter(p -> p.getProjectID() == projectId)
            .findFirst()
            .ifPresent(p -> {
                tableProject.getSelectionModel().select(p);
                populateForm(p);
            });
    }

    private void showAlert(Alert.AlertType type, String title, String msg) {
        Alert alert = new Alert(type, msg, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}
