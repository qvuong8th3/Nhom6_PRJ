package com.aita.gitanalytics.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class UserDAO {

    public boolean createUser(String fullName, String email, String githubUsername) {
        if (isBlank(fullName) || isBlank(email)) {
            return false;
        }

        String normalizedEmail = email.trim();
        String normalizedFullName = fullName.trim();
        String normalizedGithub = isBlank(githubUsername) ? null : githubUsername.trim();

        String checkSql = "SELECT 1 FROM users WHERE email = ?";
        String insertSql = "INSERT INTO users (full_name, email, github_username, created_at) " +
                           "VALUES (?, ?, ?, GETDATE())";

        try (Connection conn = DBContext.getConnection()) {
            if (conn == null) {
                System.err.println("❌ Kết nối CSDL thất bại! Kiểm tra lại DBContext.");
                return false;
            }

            // 1. Kiểm tra xem Email đã tồn tại chưa
            try (PreparedStatement checkPs = conn.prepareStatement(checkSql)) {
                checkPs.setString(1, normalizedEmail);
                try (ResultSet rs = checkPs.executeQuery()) {
                    if (rs.next()) {
                        System.err.println("❌ Email đã tồn tại trong CSDL: " + normalizedEmail);
                        return false;
                    }
                }
            }

            // 2. Thêm người dùng mới vào bảng users
            try (PreparedStatement insertPs = conn.prepareStatement(insertSql)) {
                insertPs.setString(1, normalizedFullName);
                insertPs.setString(2, normalizedEmail);
                insertPs.setString(3, normalizedGithub);

                return insertPs.executeUpdate() > 0;
            }

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean insertSampleUser() {
        return createUser(
                "Giảng viên Demo",
                "lecturer01@university.edu.vn",
                "lecturer-demo"
        );
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
