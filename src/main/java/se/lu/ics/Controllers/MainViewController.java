package se.lu.ics.Controllers;




import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.Label;




import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;




public class MainViewController {


  @FXML private TabPane tabPane;


  @FXML private Tab consultantTab;
  @FXML private Tab projectTab;
  @FXML private Tab workTab;
  @FXML private Tab milestoneTab;
  @FXML private Tab metadataTab;
  @FXML private Tab excelTab;


  private final Map<Tab, String> fxmlMap = new HashMap<>();




  @FXML
  private void initialize() {
      fxmlMap.put(consultantTab, "/fxml/EmployeeView.fxml");
      fxmlMap.put(projectTab,    "/fxml/ProjectView.fxml");
      fxmlMap.put(workTab,       "/fxml/WorkView.fxml");
      fxmlMap.put(milestoneTab,  "/fxml/MilestoneView.fxml");
      fxmlMap.put(metadataTab,   "/fxml/MetadataView.fxml");
      fxmlMap.put(excelTab,      "/fxml/ExcelView.fxml");


      tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
          if (newTab != null) {
              ensureLoaded(newTab);
          }
      });


      ensureLoaded(consultantTab);
  }




 
   private void ensureLoaded(Tab tab) {
      String fxmlPath = fxmlMap.get(tab);
      if (fxmlPath == null) {
          tab.setContent(new Label("No view associated."));
          return;
      }
      try {
          FXMLLoader loader = new FXMLLoader(Objects.requireNonNull(getClass().getResource(fxmlPath)));
          Parent content = loader.load();
          tab.setContent(content);
      } catch (IOException e) {
          e.printStackTrace();
          tab.setContent(new Label("Could not load " + fxmlPath));
      }
  }
}
