package se.lu.ics.Controllers;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.util.StringConverter;
import se.lu.ics.Data.EmployeeDao;
import se.lu.ics.Data.ProjectDao;
import se.lu.ics.Data.WorkDao;
import se.lu.ics.Models.Employee;
import se.lu.ics.Models.Project;
import se.lu.ics.Models.Work;

import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

public class WorkViewController implements Initializable {

    // ---------- Constants ----------
    private static final double HOURS_PER_WEEK = 40.0;
    private static final int MIN_HOURS = 0, MAX_HOURS = 10_000;

    // ---------- FXML ----------
    @FXML private ComboBox<Integer> cbWorkProjectNo, cbWorkEmployeeNo;
    @FXML private TextField txtWorkHours, filterField;
    @FXML private Label hoursError, lblEmployeeTotal, lblAllTotal, lblCompanyFteWeeks, lblHardest, lblProjectShare;
    @FXML private TableView<Work> tbWork;
    @FXML private TableColumn<Work, String> coWorkProjectNo, coWorkEmployeeNo, coWorkEmployeeName, coWorkWeeksFte;
    @FXML private TableColumn<Work, Integer> coWorkHours;

    // ---------- DAOs ----------
    private WorkDao workDao;
    private ProjectDao projectDao;
    private EmployeeDao employeeDao;

    // ---------- Data ----------
    private final ObservableList<Work> allWork = FXCollections.observableArrayList();
    private final ObservableList<Work> filteredWork = FXCollections.observableArrayList();
    private Map<Integer, Employee> employeeCache = Collections.emptyMap();
    private Map<Integer, String> projectNoCache = Collections.emptyMap(), employeeNoCache = Collections.emptyMap();
    private ObservableList<Integer> allProjectIds = FXCollections.observableArrayList(), allEmployeeIds = FXCollections.observableArrayList();
    private boolean isUpdatingData = false, cacheInitialized = false, statisticsNeedUpdate = true;
    private WorkDao.WorkStatistics cachedGlobalStats = null;
    private Map<Integer, Integer> cachedProjectHours = new HashMap<>();
    private int cachedTotalCompanyHours = 0;

    public WorkViewController() {
        try {
            workDao = new WorkDao();
            projectDao = new ProjectDao();
            employeeDao = new EmployeeDao();
        } catch (IOException e) {
            e.printStackTrace();
            show(Alert.AlertType.ERROR, "Init error", "Could not initialize database access.");
        }
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Table columns
        coWorkProjectNo.setCellValueFactory(c -> new ReadOnlyStringWrapper(
                projectNoCache.getOrDefault(c.getValue().getProjectID(), formatProjectNo(c.getValue().getProjectID()))));
        coWorkEmployeeNo.setCellValueFactory(c -> new ReadOnlyStringWrapper(
                employeeNoCache.getOrDefault(c.getValue().getEmployeeID(), "emp" + c.getValue().getEmployeeID())));
        coWorkHours.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getHours()));
        coWorkWeeksFte.setCellValueFactory(c -> new ReadOnlyStringWrapper(
                String.format("%.1f w", c.getValue().getHours() / HOURS_PER_WEEK)));
        coWorkEmployeeName.setCellValueFactory(c -> new ReadOnlyStringWrapper(
                buildDisplayName(employeeCache.get(c.getValue().getEmployeeID()), c.getValue().getEmployeeID())));

        tbWork.setItems(filteredWork);
        tbWork.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        initializeStaticCaches();
        setupComboBoxConverters();
        loadAllWork();
        Platform.runLater(this::refreshEmployeeCache);

        tbWork.getSelectionModel().selectedItemProperty().addListener((obs, o, w) -> {
            if (w != null) {
                isUpdatingData = true;
                cbWorkProjectNo.setValue(w.getProjectID());
                cbWorkEmployeeNo.setValue(w.getEmployeeID());
                txtWorkHours.setText(String.valueOf(w.getHours()));
                hoursError.setVisible(false);
                isUpdatingData = false;
                updateProjectSharePreview();
                updateSideInfo();
            }
        });

        filterField.textProperty().addListener((obs, o, n) -> applyFilter(n));
        cbWorkProjectNo.valueProperty().addListener((obs, o, n) -> { if (!isUpdatingData) updateProjectSharePreview(); });
        txtWorkHours.textProperty().addListener((obs, o, n) -> { if (!isUpdatingData) safeUpdateProjectShare(n); });
        cbWorkEmployeeNo.valueProperty().addListener((obs, o, n) -> { if (!isUpdatingData) updateSideInfo(); });
    }

    // ---------- ComboBox ----------
    private void setupComboBoxConverters() {
        cbWorkEmployeeNo.setConverter(new StringConverter<>() {
            @Override public String toString(Integer id) { return id == null ? "" : employeeNoCache.getOrDefault(id, "emp" + id); }
            @Override public Integer fromString(String s) { return findIdFromCache(employeeNoCache, s); }
        });
        cbWorkProjectNo.setConverter(new StringConverter<>() {
            @Override public String toString(Integer id) { return id == null ? "" : projectNoCache.getOrDefault(id, formatProjectNo(id)); }
            @Override public Integer fromString(String s) { return findIdFromCache(projectNoCache, s); }
        });
    }
    private Integer findIdFromCache(Map<Integer, String> cache, String value) {
        return cache.entrySet().stream().filter(e -> e.getValue().equals(value)).map(Map.Entry::getKey).findFirst().orElse(null);
    }

    // ---------- Buttons ----------
    @FXML private void handleWorkLoadAll() { refreshEmployeeCache(); loadAllWork(); filterField.clear(); }
    @FXML private void handleWorkClearFields() { clearForm(); }

    @FXML
    private void handleAddWork() {
        Optional<Integer> hours = parseHours();
        if (!validateInputs(hours)) return;
        int pId = cbWorkProjectNo.getValue(), eId = cbWorkEmployeeNo.getValue(), h = hours.get();

        if (exists(pId, eId)) {
            show(Alert.AlertType.WARNING, "Duplicate Entry", "This employee already has hours for this project. Use 'Update' instead.");
            return;
        }
        if (wouldExceedSixtyPercent(pId, h, null)) warnCapacity("Adding");

        try { workDao.save(new Work(pId, eId, h)); loadAllWork(); clearForm(); }
        catch (Exception ex) { ex.printStackTrace(); show(Alert.AlertType.ERROR, "Add failed", ex.getMessage()); }
    }

    @FXML
    private void handleUpdateWork() {
        Optional<Integer> hours = parseHours();
        if (!validateInputs(hours)) return;
        int pId = cbWorkProjectNo.getValue(), eId = cbWorkEmployeeNo.getValue(), h = hours.get();

        if (!exists(pId, eId)) {
            show(Alert.AlertType.WARNING, "Entry Not Found", "No existing work entry found. Use 'Add' instead.");
            return;
        }
        Integer old = allWork.stream().filter(w -> w.getProjectID() == pId && w.getEmployeeID() == eId)
                             .map(Work::getHours).findFirst().orElse(null);
        if (wouldExceedSixtyPercent(pId, h, old)) warnCapacity("Updating");

        try { workDao.updateHours(pId, eId, h); loadAllWork(); clearForm(); }
        catch (Exception ex) { ex.printStackTrace(); show(Alert.AlertType.ERROR, "Update failed", ex.getMessage()); }
    }

    @FXML
    private void handleDeleteWork() {
        if (cbWorkProjectNo.getValue() == null || cbWorkEmployeeNo.getValue() == null) {
            show(Alert.AlertType.WARNING, "Validation", "Select a row (project + employee) to delete."); return;
        }
        int pId = cbWorkProjectNo.getValue(), eId = cbWorkEmployeeNo.getValue();
        try { workDao.delete(pId, eId); optimizedRemoveFromLocal(pId, eId); clearForm(); }
        catch (Exception ex) { ex.printStackTrace(); show(Alert.AlertType.ERROR, "Delete failed", ex.getMessage()); }
    }

    @FXML
    private void handleShowHardest() {
        WorkDao.WorkStatistics stats = workDao.getWorkStatistics(null, null);
        if (stats.hardestWorkingEmployeeId == -1) { lblHardest.setText("—"); return; }
        Employee e = safeEmployeeFromCache(stats.hardestWorkingEmployeeId);
        lblHardest.setText(buildDisplayName(e, stats.hardestWorkingEmployeeId) + " • " + stats.maxEmployeeHours + "h");
    }

    @FXML
    private void handleShowAllConsultantProjects() {
        try {
            List<WorkDao.ProjectWithAllConsultants> projects = workDao.getProjectsWithAllConsultants();
            
            if (projects.isEmpty()) {
                show(Alert.AlertType.INFORMATION, "No Projects Found", 
                        "No projects were found that involve every consultant in the company.\n\n" +
                        "This means no single project has work entries from all employees.");
                return;
            }
            
            StringBuilder info = new StringBuilder();
            info.append("Projects involving ALL consultants (").append(projects.size()).append(" found):\n\n");
            
            for (WorkDao.ProjectWithAllConsultants project : projects) {
                info.append("• ").append(formatProjectNo(project.projectId))
                    .append(" - ").append(project.projectName).append("\n");
                info.append("  Total consultants: ").append(project.totalConsultants).append("\n");
                info.append("  Total hours: ").append(project.totalHours).append(" h\n");
                info.append("  Average per consultant: ")
                    .append(String.format("%.1f", project.averageHoursPerConsultant)).append(" h\n");
                info.append("  Project weeks (FTE): ")
                    .append(String.format("%.1f", project.totalHours / HOURS_PER_WEEK)).append(" w\n\n");
            }
            
            // Add instruction for detailed view
            info.append("💡 Tip: To see detailed breakdown of a specific project, ");
            info.append("use the filter 'proj:").append(projects.get(0).projectId).append("' in the filter field.");
            
            show(Alert.AlertType.INFORMATION, "Projects with All Consultants", info.toString());
            
        } catch (Exception ex) {
            ex.printStackTrace();
            show(Alert.AlertType.ERROR, "Query Failed", "Could not retrieve projects with all consultants: " + ex.getMessage());
        }
    }

    // ---------- Validation ----------
    private boolean validateInputs(Optional<Integer> hours) {
        if (cbWorkProjectNo.getValue() == null || cbWorkEmployeeNo.getValue() == null || hours.isEmpty()) {
            show(Alert.AlertType.WARNING, "Validation", "Please select project, employee and valid hours (0–10 000).");
            return false;
        }
        return true;
    }
    private boolean exists(int pId, int eId) { return allWork.stream().anyMatch(w -> w.getProjectID() == pId && w.getEmployeeID() == eId); }
    private void warnCapacity(String action) {
        show(Alert.AlertType.WARNING, "Capacity warning", action + " will make this project exceed 60% of company hours.");
    }

    // ---------- Alerts ----------
    private void show(Alert.AlertType type, String title, String msg) {
        Alert alert = new Alert(type);
        alert.setTitle(title); alert.setHeaderText(null); alert.setContentText(msg);
        alert.showAndWait();
    }

    // ---------- Formatting ----------
    private static String formatProjectNo(int id) { return "PR" + String.format("%04d", id); }
    private static String nullToEmpty(String s) { return s == null ? "" : s; }

    private String buildDisplayName(Employee e, int id) {
        if (e == null) return "Employee " + id;
        String full = (nullToEmpty(e.getFirstName()) + " " + nullToEmpty(e.getLastName())).trim();
        if (!full.isBlank()) return full;
        try { String email = (String) Employee.class.getMethod("getEmail").invoke(e);
              if (email != null && !email.isBlank()) return email; }
        catch (Throwable ignored) {}
        return "Employee " + id;
    }
    private Employee safeEmployeeFromCache(Integer id) { return id == null ? null : employeeCache.get(id); }
    private String displayNameLower(int id) { return buildDisplayName(employeeCache.get(id), id).toLowerCase(Locale.ROOT); }

    // ---------- Data & Cache ----------
    private void initializeStaticCaches() {
        if (cacheInitialized) return;
        Task<Void> loadCacheTask = new Task<>() {
            @Override protected Void call() throws Exception {
                List<Employee> allEmployees = employeeDao.findAll();
                List<Project> allProjects = projectDao.findAll();
                Platform.runLater(() -> {
                    employeeCache = allEmployees.stream().collect(Collectors.toMap(Employee::getEmployeeID, e -> e));
                    projectNoCache = allProjects.stream().collect(Collectors.toMap(Project::getProjectID, p -> formatProjectNo(p.getProjectNo())));
                    employeeNoCache = allEmployees.stream().collect(Collectors.toMap(Employee::getEmployeeID, e -> e.getEmployeeNo() != null ? e.getEmployeeNo() : "emp" + e.getEmployeeID()));
                    allProjectIds.setAll(allProjects.stream().map(Project::getProjectID).sorted().toList());
                    allEmployeeIds.setAll(allEmployees.stream().map(Employee::getEmployeeID).sorted().toList());
                    cbWorkProjectNo.setItems(allProjectIds);
                    cbWorkEmployeeNo.setItems(allEmployeeIds);
                    cacheInitialized = true;
                });
                return null;
            }
        };
        loadCacheTask.setOnFailed(e -> Platform.runLater(() -> show(Alert.AlertType.ERROR, "Cache Error", "Failed to load cache data: " + loadCacheTask.getException().getMessage())));
        new Thread(loadCacheTask).start();
    }

    private void refreshEmployeeCache() {
        Task<List<Employee>> refreshTask = new Task<>() {
            @Override protected List<Employee> call() throws Exception { return employeeDao.findAll(); }
        };
        refreshTask.setOnSucceeded(e -> Platform.runLater(() -> {
            List<Employee> allEmployees = refreshTask.getValue();
            employeeCache = allEmployees.stream().collect(Collectors.toMap(Employee::getEmployeeID, emp -> emp));
            employeeNoCache = allEmployees.stream().collect(Collectors.toMap(Employee::getEmployeeID, emp -> emp.getEmployeeNo() != null ? emp.getEmployeeNo() : "emp" + emp.getEmployeeID()));
            allEmployeeIds.setAll(allEmployees.stream().map(Employee::getEmployeeID).sorted().toList());
            cbWorkEmployeeNo.setItems(allEmployeeIds);
            tbWork.refresh();
            updateSideInfo();
        }));
        new Thread(refreshTask).start();
    }

    private void loadAllWork() {
        Task<List<Work>> loadWorkTask = new Task<>() {
            @Override protected List<Work> call() throws Exception { return workDao.findAll(); }
        };
        loadWorkTask.setOnSucceeded(e -> Platform.runLater(() -> {
            isUpdatingData = true; statisticsNeedUpdate = true;
            allWork.setAll(loadWorkTask.getValue());
            isUpdatingData = false;
            applyFilter(filterField == null ? "" : filterField.getText());
            updateSideInfo();
            tbWork.refresh();
        }));
        loadWorkTask.setOnFailed(e -> Platform.runLater(() -> {
            allWork.clear(); filteredWork.clear();
            show(Alert.AlertType.ERROR, "Load Error", "Failed to load work data: " + loadWorkTask.getException().getMessage());
        }));
        new Thread(loadWorkTask).start();
    }

    private void applyFilter(String q) {
        String query = (q == null ? "" : q.trim().toLowerCase(Locale.ROOT));
        filteredWork.setAll(query.isEmpty() ? allWork : allWork.stream().filter(w -> matchesQuery(w, query)).toList());
        updateSideInfo();
    }
    private boolean matchesQuery(Work w, String query) {
        if (query.startsWith("proj:")) return String.valueOf(w.getProjectID()).equals(query.substring(5).trim());
        if (query.startsWith("emp:")) return String.valueOf(w.getEmployeeID()).equals(query.substring(4).trim());
        if (query.startsWith("hours:")) return String.valueOf(w.getHours()).equals(query.substring(6).trim());
        if (query.startsWith("name:") || query.startsWith("ename:")) return displayNameLower(w.getEmployeeID()).contains(query.split(":",2)[1].trim());
        
        // Special filter for projects with all consultants
        if (query.equals("allconsultants") || query.equals("all-consultants")) {
            try {
                List<WorkDao.ProjectWithAllConsultants> allConsultantProjects = workDao.getProjectsWithAllConsultants();
                return allConsultantProjects.stream().anyMatch(p -> p.projectId == w.getProjectID());
            } catch (Exception ex) {
                return false; // If query fails, don't match
            }
        }
        
        if (query.matches("\\d+")) return List.of(w.getProjectID(), w.getEmployeeID(), w.getHours()).stream().anyMatch(val -> String.valueOf(val).equals(query));
        return displayNameLower(w.getEmployeeID()).contains(query);
    }

    private void updateSideInfo() {
        if (isUpdatingData) return;
        updateStatisticsCacheIfNeeded();
        Integer emp = cbWorkEmployeeNo.getValue();
        if (emp != null) {
            int empHours = allWork.stream().filter(w -> w.getEmployeeID() == emp).mapToInt(Work::getHours).sum();
            lblEmployeeTotal.setText(empHours + " h");
        } else lblEmployeeTotal.setText("—");
        lblAllTotal.setText(cachedTotalCompanyHours + " h");
        lblCompanyFteWeeks.setText(String.format("%.1f w", cachedTotalCompanyHours / HOURS_PER_WEEK));
        updateProjectSharePreview();
    }

    private void updateProjectSharePreview() {
        Integer pId = cbWorkProjectNo.getValue();
        Optional<Integer> hours = parseHoursAllowBlank();
        if (pId == null || hours.isEmpty()) { lblProjectShare.setText("—"); return; }
        updateStatisticsCacheIfNeeded();
        int currentProjectHours = cachedProjectHours.getOrDefault(pId, 0);
        int newProjectHours = currentProjectHours + hours.get();
        int newCompanyTotal = cachedTotalCompanyHours + hours.get();
        if (newCompanyTotal == 0) { lblProjectShare.setText("—"); return; }
        double share = (newProjectHours * 100.0) / newCompanyTotal;
        double projectWeeks = newProjectHours / HOURS_PER_WEEK;
        lblProjectShare.setText(share > 60.0
                ? String.format(Locale.ROOT, "⚠️ %.1f %% (%.1f w)", share, projectWeeks)
                : String.format(Locale.ROOT, "%.1f %% (%.1f w)", share, projectWeeks));
    }

    private void updateStatisticsCacheIfNeeded() {
        if (!statisticsNeedUpdate && cachedGlobalStats != null) return;
        cachedGlobalStats = workDao.getWorkStatistics(null, null);
        cachedTotalCompanyHours = cachedGlobalStats.totalHours;
        cachedProjectHours.clear();
        allWork.stream().collect(Collectors.groupingBy(Work::getProjectID, Collectors.summingInt(Work::getHours)))
                .forEach(cachedProjectHours::put);
        statisticsNeedUpdate = false;
    }

    // ---------- Parsing ----------
    private Optional<Integer> parseHours() {
        String text = getHoursText();
        try {
            int value = Integer.parseInt(text);
            boolean valid = value >= MIN_HOURS && value <= MAX_HOURS;
            updateHoursError(valid);
            return valid ? Optional.of(value) : Optional.empty();
        } catch (Exception ex) {
            updateHoursError(false); return Optional.empty();
        }
    }
    private Optional<Integer> parseHoursAllowBlank() {
        String text = getHoursText();
        if (text.isEmpty()) return Optional.empty();
        try { return Optional.of(Integer.parseInt(text)); }
        catch (Exception ex) { return Optional.empty(); }
    }
    private String getHoursText() { return txtWorkHours.getText() == null ? "" : txtWorkHours.getText().trim(); }
    private void updateHoursError(boolean valid) {
        String message = valid ? "" : "Hours must be 0–10 000";
        hoursError.setText(message);
        hoursError.setVisible(!valid);
        hoursError.setManaged(!valid);
    }

    private boolean wouldExceedSixtyPercent(int projectId, int newHours, Integer oldHours) {
        updateStatisticsCacheIfNeeded();
        int currentProjectHours = cachedProjectHours.getOrDefault(projectId, 0);
        int companyAfter = cachedTotalCompanyHours - (oldHours == null ? 0 : oldHours) + newHours;
        int projectAfter = currentProjectHours - (oldHours == null ? 0 : oldHours) + newHours;
        return companyAfter > 0 && projectAfter / (double) companyAfter > 0.60;
    }

    private void clearForm() {
        cbWorkProjectNo.setValue(null);
        cbWorkEmployeeNo.setValue(null);
        txtWorkHours.clear();
        tbWork.getSelectionModel().clearSelection();
        hoursError.setVisible(false);
        hoursError.setManaged(false);
        lblProjectShare.setText("—");
        updateSideInfo();
    }

    private void optimizedRemoveFromLocal(int projectId, int employeeId) {
        isUpdatingData = true;
        allWork.removeIf(w -> w.getProjectID() == projectId && w.getEmployeeID() == employeeId);
        filteredWork.removeIf(w -> w.getProjectID() == projectId && w.getEmployeeID() == employeeId);
        statisticsNeedUpdate = true;
        isUpdatingData = false;
        updateSideInfo();
        tbWork.refresh();
    }

    // ---------- Extra Methods ----------
    public void addMultipleWork(List<Work> workList) {
        if (workList.isEmpty()) return;
        try {
            for (Work work : workList) {
                if (wouldExceedSixtyPercent(work.getProjectID(), work.getHours(), null)) {
                    show(Alert.AlertType.WARNING, "Batch Capacity Warning", "One or more entries exceed 60%. Cancelled.");
                    return;
                }
            }
            workDao.saveBatch(workList);
            loadAllWork();
            clearForm();
            show(Alert.AlertType.INFORMATION, "Batch Success", "Added " + workList.size() + " work entries.");
        } catch (Exception ex) {
            ex.printStackTrace();
            show(Alert.AlertType.ERROR, "Batch Add Failed", ex.getMessage());
        }
    }

    public void showDetailedProjectInfo(int projectId) {
        WorkDao.ProjectEmployeeDetails details = workDao.getProjectEmployeeDetails(projectId);
        StringBuilder info = new StringBuilder();
        info.append("Project ").append(projectId).append(" Details:\n\n");
        info.append("Employees: ").append(details.employeeCount).append("\n");
        info.append("Total hours: ").append(details.totalProjectHours).append(" h\n");
        info.append("Average per employee: ").append(String.format("%.1f", details.averageHoursPerEmployee)).append(" h\n\n");
        info.append("Employees (sorted by hours):\n");
        for (int i = 0; i < details.employeeIds.size(); i++) {
            int empId = details.employeeIds.get(i);
            int hours = details.employeeHours.get(i);
            Employee emp = safeEmployeeFromCache(empId);
            info.append("- ").append(buildDisplayName(emp, empId)).append(": ").append(hours).append(" h\n");
        }
        show(Alert.AlertType.INFORMATION, "Project Details", info.toString());
    }

    /**
     * Shows detailed information about a project that involves all consultants
     */
    public void showDetailedAllConsultantProjectInfo(int projectId) {
        try {
            List<WorkDao.DetailedWorkInfo> details = workDao.getDetailedInfoForProjectWithAllConsultants(projectId);
            
            if (details.isEmpty()) {
                show(Alert.AlertType.WARNING, "Invalid Project", 
                        "Project " + formatProjectNo(projectId) + " does not involve all consultants.");
                return;
            }
            
            // Get project info from first record
            WorkDao.DetailedWorkInfo firstRecord = details.get(0);
            int totalHours = details.stream().mapToInt(d -> d.hours).sum();
            
            StringBuilder info = new StringBuilder();
            info.append("Project ").append(formatProjectNo(projectId))
                .append(" - ").append(firstRecord.projectName).append("\n");
            info.append("(This project involves ALL ").append(details.size()).append(" consultants)\n\n");
            info.append("Total project hours: ").append(totalHours).append(" h\n");
            info.append("Average per consultant: ")
                .append(String.format("%.1f", (double)totalHours / details.size())).append(" h\n");
            info.append("Project weeks (FTE): ")
                .append(String.format("%.1f", totalHours / HOURS_PER_WEEK)).append(" w\n\n");
            
            info.append("Consultant breakdown (sorted by hours):\n");
            details.stream()
                .sorted((a, b) -> Integer.compare(b.hours, a.hours))
                .forEach(d -> {
                    info.append("• ").append(d.employeeName);
                    if (d.employeeTitle != null && !d.employeeTitle.trim().isEmpty()) {
                        info.append(" (").append(d.employeeTitle).append(")");
                    }
                    info.append(": ").append(d.hours).append(" h\n");
                });
            
            show(Alert.AlertType.INFORMATION, "All-Consultant Project Details", info.toString());
            
        } catch (Exception ex) {
            ex.printStackTrace();
            show(Alert.AlertType.ERROR, "Query Failed", 
                    "Could not retrieve detailed information: " + ex.getMessage());
        }
    }

    // ---------- Utility ----------
    private void safeUpdateProjectShare(String n) {
        try {
            if (n != null && !n.trim().isEmpty()) Integer.parseInt(n.trim());
            updateProjectSharePreview();
        } catch (NumberFormatException ex) {
            lblProjectShare.setText("—");
        }
    }
}


