# ArcticByte

A JavaFX desktop application for consultant management, project tracking and time reporting.

Built with fellow students and working closely together for a couple of weeks.

## Features

- Manage consultant and employee information.
- Organise projects and consultant assignments.
- Record and review worked hours.
- View consultant and project overviews.
- Store and retrieve information through a SQL Server database.

## Technologies

- Java 21
- JavaFX 21 and FXML
- Maven
- Microsoft SQL Server and JDBC
- Microsoft Azure virtual machine

## Running locally

The application requires Java 21, Maven and access to a SQL Server database with the expected project schema.

Database connection settings are loaded from:

`src/main/resources/config/config.properties`

This configuration file is not included in the repository and must be provided locally before running the application.

After configuring the database connection, launch the application from the project root:

```bash
mvn javafx:run
```

## Documentation

Project requirements are available in the `Docs` directory.
