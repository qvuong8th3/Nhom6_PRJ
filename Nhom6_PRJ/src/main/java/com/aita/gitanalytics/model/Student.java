package com.aita.gitanalytics.model;

public class Student {

    private final int userId;
    private final String username;
    private final String fullName;
    private final String email;
    private final String githubUsername;

    public Student(int userId, String username, String fullName, String email, String githubUsername) {
        this.userId = userId;
        this.username = username;
        this.fullName = fullName;
        this.email = email;
        this.githubUsername = githubUsername;
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

    public String getGithubUsername() {
        return githubUsername;
    }
}