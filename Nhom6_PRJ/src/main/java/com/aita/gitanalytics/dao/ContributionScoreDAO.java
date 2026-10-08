package com.aita.gitanalytics.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class ContributionScoreDAO {

    /**
     * Cập nhật điểm đóng góp của sinh viên (Sử dụng executeUpdate)
     */
    public boolean updateContributionScore(int userId, int groupId, double newScore) {
        String sql = "UPDATE Contribution_Scores SET final_contribution_percentage = ?, updated_at = GETDATE() WHERE user_id = ? AND group_id = ?";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setDouble(1, newScore);
            ps.setInt(2, userId);
            ps.setInt(3, groupId);
            
            int rowsAffected = ps.executeUpdate();
            return rowsAffected > 0;
            
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Lấy điểm đóng góp của một sinh viên (Sử dụng executeQuery)
     */
    public double getContributionScore(int userId, int groupId) {
        String sql = "SELECT final_contribution_percentage FROM Contribution_Scores WHERE user_id = ? AND group_id = ?";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, userId);
            ps.setInt(2, groupId);
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble("final_contribution_percentage");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1.0;
    }
}
