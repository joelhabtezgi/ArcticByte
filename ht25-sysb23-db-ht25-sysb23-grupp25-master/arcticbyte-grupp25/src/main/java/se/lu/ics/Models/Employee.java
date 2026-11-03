    
package se.lu.ics.Models;



public class Employee {
    private String employeeNo;
    private int employeeID;
    private String firstName;
    private String lastName;
    private int titleID; // FK
    private String titleName; // New field for joined title name
    private double salary;
    private String projectNames; // kommaseparerad lista av projektnamn

    // Konstruktor för befintlig anställd (från databas)
    public Employee(int employeeID, String firstName, String lastName, int titleID, String titleName, double salary, String projectNames, String employeeNo) {
        this.employeeID = employeeID;
        this.employeeNo = employeeNo;
        this.firstName = firstName;
        this.lastName = lastName;
        this.titleID = titleID;
        this.titleName = titleName;
        this.salary = salary;
        this.projectNames = projectNames;
    }

    // Konstruktor för ny anställd (utan id)
    public Employee(String firstName, String lastName, int titleID, double salary, String employeeNo) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.titleID = titleID;
        this.salary = salary;
        this.employeeNo = employeeNo;
    }
    public String getEmployeeNo() { return employeeNo; }
    public void setEmployeeNo(String employeeNo) { this.employeeNo = employeeNo; }

    // Getters & Setters
    public int getEmployeeID() { return employeeID; }
    public void setEmployeeID(int employeeID) { this.employeeID = employeeID; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public int getTitleID() { return titleID; }
    public void setTitleID(int titleID) { this.titleID = titleID; }

    public String getTitleName() { return titleName; }
    public void setTitleName(String titleName) { this.titleName = titleName; }

    public double getSalary() { return salary; }
    public void setSalary(double salary) { this.salary = salary; }

    public String getProjectNames() { return projectNames; }
    public void setProjectNames(String projectNames) { this.projectNames = projectNames; }
}

