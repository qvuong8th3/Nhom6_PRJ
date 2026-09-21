package com.aita.gitanalytics.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class GitCommitDAO {

    /**
     * Thêm một commit mới vào cơ sở dữ liệu (Sử dụng executeUpdate)
     */
    public boolean insertCommit(String commitHash, String message, String authorEmail, int linesAdded, int linesDeleted) {
        String sql = "INSERT INTO Commits (commit_hash, message, author_email, lines_added, lines_deleted, committed_at) VALUES (?, ?, ?, ?, ?, GETDATE())";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, commitHash);
            ps.setString(2, message);
            ps.setString(3, authorEmail);
            ps.setInt(4, linesAdded);
            ps.setInt(5, linesDeleted);
            
            int rowsAffected = ps.executeUpdate();
            return rowsAffected > 0;
            
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Lấy tổng số commit của một tác giả (Sử dụng executeQuery)
     */
    public int getTotalCommitsByAuthor(String authorEmail) {
        String sql = "SELECT COUNT(*) AS total_commits FROM Commits WHERE author_email = ?";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, authorEmail);
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("total_commits");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }
}
