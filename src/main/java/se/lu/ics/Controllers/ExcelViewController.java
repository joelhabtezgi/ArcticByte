package se.lu.ics.Controllers;


import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.stage.FileChooser;


import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.prefs.Preferences;


public class ExcelViewController {


   @FXML private Label excelStatus;


   private static final String PREF_LAST_XLSX = "excel.last.xlsx.path";
   private static final String EXCEL_ONLINE_URL = "https://1drv.ms/x/c/a64d36be1da468b4/EbsTBXpz4n9EttOA6hNlRpoBdi1eV0_uSw0TV008a1AgIg?e=qX6Zdq";
   // URL för att ladda ner filen direkt (för desktop Excel)
   private static final String EXCEL_DOWNLOAD_URL = "https://1drv.ms/x/c/a64d36be1da468b4/EbsTBXpz4n9EttOA6hNlRpoBdi1eV0_uSw0TV008a1AgIg?e=qX6Zdq&download=1";


   @FXML
   private void initialize() {
       setStatus("Use 'Open in Desktop Excel' for database connections, or browse local files.");
   }


   @FXML
   private void onOpenExcelOnlineClick() {
       try {
           if (!Desktop.isDesktopSupported()) {
               setError("Desktop integration not supported on this system.");
               return;
           }
           Desktop.getDesktop().browse(new URI(EXCEL_DOWNLOAD_URL));
           setStatus("Downloading Excel file... Open with desktop Excel for database connections.");
       } catch (IOException e) {
           setError("Could not open browser: " + e.getMessage());
       } catch (URISyntaxException e) {
           setError("Invalid URL: " + e.getMessage());
       }
   }
  
   @FXML
   private void onOpenExcelWebClick() {
       try {
           if (!Desktop.isDesktopSupported()) {
               setError("Desktop integration not supported on this system.");
               return;
           }
           Desktop.getDesktop().browse(new URI(EXCEL_ONLINE_URL));
           setStatus("Opening Excel Online (limited database support)");
       } catch (IOException e) {
           setError("Could not open browser: " + e.getMessage());
       } catch (URISyntaxException e) {
           setError("Invalid URL: " + e.getMessage());
       }
   }


   @FXML
   private void onOpenExcelClick() {
       FileChooser fc = new FileChooser();
       fc.setTitle("Select Excel file (.xlsx)");
       fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel files", "*.xlsx"));
       String last = Preferences.userNodeForPackage(getClass()).get(PREF_LAST_XLSX, null);
       if (last != null) {
           File f = new File(last);
           if (f.getParentFile() != null && f.getParentFile().exists()) {
               fc.setInitialDirectory(f.getParentFile());
           }
       }
       File file = fc.showOpenDialog(null);
       if (file == null) {
           setStatus("No file selected.");
           return;
       }
       openWithDesktop(file);
   }


   @FXML
   private void onShowRefreshInstructions() {
       setStatus("📋 In Excel: Go to Data tab → 'Refresh All' or press Ctrl+Alt+F5 to update all data connections.");
   }


   private void openWithDesktop(File file) {
       try {
           if (!Desktop.isDesktopSupported()) {
               setError("Desktop integration not supported on this system.");
               return;
           }
           Desktop.getDesktop().open(file);
           Preferences.userNodeForPackage(getClass()).put(PREF_LAST_XLSX, file.getAbsolutePath());
           setStatus("Opened: " + file.getName());
       } catch (IOException e) {
           setError("Could not open file: " + e.getMessage());
       }
   }


   private void setStatus(String s) { if (excelStatus != null) excelStatus.setText(s); }
   private void setError(String s)  { if (excelStatus != null) excelStatus.setText("Error: " + s); }
  
   public static void notifyDataChanged() {
       System.out.println("Data changed - Excel users should refresh their data");
   }
}


