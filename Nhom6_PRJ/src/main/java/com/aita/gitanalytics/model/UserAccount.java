package com.aita.gitanalytics.model;

public class UserAccount {

    private final int userId;
    private final String username;
    private final String fullName;
    private final String email;
    private final String role;

    public UserAccount(int userId, String username, String fullName, String email, String role) {
        this.userId = userId;
        this.username = username;
        this.fullName = fullName;
        this.email = email;
        this.role = role;
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

    public String getRole() {
        return role;
    }
}