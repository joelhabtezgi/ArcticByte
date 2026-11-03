package se.lu.ics.Models;


    public class Work {
        private int projectID;   // PK, FK
        private int employeeID;  // PK, FK
        private int hours;
    
        public Work(int projectID, int employeeID, int hours) {
            this.projectID = projectID;
            this.employeeID = employeeID;
            this.hours = hours;
        }
    
        // Getters & Setters
        public int getProjectID() { return projectID; }
        public void setProjectID(int projectID) { this.projectID = projectID; }
    
        public int getEmployeeID() { return employeeID; }
        public void setEmployeeID(int employeeID) { this.employeeID = employeeID; }
    
        public int getHours() { return hours; }
        public void setHours(int hours) { this.hours = hours; }
    }
    

