package se.lu.ics.Controllers;


import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import se.lu.ics.Data.ConnectionHandler;
import se.lu.ics.Data.MetadataDao;


import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;




public class MetadataViewController {


   @FXML private TableView<MetadataRow> metadataTable;
   @FXML private TableColumn<MetadataRow, String> colSchema;
   @FXML private TableColumn<MetadataRow, String> colTable;
   @FXML private TableColumn<MetadataRow, String> colName;
   @FXML private TableColumn<MetadataRow, String> colType;
   @FXML private TableColumn<MetadataRow, String> colExtra;
   @FXML private Label resultsTitle;
   @FXML private Label resultCount;


   private MetadataDao dao;
  
 
   public static class MetadataRow {
       private final SimpleStringProperty schema;
       private final SimpleStringProperty table;
       private final SimpleStringProperty name;
       private final SimpleStringProperty type;
       private final SimpleStringProperty extra;
      
       public MetadataRow(String schema, String table, String name, String type, String extra) {
           this.schema = new SimpleStringProperty(schema != null ? schema : "");
           this.table = new SimpleStringProperty(table != null ? table : "");
           this.name = new SimpleStringProperty(name != null ? name : "");
           this.type = new SimpleStringProperty(type != null ? type : "");
           this.extra = new SimpleStringProperty(extra != null ? extra : "");
       }
      
      
       public String getSchema() { return schema.get(); }
       public String getTable() { return table.get(); }
       public String getName() { return name.get(); }
       public String getType() { return type.get(); }
       public String getExtra() { return extra.get(); }
   }


   @FXML
   private void initialize() {
       try {
           dao = new MetadataDao(new ConnectionHandler());
           setupTableColumns();
       } catch (IOException e) {
           renderError("Config error: " + e.getMessage());
       }
   }
  
   private void setupTableColumns() {
     
       colSchema.setCellValueFactory(cellData -> cellData.getValue().schema);
       colTable.setCellValueFactory(cellData -> cellData.getValue().table);
       colName.setCellValueFactory(cellData -> cellData.getValue().name);
       colType.setCellValueFactory(cellData -> cellData.getValue().type);
       colExtra.setCellValueFactory(cellData -> cellData.getValue().extra);
      
      
       metadataTable.setRowFactory(tv -> {
           TableRow<MetadataRow> row = new TableRow<>();
           row.setStyle("-fx-border-color: transparent; -fx-border-width: 0 0 1 0; -fx-border-style: solid; -fx-border-color: #f1f3f4;");
           return row;
       });
   }


  
   @FXML private void onShowAllColumns() {
       if (!ensureDao()) return;
       try {
           var rows = dao.getAllColumnNames();
           List<MetadataRow> tableData = new ArrayList<>();
          
           for (var r : rows) {
               tableData.add(new MetadataRow(
                   r.schemaName,
                   r.tableName,
                   r.columnName,
                   r.dataType,
                   ""
               ));
           }
          
           renderTable("All Database Columns", tableData);
       } catch (SQLException e) {
           renderError("SQL: " + e.getMessage());
       }
   }


   @FXML private void onShowPrimaryKeys() {
       if (!ensureDao()) return;
       try {
           var rows = dao.getPrimaryKeyConstraints();
           List<MetadataRow> tableData = new ArrayList<>();
          
           for (var r : rows) {
               tableData.add(new MetadataRow(
                   r.schemaName,
                   r.tableName,
                   r.constraintName,
                   "PRIMARY KEY",
                   ""
               ));
           }
          
           renderTable("Primary Key Constraints", tableData);
       } catch (SQLException e) {
           renderError("SQL: " + e.getMessage());
       }
   }


   @FXML private void onShowCheckConstraints() {
       if (!ensureDao()) return;
       try {
           var rows = dao.getCheckConstraints();
           List<MetadataRow> tableData = new ArrayList<>();
          
           for (var r : rows) {
               String definition = r.definition == null ? "" : r.definition.replaceAll("\\s+", " ").trim();
               if (definition.length() > 100) {
                   definition = definition.substring(0, 97) + "...";
               }
              
               tableData.add(new MetadataRow(
                   r.schemaName,
                   r.tableName,
                   r.constraintName,
                   "CHECK",
                   definition
               ));
           }
          
           renderTable("Check Constraints", tableData);
       } catch (SQLException e) {
           renderError("SQL: " + e.getMessage());
       }
   }


   @FXML private void onShowConsultantNonInt() {
       if (!ensureDao()) return;
       try {
           var res = dao.getNonIntegerColumnsForConsultantOrEmployee();
          
           if (res.tableName == null) {
               List<MetadataRow> tableData = new ArrayList<>();
               tableData.add(new MetadataRow(
                   "",
                   "Not Found",
                   "No Consultant/Employee table found",
                   "ERROR",
                   "Check database structure"
               ));
               renderTable("Non-INT columns: Consultant/Employee", tableData);
           } else {
               List<MetadataRow> tableData = new ArrayList<>();
              
               if (res.columns.isEmpty()) {
                   tableData.add(new MetadataRow(
                       res.schemaName,
                       res.tableName,
                       "No non-INT columns",
                       "INFO",
                       "All columns are INTEGER type"
                   ));
               } else {
                   for (String column : res.columns) {
                       tableData.add(new MetadataRow(
                           res.schemaName,
                           res.tableName,
                           column,
                           "NON-INTEGER",
                           ""
                       ));
                   }
               }
              
               String fullName = (res.schemaName != null ? res.schemaName + "." : "") + res.tableName;
               renderTable("Non-INT columns in " + fullName, tableData);
           }
       } catch (SQLException e) {
           renderError("SQL: " + e.getMessage());
       }
   }


   @FXML private void onShowLargestTable() {
       if (!ensureDao()) return;
       try {
           var t = dao.getLargestTableByRowCount();
          
           List<MetadataRow> tableData = new ArrayList<>();
           tableData.add(new MetadataRow(
               t.schemaName,
               t.tableName,
               "Row Count",
               "TABLE STATISTICS",
               String.format("%,d rows", t.rowCount)
           ));
          
           renderTable("Largest Table by Row Count", tableData);
       } catch (SQLException e) {
           renderError("SQL: " + e.getMessage());
       }
   }


   @FXML private void onRefreshAll() {
       if (!ensureDao()) return;
       try {
          
           List<MetadataRow> summaryData = new ArrayList<>();
          
          
           var columns = dao.getAllColumnNames();
           summaryData.add(new MetadataRow("", "SUMMARY", "Total Columns", "COUNT",
               String.valueOf(columns.size())));
          
          
           var primaryKeys = dao.getPrimaryKeyConstraints();
           summaryData.add(new MetadataRow("", "SUMMARY", "Primary Key Constraints", "COUNT",
               String.valueOf(primaryKeys.size())));
          
        
           var checkConstraints = dao.getCheckConstraints();
           summaryData.add(new MetadataRow("", "SUMMARY", "Check Constraints", "COUNT",
               String.valueOf(checkConstraints.size())));
          
          
           var largestTable = dao.getLargestTableByRowCount();
           summaryData.add(new MetadataRow(largestTable.schemaName, largestTable.tableName,
               "Largest Table", "ROW COUNT", String.format("%,d rows", largestTable.rowCount)));
          
          
           var nonIntResult = dao.getNonIntegerColumnsForConsultantOrEmployee();
           if (nonIntResult.tableName != null) {
               summaryData.add(new MetadataRow(nonIntResult.schemaName, nonIntResult.tableName,
                   "Non-INT Columns", "COUNT", String.valueOf(nonIntResult.columns.size())));
           } else {
               summaryData.add(new MetadataRow("", "WARNING", "No Consultant/Employee table found", "INFO",
                   "Check database structure"));
           }
          
           renderTable("Database Metadata Summary", summaryData);
       } catch (SQLException e) {
           renderError("SQL: " + e.getMessage());
       }
   }


  
   private void renderTable(String title, List<MetadataRow> data) {
       resultsTitle.setText(title);
       resultCount.setText(data.size() + " items");
      
       metadataTable.setVisible(true);
       metadataTable.setManaged(true);
       metadataTable.setItems(FXCollections.observableArrayList(data));
       metadataTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
   }
  


  
   private void renderError(String msg) {
       resultsTitle.setText("Error");
       resultCount.setText("");
      
       List<MetadataRow> errorData = new ArrayList<>();
       errorData.add(new MetadataRow("", "ERROR", "Could not load metadata", "ERROR", msg));
       errorData.add(new MetadataRow("", "HELP", "Solution", "INFO", "Please check your database connection and configuration."));
      
       metadataTable.setItems(FXCollections.observableArrayList(errorData));
       metadataTable.setVisible(true);
       metadataTable.setManaged(true);
   }
  
   private boolean ensureDao() {
       if (dao != null) return true;
       renderError("DAO not initialized (config missing?)");
       return false;
   }
}
