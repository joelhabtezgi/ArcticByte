package se.lu.ics.Data;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;


public class MetadataDao {
   private final ConnectionHandler connectionHandler;
  
   public MetadataDao(ConnectionHandler connectionHandler) {
       this.connectionHandler = connectionHandler;
   }
  


   public List<ColumnMeta> getAllColumnNames() throws SQLException {
       String sql = """
           SELECT SCHEMA_NAME(t.schema_id) AS schema_name,
                  t.name  AS table_name,
                  c.name  AS column_name,
                  ty.name AS data_type
           FROM sys.tables t
           JOIN sys.columns c ON c.object_id = t.object_id
           JOIN sys.types   ty ON ty.user_type_id = c.user_type_id
           ORDER BY schema_name, table_name, c.column_id
       """;
       try (Connection con = connectionHandler.getConnection();
            PreparedStatement ps = con.prepareStatement(sql);
            ResultSet rs = ps.executeQuery()) {
           List<ColumnMeta> list = new ArrayList<>();
           while (rs.next()) {
               list.add(new ColumnMeta(
                   rs.getString("schema_name"),
                   rs.getString("table_name"),
                   rs.getString("column_name"),
                   rs.getString("data_type")
               ));
           }
           return list;
       } catch (SQLException e) {
           throw new DaoException("Error fetching column metadata", e);
       }
   }


   public List<ConstraintMeta> getPrimaryKeyConstraints() throws SQLException {
       String sql = """
           SELECT SCHEMA_NAME(t.schema_id) AS schema_name,
                  t.name  AS table_name,
                  kc.name AS constraint_name
           FROM sys.key_constraints kc
           JOIN sys.tables t ON t.object_id = kc.parent_object_id
           WHERE kc.type = 'PK'
           ORDER BY schema_name, table_name, kc.name
       """;
       try (Connection con = connectionHandler.getConnection();
            PreparedStatement ps = con.prepareStatement(sql);
            ResultSet rs = ps.executeQuery()) {
           List<ConstraintMeta> list = new ArrayList<>();
           while (rs.next()) {
               list.add(new ConstraintMeta(
                   rs.getString("constraint_name"),
                   rs.getString("schema_name"),
                   rs.getString("table_name"),
                   null
               ));
           }
           return list;
       } catch (SQLException e) {
           throw new DaoException("Error fetching primary key constraints", e);
       }
   }


   public List<ConstraintMeta> getCheckConstraints() throws SQLException {
       String sql = """
           SELECT SCHEMA_NAME(t.schema_id) AS schema_name,
                  t.name        AS table_name,
                  cc.name       AS constraint_name,
                  cc.definition AS definition
           FROM sys.check_constraints cc
           JOIN sys.tables t ON t.object_id = cc.parent_object_id
           ORDER BY schema_name, table_name, cc.name
       """;
       try (Connection con = connectionHandler.getConnection();
            PreparedStatement ps = con.prepareStatement(sql);
            ResultSet rs = ps.executeQuery()) {
           List<ConstraintMeta> list = new ArrayList<>();
           while (rs.next()) {
               list.add(new ConstraintMeta(
                   rs.getString("constraint_name"),
                   rs.getString("schema_name"),
                   rs.getString("table_name"),
                   rs.getString("definition")
               ));
           }
           return list;
       } catch (SQLException e) {
           throw new DaoException("Error fetching check constraints", e);
       }
   }


   public NonIntResult getNonIntegerColumnsForConsultantOrEmployee() throws SQLException {
       String sql = """
           SELECT TOP 1 SCHEMA_NAME(t.schema_id) AS schema_name, t.name AS table_name
           FROM sys.tables t
           WHERE t.name IN ('Consultant','Employee')
           ORDER BY CASE WHEN t.name='Consultant' THEN 0 ELSE 1 END, t.name
       """;
      
       try (Connection con = connectionHandler.getConnection();
            PreparedStatement ps = con.prepareStatement(sql);
            ResultSet rs = ps.executeQuery()) {
          
           if (!rs.next()) {
               return new NonIntResult(null, null, List.of());
           }
          
           String schemaName = rs.getString("schema_name");
           String tableName = rs.getString("table_name");
          
           // Now get non-INT columns for this table
           String columnSql = """
               SELECT c.name AS column_name
               FROM sys.tables t
               JOIN sys.columns c ON c.object_id = t.object_id
               JOIN sys.types   ty ON ty.user_type_id = c.user_type_id
               WHERE t.name = ? AND SCHEMA_NAME(t.schema_id) = ?
                 AND ty.name <> 'int'
               ORDER BY c.column_id
           """;
          
           try (PreparedStatement colPs = con.prepareStatement(columnSql)) {
               colPs.setString(1, tableName);
               colPs.setString(2, schemaName);
               try (ResultSet colRs = colPs.executeQuery()) {
                   List<String> cols = new ArrayList<>();
                   while (colRs.next()) {
                       cols.add(colRs.getString("column_name"));
                   }
                   return new NonIntResult(schemaName, tableName, cols);
               }
           }
       } catch (SQLException e) {
           throw new DaoException("Error fetching non-integer columns for Consultant or Employee", e);
       }
   }


   public TableRowCount getLargestTableByRowCount() throws SQLException {
       String sql = """
           SELECT TOP 1 SCHEMA_NAME(t.schema_id) AS schema_name,
                        t.name AS table_name,
                        SUM(p.rows) AS row_count
           FROM sys.tables t
           JOIN sys.partitions p ON p.object_id = t.object_id
           WHERE p.index_id IN (0,1)
           GROUP BY t.schema_id, t.name
           ORDER BY row_count DESC
       """;
       try (Connection con = connectionHandler.getConnection();
            PreparedStatement ps = con.prepareStatement(sql);
            ResultSet rs = ps.executeQuery()) {
           if (rs.next()) {
               return new TableRowCount(
                   rs.getString("schema_name"),
                   rs.getString("table_name"),
                   rs.getLong("row_count")
               );
           }
           return new TableRowCount(null, "-", 0L);
       } catch (SQLException e) {
           throw new DaoException("Error fetching largest table by row count", e);
       }
   }


   public static class ColumnMeta {
       public final String schemaName, tableName, columnName, dataType;
       public ColumnMeta(String s, String t, String c, String d) {
           this.schemaName = s; this.tableName = t; this.columnName = c; this.dataType = d;
       }
   }
  
   public static class ConstraintMeta {
       public final String constraintName, schemaName, tableName, definition;
       public ConstraintMeta(String n, String s, String t, String d){
           this.constraintName = n; this.schemaName = s; this.tableName = t; this.definition = d;
       }
   }
  
   public static class TableRowCount {
       public final String schemaName, tableName;
       public final long rowCount;
       public TableRowCount(String s, String t, long r){
           this.schemaName = s; this.tableName = t; this.rowCount = r;
       }
   }
  
   public static class NonIntResult {
       public final String schemaName, tableName;
       public final List<String> columns;
       public NonIntResult(String schemaName, String tableName, List<String> columns){
           this.schemaName = schemaName; this.tableName = tableName; this.columns = columns;
       }
   }
}
