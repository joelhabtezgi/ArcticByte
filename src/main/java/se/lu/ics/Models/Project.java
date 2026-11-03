  package se.lu.ics.Models;

    public class Project {
        private int projectID;
        private int projectNo;
        private String projectName;
        private String startDate; 
        private String endDate;   

        public Project(int projectID, int projectNo, String projectName, String startDate, String endDate) {
            this.projectID = projectID;
            this.projectNo = projectNo;
            this.projectName = projectName;
            this.startDate = startDate;
            this.endDate = endDate;
        }

        // Getters & Setters
        public int getProjectID() { return projectID; }
        public void setProjectID(int projectID) { this.projectID = projectID; }

        public int getProjectNo() { return projectNo; }
        public void setProjectNo(int projectNo) { this.projectNo = projectNo; }

        public String getProjectName() { return projectName; }
        public void setProjectName(String projectName) { this.projectName = projectName; }

        public String getStartDate() { return startDate; }
        public void setStartDate(String startDate) { this.startDate = startDate; }

        public String getEndDate() { return endDate; }
        public void setEndDate(String endDate) { this.endDate = endDate; }
        
        @Override
        public String toString(){
            return "Project " + projectNo + " - " + projectName;
        }
    }
