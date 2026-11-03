module maven.dbproject{
    exports se.lu.ics;
 
 
    opens se.lu.ics.Controllers to javafx.fxml;
    opens se.lu.ics.Models to javafx.base;
 
 
    requires java.sql;
    requires javafx.controls;
    requires javafx.fxml;
    requires java.desktop;
    requires java.prefs;
    requires transitive javafx.graphics;
 }
 