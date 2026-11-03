package se.lu.ics.Controllers;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import se.lu.ics.Data.MilestoneDao;
import se.lu.ics.Data.ProjectDao;
import se.lu.ics.Models.Milestone;
import se.lu.ics.Models.Project;

import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MilestoneViewController {

    // --- FXML fields ---
    @FXML private AnchorPane anchorMilestone;
    @FXML private TableView<Milestone> tableMilestone;
    @FXML private TableColumn<Milestone, String> coMilestoneNo;
    @FXML private TableColumn<Milestone, String> coMilestoneName;
    @FXML private TableColumn<Milestone, String> coMilestoneDate;
    @FXML private TableColumn<Milestone, String> coMilestoneStatus;
    @FXML private TableColumn<Milestone, String> coMilestoneProject;

    @FXML private TextField txtMilestoneNo;
    @FXML private TextField txtMilestoneName;
    @FXML private ComboBox<String> cbMilestoneStatus;
    @FXML private ComboBox<String> cbMilestoneProject;
    @FXML private DatePicker dpMilestoneDate;

    @FXML private Label lblMilestoneCount;
    @FXML private Label lblLoadMilestones;

    @FXML private Button btnAddMilestone;
    @FXML private Button btnUpdateMilestone;
    @FXML private Button btnDeleteMilestone;
    @FXML private Button btnClearFieldsMilestone;
    @FXML private Button btnLoadAllMilestone;
    @FXML private Button btnLoadMilestonesByProject;
    @FXML private Button btnCountMilestones;
    @FXML private Button btnGenerateNew;

    // --- Data ---
    private MilestoneDao milestoneDao;
    private ProjectDao projectDao;
    private ObservableList<Milestone> milestoneList;

    private ObservableList<Project> allProjects = FXCollections.observableArrayList();
    private boolean projectCacheInitialized = false;
    private boolean milestonesLoaded = false;
    private boolean isLoadingMilestones = false;
    
    // Mappning för att komma ihåg ursprunglig status (milestoneId -> ursprungligStatus)
    private Map<Integer, String> originalStatusMap = new HashMap<>();

    // --- Constants ---
    private static final int PROJECT_OFFSET = 2000;
    private static final int MILESTONE_OFFSET = 1000;

    // --- Initialization ---
    public MilestoneViewController() {
        try {
            milestoneDao = new MilestoneDao();
            projectDao = new ProjectDao();
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Initialization Error", "Failed to connect: " + e.getMessage());
        }
    }

    /**
	○  Initializes table, form controls, event listeners, and loads data.
     */
    @FXML
    public void initialize() {
        setupTableColumns();
        setupFormControls();
        setupEventListeners();

        Platform.runLater(() -> {
            initializeProjectCache();
            loadAllMilestones();
            updateStatusLabels();
        });
    }

    // --- Setup methods ---
    private void setupTableColumns() {
        coMilestoneNo.setCellValueFactory(c ->
            new javafx.beans.property.SimpleStringProperty(generateMilestoneNo(c.getValue().getMilestoneID())));
        coMilestoneName.setCellValueFactory(c ->
            new javafx.beans.property.SimpleStringProperty(c.getValue().getMilestoneName()));
        coMilestoneDate.setCellValueFactory(c ->
            new javafx.beans.property.SimpleStringProperty(c.getValue().getDate()));
        coMilestoneStatus.setCellValueFactory(c ->
            new javafx.beans.property.SimpleStringProperty(formatStatusForDisplay(c.getValue().getStatus(), c.getValue().getMilestoneID())));
        coMilestoneProject.setCellValueFactory(c ->
            new javafx.beans.property.SimpleStringProperty(generateProjectNo(c.getValue().getProjectID())));

        milestoneList = FXCollections.observableArrayList();
        tableMilestone.setItems(milestoneList);
    }

    private void setupFormControls() {
        cbMilestoneStatus.setItems(FXCollections.observableArrayList("Not Started", "In Progress", "Completed"));
        txtMilestoneNo.setEditable(true);
        txtMilestoneNo.clear();
        showEditButtons(false);
    }

    private void setupEventListeners() {
        tableMilestone.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
            if (newSel != null) {
                populateForm(newSel);
                showEditButtons(true);
            } else {
                showEditButtons(false);
            }
        });

        txtMilestoneNo.textProperty().addListener((obs, oldText, newText) -> {
            if (newText != null && newText.matches("^MS[0-9]{4}$")) {
                if (!searchAndPopulateMilestone(newText.trim())) showEditButtons(false);
            }
        });
    }

    private void showEditButtons(boolean show) {
        btnUpdateMilestone.setVisible(show);
        btnDeleteMilestone.setVisible(show);
    }

    // --- Event handlers ---
    @FXML
    private void handleAddMilestone(ActionEvent e) {
        ValidationResult validation = validateMilestoneInput();
        if (!validation.isValid) {
            showAlert(Alert.AlertType.WARNING, "Invalid Input", validation.errorMessage);
            return;
        }
        
        try {
            String name = txtMilestoneName.getText().trim();
            LocalDate date = dpMilestoneDate.getValue();
            String status = cbMilestoneStatus.getValue();
            String milestoneNo = txtMilestoneNo.getText().trim();
            Integer projectId = findProjectId(cbMilestoneProject.getValue());
            
            // Check if MilestoneNo has valid format (MSxxxx)
            boolean wasCustomFormat = false;
            String originalInput = milestoneNo;
            int milestoneNoInt;
            
            if (!isValidMilestoneFormat(milestoneNo)) {
                wasCustomFormat = true;
                // Generate next milestone number automatically
                int maxId = milestoneList.stream().mapToInt(Milestone::getMilestoneID).max().orElse(0);
                milestoneNoInt = maxId + 1 + MILESTONE_OFFSET;
            } else {
                // Extract numeric part from valid MilestoneNo (e.g., "MS1013" -> 1013)
                milestoneNoInt = extractMilestoneNumber(milestoneNo);
            }
            
            // Create milestone with temporary ID - database will generate the real ID
            Milestone m = new Milestone(0, milestoneNoInt, name, date.toString(), formatStatusForDatabase(status), projectId);

            // Save to database (let DB generate ID)
            Milestone savedMilestone = milestoneDao.saveAndReturn(m);
            
            // Spara ursprunglig status för framtida display
            if (status.equals("Not Started") || status.equals("In Progress")) {
                originalStatusMap.put(savedMilestone.getMilestoneID(), status);
            }
            
            // Add to list and update UI
            milestoneList.add(savedMilestone);
            updateStatusLabels();
            clearForm();

            // Show appropriate message based on whether format was changed
            String finalMilestoneNo = generateMilestoneNo(savedMilestone.getMilestoneID());
            if (wasCustomFormat) {
                showAlert(Alert.AlertType.INFORMATION, "Milestone Added", 
                    "Invalid milestone format '" + originalInput + "' was replaced.\n" +
                    "Milestone added with generated number: " + finalMilestoneNo + "\n" +
                    "All other data was saved as entered.");
            } else {
                showAlert(Alert.AlertType.INFORMATION, "Added", "Milestone added: " + finalMilestoneNo);
            }
        } catch (Exception ex) {
            showAlert(Alert.AlertType.ERROR, "Add Error", ex.getMessage());
        }
    }

    @FXML
    private void handleUpdateMilestone(ActionEvent e) {
        Milestone selected = tableMilestone.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "No Selection", "Select a milestone to update.");
            return;
        }
        ValidationResult validation = validateMilestoneInput();
        if (!validation.isValid) {
            showAlert(Alert.AlertType.WARNING, "Invalid Input", validation.errorMessage);
            return;
        }
        try {
            String name = txtMilestoneName.getText().trim();
            LocalDate date = dpMilestoneDate.getValue();
            String status = cbMilestoneStatus.getValue();
            Integer projectId = findProjectId(cbMilestoneProject.getValue());

            selected.setMilestoneName(name);
            selected.setDate(date.toString());
            selected.setStatus(formatStatusForDatabase(status));
            selected.setProjectID(projectId);

            // Spara ursprunglig status för framtida display
            if (status.equals("Not Started") || status.equals("In Progress")) {
                originalStatusMap.put(selected.getMilestoneID(), status);
            } else if (status.equals("Completed")) {
                // Ta bort från mappning om den blev "Completed"
                originalStatusMap.remove(selected.getMilestoneID());
            }

            milestoneDao.update(selected);
            tableMilestone.refresh();
            updateStatusLabels();

            showAlert(Alert.AlertType.INFORMATION, "Updated", "Milestone updated.");
        } catch (Exception ex) {
            showAlert(Alert.AlertType.ERROR, "Update Error", ex.getMessage());
        }
    }

    @FXML
    private void handleDeleteMilestone(ActionEvent e) {
        Milestone selected = tableMilestone.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "No Selection", "Select a milestone to delete.");
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete milestone \"" + selected.getMilestoneName() + "\"?",
                ButtonType.OK, ButtonType.CANCEL);
        confirm.showAndWait().ifPresent(r -> {
            if (r == ButtonType.OK) {
                try {
                    milestoneDao.delete(selected.getMilestoneID());
                    milestoneList.remove(selected);
                    
                    // Ta bort från status-mappning
                    originalStatusMap.remove(selected.getMilestoneID());
                    
                    updateStatusLabels();
                    clearForm();
                    showAlert(Alert.AlertType.INFORMATION, "Deleted", "Milestone deleted.");
                } catch (Exception ex) {
                    showAlert(Alert.AlertType.ERROR, "Delete Error", ex.getMessage());
                }
            }
        });
    }

    @FXML
    private void handleClearFieldsMilestone(ActionEvent e) {
        clearForm();
        updateStatusLabels();
    }

    @FXML
    private void handleGenerateNew(ActionEvent e) {
        refreshAutoMilestoneNo();
        showAlert(Alert.AlertType.INFORMATION, "Generated", "New milestone number: " + txtMilestoneNo.getText());
    }

    @FXML
    private void handleLoadAllMilestone(ActionEvent e) {
        try {
            milestoneList.setAll(milestoneDao.findAll());
            milestonesLoaded = true;
            lblLoadMilestones.setText("Loaded: " + milestoneList.size() + " milestones");
            lblMilestoneCount.setText("Count: " + milestoneList.size());
        } catch (Exception ex) {
            showAlert(Alert.AlertType.ERROR, "Load Error", ex.getMessage());
        }
    }

    @FXML
    private void handleLoadByProject(ActionEvent e) {
        String projectNo = cbMilestoneProject.getValue();
        Integer projectId = findProjectId(projectNo);
        
        if (projectId == null) {
            showAlert(Alert.AlertType.WARNING, "No Project", "Select a valid project.");
            return;
        }
        try {
            List<Milestone> list = milestoneDao.findByProjectSorted(projectId);
            
            milestoneList.setAll(list);
            lblLoadMilestones.setText("Loaded: " + list.size() + " milestones");
            lblMilestoneCount.setText("Count: " + list.size());
            System.out.println("DEBUG: Successfully updated UI with milestone list");
        } catch (Exception ex) {
            System.out.println("DEBUG: Exception in handleLoadByProject: " + ex.getMessage());
            ex.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Load Error", ex.getMessage());
        }
    }

    @FXML
    private void handleCountMilestones(ActionEvent e) {
        String projectNo = cbMilestoneProject.getValue();
        Integer projectId = findProjectId(projectNo);
        if (projectId == null) {
            showAlert(Alert.AlertType.WARNING, "No Project", "Select a valid project.");
            return;
        }
        try {
            int count = milestoneDao.countByProject(projectId);
            lblMilestoneCount.setText("Count: " + count);
        } catch (Exception ex) {
            showAlert(Alert.AlertType.ERROR, "Count Error", ex.getMessage());
        }
    }

    // --- Helpers ---
    private void populateForm(Milestone m) {
        txtMilestoneNo.setText(generateMilestoneNo(m.getMilestoneID()));
        txtMilestoneName.setText(m.getMilestoneName());
        
        // Använd den nya metoden med milestoneId för korrekt status-mappning
        String displayStatus = formatStatusForDisplay(m.getStatus(), m.getMilestoneID());
        cbMilestoneStatus.setValue(displayStatus);
        
        cbMilestoneProject.setValue(generateProjectNo(m.getProjectID()));
        dpMilestoneDate.setValue(parseDate(m.getDate()));
    }

    private LocalDate parseDate(String date) {
        try { return date != null ? LocalDate.parse(date) : null; }
        catch (Exception e) { return null; }
    }

    private void clearForm() {
        refreshAutoMilestoneNo();
        txtMilestoneName.clear();
        dpMilestoneDate.setValue(null);
        cbMilestoneStatus.getSelectionModel().clearSelection();
        cbMilestoneProject.getSelectionModel().clearSelection();
        tableMilestone.getSelectionModel().clearSelection();
        showEditButtons(false);
    }

    private void refreshAutoMilestoneNo() {
        txtMilestoneNo.setText(generateNextMilestoneNo());
    }

    private void updateStatusLabels() {
        lblLoadMilestones.setText("Loaded: " + milestoneList.size());
    }

    // Konverterar status från databas till användarvänlig display med stöd för ursprunglig status
    private String formatStatusForDisplay(String dbStatus, Integer milestoneId) {
        if (dbStatus != null && dbStatus.equalsIgnoreCase("ongoing")) {
            // Kolla om vi har sparat den ursprungliga statusen
            String originalStatus = originalStatusMap.get(milestoneId);
            
            if (originalStatus != null) {
                return originalStatus;
            } else {
                return "Not Started";  // Standard om vi inte har sparat status
            }
        }
        
        return dbStatus;
    }

    private String formatStatusForDatabase(String s) {
        if (s != null) {
            if (s.equalsIgnoreCase("In Progress") || s.equalsIgnoreCase("Not Started")) {
                return "Ongoing";
            }
        }
        return s;
    }

    private String generateProjectNo(int id) {
        return "PR" + (id + PROJECT_OFFSET);
    }

    private Integer findProjectId(String no) {
        if (no == null || !no.startsWith("PR")) return null;
        try { return Integer.parseInt(no.substring(2)) - PROJECT_OFFSET; }
        catch (NumberFormatException e) { return null; }
    }

    private String generateMilestoneNo(int id) {
        return "MS" + (id + MILESTONE_OFFSET);
    }

    private String generateNextMilestoneNo() {
        int maxId = milestoneList.stream().mapToInt(Milestone::getMilestoneID).max().orElse(0);
        return generateMilestoneNo(maxId + 1);
    }

    private boolean searchAndPopulateMilestone(String no) {
        Integer id = findMilestoneId(no);
        if (id == null || id == -1) return false;
        return milestoneList.stream()
            .filter(m -> m.getMilestoneID() == id)
            .findFirst()
            .map(m -> { populateForm(m); return true; })
            .orElse(false);
    }

    private Integer findMilestoneId(String no) {
        if (no == null || !no.startsWith("MS")) return null;
        try { return Integer.parseInt(no.substring(2)) - MILESTONE_OFFSET; }
        catch (NumberFormatException e) { return null; }
    }

    private void showAlert(Alert.AlertType type, String title, String msg) {
        Alert alert = new Alert(type, msg, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    // --- Validation helper ---
    private ValidationResult validateMilestoneInput() {
        System.out.println("DEBUG: Validating input...");
        
        if (txtMilestoneName.getText() == null || txtMilestoneName.getText().trim().isEmpty()) {
            return new ValidationResult(false, "Milestone name is required.");
        }
        System.out.println("DEBUG: Name validation passed: " + txtMilestoneName.getText());
        
        if (dpMilestoneDate.getValue() == null) {
            return new ValidationResult(false, "Milestone date is required.");
        }
        System.out.println("DEBUG: Date validation passed: " + dpMilestoneDate.getValue());
        
        // Validate that milestone date is not before 2022-01-01
        LocalDate minDate = LocalDate.of(2022, 1, 1);
        LocalDate selectedDate = dpMilestoneDate.getValue();
        LocalDate today = LocalDate.now();
        
        System.out.println("DEBUG: Date validation details:");
        System.out.println("  - Selected date: " + selectedDate);
        System.out.println("  - Today's date: " + today);
        System.out.println("  - Min date (2022-01-01): " + minDate);
        System.out.println("  - Is selected before min? " + selectedDate.isBefore(minDate));
        System.out.println("  - Is selected after today? " + selectedDate.isAfter(today));
        
        if (selectedDate.isBefore(minDate)) {
            return new ValidationResult(false, "Milestone date cannot be before 2022-01-01. Selected date: " + selectedDate);
        }
        
        if (cbMilestoneStatus.getValue() == null || cbMilestoneStatus.getValue().trim().isEmpty()) {
            return new ValidationResult(false, "Status must be selected. Available options: " + cbMilestoneStatus.getItems());
        }
        
        if (cbMilestoneProject.getValue() == null || cbMilestoneProject.getValue().trim().isEmpty()) {
            return new ValidationResult(false, "Project must be selected. Available projects: " + cbMilestoneProject.getItems().size() + " loaded");
        }
        System.out.println("DEBUG: Project validation passed: " + cbMilestoneProject.getValue());
        
        Integer projectId = findProjectId(cbMilestoneProject.getValue());
        if (projectId == null) {
            return new ValidationResult(false, "Invalid project selected. Please select a valid project from the dropdown.");
        }
        System.out.println("DEBUG: Project ID validation passed: " + projectId);
        
        return new ValidationResult(true, "");
    }

    private static class ValidationResult {
        final boolean isValid;
        final String errorMessage;
        ValidationResult(boolean valid, String error) { this.isValid = valid; this.errorMessage = error; }
    }

    // --- Project cache init (helper) ---
    private void initializeProjectCache() {
        if (projectCacheInitialized) return;
        Task<List<Project>> task = new Task<>() {
            @Override
            protected List<Project> call() throws Exception {
                return projectDao.findAll();
            }
        };
        task.setOnSucceeded(e -> Platform.runLater(() -> {
            allProjects.setAll(task.getValue());
            cbMilestoneProject.setItems(FXCollections.observableArrayList(
                allProjects.stream().map(p -> generateProjectNo(p.getProjectID())).sorted().toList()
            ));
            projectCacheInitialized = true;
        }));
        task.setOnFailed(e -> Platform.runLater(() -> projectCacheInitialized = true));
        new Thread(task).start();
    }

    private void loadAllMilestones() {
        if (milestonesLoaded || isLoadingMilestones) return;
        isLoadingMilestones = true;
        Task<List<Milestone>> task = new Task<>() {
            @Override
            protected List<Milestone> call() throws Exception {
                return milestoneDao.findAll();
            }
        };
        task.setOnSucceeded(e -> Platform.runLater(() -> {
            milestoneList.setAll(task.getValue());
            updateStatusLabels();
            milestonesLoaded = true;
            isLoadingMilestones = false;
        }));
        task.setOnFailed(e -> Platform.runLater(() -> {
            milestonesLoaded = true;
            isLoadingMilestones = false;
        }));
        new Thread(task).start();
    }
    
    /**
     * Extracts the numeric part from a milestone number string.
     * For example: "MS1013" -> 1013, "1013" -> 1013
     */
    private int extractMilestoneNumber(String milestoneNoStr) {
        if (milestoneNoStr == null || milestoneNoStr.trim().isEmpty()) {
            throw new IllegalArgumentException("MilestoneNo cannot be null or empty");
        }
        
        // Remove "MS" prefix and any non-digit characters, then parse as int
        String numericPart = milestoneNoStr.replaceAll("[^0-9]", "");
        
        if (numericPart.isEmpty()) {
            throw new IllegalArgumentException("No numeric part found in MilestoneNo: " + milestoneNoStr);
        }
        
        try {
            return Integer.parseInt(numericPart);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Unable to parse numeric part of MilestoneNo: " + milestoneNoStr, e);
        }
    }
    
    /**
     * Validates if a milestone number has the correct format (MSxxxx where xxxx are digits).
     * For example: "MS1013" -> true, "Z4test" -> false, "1013" -> false
     */
    private boolean isValidMilestoneFormat(String milestoneNoStr) {
        if (milestoneNoStr == null || milestoneNoStr.trim().isEmpty()) {
            return false;
        }
        
        // Check if it starts with "MS" followed by only digits
        return milestoneNoStr.matches("^MS\\d+$");
    }
    

}