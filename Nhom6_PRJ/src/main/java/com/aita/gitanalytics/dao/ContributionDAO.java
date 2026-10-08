package com.aita.gitanalytics.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

        try (Connection conn = DBContext.getConnection();
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
        
        return list;
    }

    public Map<String, Object> getGroupTimeline(int groupId) {
        LocalDate currentWeek = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate start = currentWeek.minusWeeks(4);
        LocalDate end = currentWeek.plusWeeks(1);
        List<String> labels = new ArrayList<>();
        for (int week = 0; week < 5; week++) {
            labels.add("Tuần " + (week + 1) + " (" + start.plusWeeks(week).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM")) + ")");
        }

        Map<String, int[]> commitsByStudent = new LinkedHashMap<>();
        String sql = "SELECT u.full_name, c.commit_date "
                + "FROM Commits c "
                + "JOIN Git_Repositories r ON c.repo_id = r.repo_id "
                + "JOIN Users u ON c.user_id = u.user_id "
                + "WHERE r.group_id = ? AND c.commit_date >= ? AND c.commit_date < ? "
                + "ORDER BY u.full_name, c.commit_date";

        try (Connection conn = DBContext.getConnection()) {
            if (conn != null) {
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, groupId);
                    ps.setTimestamp(2, Timestamp.valueOf(start.atStartOfDay()));
                    ps.setTimestamp(3, Timestamp.valueOf(end.atStartOfDay()));
                    try (ResultSet rs = ps.executeQuery()) {
                        collectTimelineRows(rs, start, commitsByStudent);
                    }
                }
            }
        } catch (SQLException modernSchemaError) {
            String legacySql = "SELECT u.full_name, c.commit_date "
                    + "FROM commit_logs c "
                    + "JOIN git_repositories r ON c.repo_id = r.repo_id "
                    + "JOIN projects p ON r.project_id = p.project_id "
                    + "JOIN users u ON c.user_id = u.user_id "
                    + "WHERE p.group_id = ? AND c.commit_date >= ? AND c.commit_date < ? "
                    + "ORDER BY u.full_name, c.commit_date";
            try (Connection conn = DBContext.getConnection()) {
                if (conn != null) {
                    try (PreparedStatement ps = conn.prepareStatement(legacySql)) {
                        ps.setInt(1, groupId);
                        ps.setTimestamp(2, Timestamp.valueOf(start.atStartOfDay()));
                        ps.setTimestamp(3, Timestamp.valueOf(end.atStartOfDay()));
                        try (ResultSet rs = ps.executeQuery()) {
                            collectTimelineRows(rs, start, commitsByStudent);
                        }
                    }
                }
            } catch (SQLException legacySchemaError) {
                legacySchemaError.addSuppressed(modernSchemaError);
                legacySchemaError.printStackTrace();
            }
        }

        String[] colors = {"#3b82f6", "#10b981", "#f59e0b", "#ef4444", "#8b5cf6", "#06b6d4"};
        List<Map<String, Object>> datasets = new ArrayList<>();
        int colorIndex = 0;
        for (Map.Entry<String, int[]> entry : commitsByStudent.entrySet()) {
            Map<String, Object> dataset = new LinkedHashMap<>();
            String color = colors[colorIndex++ % colors.length];
            dataset.put("label", entry.getKey());
            dataset.put("data", entry.getValue());
            dataset.put("borderColor", color);
            dataset.put("backgroundColor", color);
            dataset.put("tension", 0.3);
            datasets.add(dataset);
        }

        Map<String, Object> timeline = new LinkedHashMap<>();
        timeline.put("labels", labels);
        timeline.put("datasets", datasets);
        return timeline;
    }

    public Map<String, Object> getGroupMetadata(int groupId) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        String sql = "SELECT g.group_name, r.repo_url "
                + "FROM Project_Groups g "
                + "LEFT JOIN Git_Repositories r ON g.group_id = r.group_id "
                + "WHERE g.group_id = ?";
        try (Connection conn = DBContext.getConnection()) {
            if (conn != null) {
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, groupId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            metadata.put("groupName", rs.getString("group_name"));
                            metadata.put("repoUrl", rs.getString("repo_url"));
                        }
                    }
                }
            }
        } catch (SQLException modernSchemaError) {
            String legacySql = "SELECT g.group_name, r.repo_url "
                    + "FROM groups g "
                    + "LEFT JOIN projects p ON g.group_id = p.group_id "
                    + "LEFT JOIN git_repositories r ON p.project_id = r.project_id "
                    + "WHERE g.group_id = ?";
            try (Connection conn = DBContext.getConnection()) {
                if (conn != null) {
                    try (PreparedStatement ps = conn.prepareStatement(legacySql)) {
                        ps.setInt(1, groupId);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                metadata.put("groupName", rs.getString("group_name"));
                                metadata.put("repoUrl", rs.getString("repo_url"));
                            }
                        }
                    }
                }
            } catch (SQLException legacySchemaError) {
                legacySchemaError.addSuppressed(modernSchemaError);
                legacySchemaError.printStackTrace();
            }
        }
        return metadata;
    }

    private void collectTimelineRows(ResultSet rs, LocalDate start,
                                     Map<String, int[]> commitsByStudent) throws SQLException {
        while (rs.next()) {
            String name = rs.getString("full_name");
            int[] weeklyCommits = commitsByStudent.computeIfAbsent(name, key -> new int[5]);
            LocalDate commitDate = rs.getTimestamp("commit_date").toLocalDateTime().toLocalDate();
            long weekIndex = ChronoUnit.WEEKS.between(start,
                    commitDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)));
            if (weekIndex >= 0 && weekIndex < weeklyCommits.length) {
                weeklyCommits[(int) weekIndex]++;
            }
        }
    }
}
