package se.lu.ics.Models;

public class Title {
    private int titleID;
    private String titleName;

    public Title(int titleID, String titleName) {
        this.titleID = titleID;
        this.titleName = titleName;
    }

    // Getters & Setters
    public int getTitleID() { return titleID; }
    public void setTitleID(int titleID) { this.titleID = titleID; }

    public String getTitleName() { return titleName; }
    public void setTitleName(String titleName) { this.titleName = titleName; }
}
