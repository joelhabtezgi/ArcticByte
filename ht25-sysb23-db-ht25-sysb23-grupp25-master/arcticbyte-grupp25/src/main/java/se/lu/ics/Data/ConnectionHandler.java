package se.lu.ics.Data;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public class ConnectionHandler {
    private String connectionURL;
    private final String propertiedFilePath = "/config/config.properties";

    public ConnectionHandler() throws IOException {
        Properties connectionProperties = new Properties();
        try (InputStream inputStream = getClass().getResourceAsStream(propertiedFilePath)) {
            if (inputStream != null) {
                connectionProperties.load(inputStream);
            } else {
                throw new IOException("Config file 'config.properties' not found in classpath");
            }
        }

        String databaseServerName = connectionProperties.getProperty("database.server.name");
        String databaseServerPort = connectionProperties.getProperty("database.server.port");
        String databaseName = connectionProperties.getProperty("database.name");
        String databaseUserName = connectionProperties.getProperty("database.user.name");
        String databaseUserPassword = connectionProperties.getProperty("database.user.password");

        connectionURL = "jdbc:sqlserver://"
                + databaseServerName + ":"
                + databaseServerPort + ";"
                + "database=" + databaseName + ";"
                + "user=" + databaseUserName + ";"
                + "password=" + databaseUserPassword + ";"
                + "encrypt=true;"
                + "trustServerCertificate=true;";
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(connectionURL);
    }
}