package com.aita.gitanalytics.model;

public class Lecturer {

    private final int userId;
    private final String username;
    private final String fullName;
    private final String email;

    public Lecturer(int userId, String username, String fullName, String email) {
        this.userId = userId;
        this.username = username;
        this.fullName = fullName;
        this.email = email;
    }

    public int getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }
}