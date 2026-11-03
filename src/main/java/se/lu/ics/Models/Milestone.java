package se.lu.ics.Models;

public class Milestone {
    private int milestoneID;
    private int milestoneNo;  // Changed from String to int since database expects int
    private String milestoneName;
    private String date;   
    private String status;
    private int projectID; // FK

    public Milestone(int milestoneID, String milestoneName, String date, String status, int projectID) {
        this.milestoneID = milestoneID;
        this.milestoneName = milestoneName;
        this.date = date;
        this.status = status;
        this.projectID = projectID;
    }

    // Constructor with milestoneNo
    public Milestone(int milestoneID, int milestoneNo, String milestoneName, String date, String status, int projectID) {
        this.milestoneID = milestoneID;
        this.milestoneNo = milestoneNo;
        this.milestoneName = milestoneName;
        this.date = date;
        this.status = status;
        this.projectID = projectID;
    }

    // Getters & Setters
    public int getMilestoneID() { return milestoneID; }
    public void setMilestoneID(int milestoneID) { this.milestoneID = milestoneID; }

    public int getMilestoneNo() { return milestoneNo; }
    public void setMilestoneNo(int milestoneNo) { this.milestoneNo = milestoneNo; }

    public String getMilestoneName() { return milestoneName; }
    public void setMilestoneName(String milestoneName) { this.milestoneName = milestoneName; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getProjectID() { return projectID; }
    public void setProjectID(int projectID) { this.projectID = projectID; }
}
