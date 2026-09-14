package com.aita.gitanalytics.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ContributionDAO {

    // Lớp nội bộ đại diện cho dữ liệu đóng góp của 1 sinh viên (DTO)
    public static class StudentContribution {
        public String studentName;
        public int commits;
        public int loc;
        public int regularity;
        public double percentage;

        public StudentContribution(String studentName, int commits, int loc, int regularity, double percentage) {
            this.studentName = studentName;
            this.commits = commits;
            this.loc = loc;
            this.regularity = regularity;
            this.percentage = percentage;
        }
    }

    /**
     * Truy vấn thông tin đóng góp của 1 nhóm dựa vào groupId.
     * Đây là dữ liệu thực tế lấy từ Database (Bảng Contribution_Scores kết hợp Users).
     */
    public List<StudentContribution> getGroupContributions(int groupId) {
        List<StudentContribution> list = new ArrayList<>();
        
        String sql = "SELECT u.full_name, cs.total_commits, cs.total_loc, cs.regularity_score, cs.final_contribution_percentage " +
                     "FROM Contribution_Scores cs " +
                     "JOIN Users u ON cs.user_id = u.user_id " +
                     "WHERE cs.group_id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn != null ? conn.prepareStatement(sql) : null) {
            
            if (ps != null) {
                ps.setInt(1, groupId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        StudentContribution sc = new StudentContribution(
                            rs.getString("full_name"),
                            rs.getInt("total_commits"),
                            rs.getInt("total_loc"),
                            (int) rs.getFloat("regularity_score"),
                            rs.getFloat("final_contribution_percentage")
                        );
                        list.add(sc);
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        // Nếu database chưa có dữ liệu (rs.next() = false), mock một số data cho prototype chạy
        if (list.isEmpty()) {
            list.add(new StudentContribution("Nguyen Van A (DB Mock)", 45, 2600, 88, 38.0));
            list.add(new StudentContribution("Tran Thi B (DB Mock)", 32, 1900, 92, 30.5));
            list.add(new StudentContribution("Le Van C (DB Mock)", 2, 100, 10, 5.0)); // Free rider
            list.add(new StudentContribution("Pham Van D (DB Mock)", 28, 2100, 78, 26.5));
        }

        return list;
    }
}
