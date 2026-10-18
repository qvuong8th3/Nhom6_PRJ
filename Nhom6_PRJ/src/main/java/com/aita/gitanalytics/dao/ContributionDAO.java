package com.aita.gitanalytics.dao;

import com.aita.gitanalytics.service.GitScoreCalculatorService.AssessorWeights;
import com.aita.gitanalytics.service.GitScoreCalculatorService.StudentGitData;

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
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ContributionDAO {

    // Lớp DTO đầy đủ thông tin đóng góp của sinh viên
    public static class StudentContribution {
        public int userId;
        public String studentName;
        public int commits;
        public int loc;
        public int regularity;
        public int pullRequests;
        public int reviews;
        public double penaltyScore;
        public List<String> penaltyReasons = new ArrayList<>();
        public boolean isFreeRiderSuspect;
        public double percentage;
        public double suggestedPercentage;
        public double volumeScore;
        public double frequencyScore;
        public double reviewScore;

        public StudentContribution(int userId, String studentName, int commits, int loc, int regularity,
                                   int pullRequests, int reviews, double penaltyScore, double percentage) {
            this.userId = userId;
            this.studentName = studentName;
            this.commits = commits;
            this.loc = loc;
            this.regularity = regularity;
            this.pullRequests = pullRequests;
            this.reviews = reviews;
            this.penaltyScore = penaltyScore;
            this.percentage = percentage;
            this.suggestedPercentage = percentage;
        }

        public StudentContribution(String studentName, int commits, int loc, int regularity, double percentage) {
            this(0, studentName, commits, loc, regularity, 0, 0, 0, percentage);
        }
    }

    /**
     * Lấy danh sách đóng góp từ dữ liệu commits, PRs, reviews thực tế trong DB
     */
    public Map<Integer, StudentGitData> getGroupGitRawData(int groupId) {
        Map<Integer, StudentGitData> dataMap = new LinkedHashMap<>();

        // 1. Lấy danh sách thành viên trong nhóm
        String membersSql = "SELECT u.user_id, u.full_name "
                + "FROM Group_Members gm "
                + "JOIN Users u ON gm.user_id = u.user_id "
                + "WHERE gm.group_id = ? "
                + "ORDER BY gm.user_id";

        // 2. Lấy dữ liệu commits
        String commitsSql = "SELECT c.user_id, c.commit_message, c.lines_added, c.lines_deleted, c.commit_date "
                + "FROM commit_logs c "
                + "JOIN git_repositories r ON c.repo_id = r.repo_id "
                + "JOIN projects p ON r.project_id = p.project_id "
                + "WHERE p.group_id = ? "
                + "ORDER BY c.user_id, c.commit_date ASC";

        // 3. Lấy dữ liệu PRs
        String prSql = "SELECT user_id, COUNT(*) AS pr_count FROM Pull_Requests WHERE group_id = ? GROUP BY user_id";

        // 4. Lấy dữ liệu Reviews
        String reviewSql = "SELECT reviewer_id, COUNT(*) AS review_count FROM Code_Reviews WHERE group_id = ? GROUP BY reviewer_id";

        try (Connection conn = DBContext.getConnection()) {
            if (conn == null) return dataMap;

            Map<Integer, String> names = new LinkedHashMap<>();
            try (PreparedStatement ps = conn.prepareStatement(membersSql)) {
                ps.setInt(1, groupId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        names.put(rs.getInt("user_id"), rs.getString("full_name"));
                    }
                }
            }

            // Nếu nhóm không có trong Group_Members, lấy từ users đã có commit
            if (names.isEmpty()) {
                String fallbackSql = "SELECT DISTINCT u.user_id, u.full_name FROM commit_logs c "
                        + "JOIN git_repositories r ON c.repo_id = r.repo_id "
                        + "JOIN projects p ON r.project_id = p.project_id "
                        + "JOIN users u ON c.user_id = u.user_id WHERE p.group_id = ?";
                try (PreparedStatement ps = conn.prepareStatement(fallbackSql)) {
                    ps.setInt(1, groupId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            names.put(rs.getInt("user_id"), rs.getString("full_name"));
                        }
                    }
                }
            }

            Map<Integer, List<String>> userMessages = new HashMap<>();
            Map<Integer, Integer> userLoc = new HashMap<>();
            Map<Integer, Integer> userCommits = new HashMap<>();
            Map<Integer, List<LocalDate>> userCommitDates = new HashMap<>();

            try (PreparedStatement ps = conn.prepareStatement(commitsSql)) {
                ps.setInt(1, groupId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int uid = rs.getInt("user_id");
                        String msg = rs.getString("commit_message");
                        int added = rs.getInt("lines_added");
                        int deleted = rs.getInt("lines_deleted");
                        Timestamp ts = rs.getTimestamp("commit_date");

                        userMessages.computeIfAbsent(uid, k -> new ArrayList<>()).add(msg);
                        userCommits.put(uid, userCommits.getOrDefault(uid, 0) + 1);
                        userLoc.put(uid, userLoc.getOrDefault(uid, 0) + Math.max(0, added - (deleted / 3)));
                        if (ts != null) {
                            userCommitDates.computeIfAbsent(uid, k -> new ArrayList<>()).add(ts.toLocalDateTime().toLocalDate());
                        }
                    }
                }
            }

            Map<Integer, Integer> userPrs = new HashMap<>();
            try (PreparedStatement ps = conn.prepareStatement(prSql)) {
                ps.setInt(1, groupId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        userPrs.put(rs.getInt("user_id"), rs.getInt("pr_count"));
                    }
                }
            } catch (SQLException ignored) {}

            Map<Integer, Integer> userReviews = new HashMap<>();
            try (PreparedStatement ps = conn.prepareStatement(reviewSql)) {
                ps.setInt(1, groupId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        userReviews.put(rs.getInt("reviewer_id"), rs.getInt("review_count"));
                    }
                }
            } catch (SQLException ignored) {}

            for (Map.Entry<Integer, String> entry : names.entrySet()) {
                int uid = entry.getKey();
                String name = entry.getValue();
                int commits = userCommits.getOrDefault(uid, 0);
                int loc = userLoc.getOrDefault(uid, 0);
                List<String> messages = userMessages.getOrDefault(uid, Collections.emptyList());
                int prs = userPrs.getOrDefault(uid, 0);
                int reviews = userReviews.getOrDefault(uid, 0);

                // Tính điểm độ đều đặn Regularity (dựa trên số ngày khác nhau có commit)
                List<LocalDate> dates = userCommitDates.getOrDefault(uid, Collections.emptyList());
                long distinctDays = dates.stream().distinct().count();
                double regularity = commits == 0 ? 0 : Math.min(100.0, Math.max(10.0, distinctDays * 20.0));

                // Kiểm tra last-minute dump (toàn bộ commit chỉ xảy ra trong 1 ngày duy nhất)
                boolean isLastMinute = commits > 0 && distinctDays == 1 && commits >= 1;

                dataMap.put(uid, new StudentGitData(uid, name, commits, loc, regularity, prs, reviews, messages, isLastMinute));
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return dataMap;
    }

    /**
     * Lấy cấu hình trọng số W1, W2, W3, W4 từ contribution_settings
     */
    public AssessorWeights getGroupWeights(int groupId) {
        String sql = "SELECT commit_weight, loc_weight, regularity_weight, review_weight, penalty_weight "
                + "FROM contribution_settings WHERE group_id = ? OR project_id = 1";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn != null ? conn.prepareStatement(sql) : null) {
            if (ps != null) {
                ps.setInt(1, groupId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        double locW = rs.getDouble("loc_weight");
                        double regW = rs.getDouble("regularity_weight");
                        double revW = rs.getDouble("review_weight");
                        double penW = rs.getDouble("penalty_weight");
                        return new AssessorWeights(
                                locW > 0 ? locW : 0.35,
                                regW > 0 ? regW : 0.25,
                                revW > 0 ? revW : 0.25,
                                penW > 0 ? penW : 0.15
                        );
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return new AssessorWeights();
    }

    /**
     * Cập nhật cấu hình trọng số cho nhóm
     */
    public boolean updateGroupWeights(int groupId, double volumeWeight, double frequencyWeight, double reviewWeight, double penaltyWeight) {
        String checkSql = "SELECT COUNT(*) FROM contribution_settings WHERE group_id = ?";
        String updateSql = "UPDATE contribution_settings SET loc_weight = ?, regularity_weight = ?, review_weight = ?, penalty_weight = ? WHERE group_id = ?";
        String insertSql = "INSERT INTO contribution_settings (project_id, group_id, loc_weight, regularity_weight, review_weight, penalty_weight) VALUES (1, ?, ?, ?, ?, ?)";

        try (Connection conn = DBContext.getConnection()) {
            if (conn == null) return false;
            boolean exists = false;
            try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
                ps.setInt(1, groupId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) exists = rs.getInt(1) > 0;
                }
            }

            if (exists) {
                try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                    ps.setDouble(1, volumeWeight);
                    ps.setDouble(2, frequencyWeight);
                    ps.setDouble(3, reviewWeight);
                    ps.setDouble(4, penaltyWeight);
                    ps.setInt(5, groupId);
                    return ps.executeUpdate() > 0;
                }
            } else {
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    ps.setInt(1, groupId);
                    ps.setDouble(2, volumeWeight);
                    ps.setDouble(3, frequencyWeight);
                    ps.setDouble(4, reviewWeight);
                    ps.setDouble(5, penaltyWeight);
                    return ps.executeUpdate() > 0;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
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
                + "FROM commit_logs c "
                + "JOIN git_repositories r ON c.repo_id = r.repo_id "
                + "JOIN projects p ON r.project_id = p.project_id "
                + "JOIN users u ON c.user_id = u.user_id "
                + "WHERE p.group_id = ? AND c.commit_date >= ? AND c.commit_date < ? "
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
        } catch (SQLException e) {
            e.printStackTrace();
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
                + "FROM groups g "
                + "LEFT JOIN projects p ON g.group_id = p.group_id "
                + "LEFT JOIN git_repositories r ON p.project_id = r.project_id "
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
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return metadata;
    }

    /**
     * Cập nhật URL GitHub repository cho nhóm
     */
    public boolean updateRepoUrl(int groupId, String newRepoUrl) {
        String trimmed = newRepoUrl != null ? newRepoUrl.trim() : "";
        String updateSql = "UPDATE r SET repo_url = ?, last_synced_at = GETDATE() "
                + "FROM git_repositories r "
                + "JOIN projects p ON r.project_id = p.project_id "
                + "WHERE p.group_id = ?";
        try (Connection conn = DBContext.getConnection()) {
            if (conn == null) return false;

            // 1. Dọn dẹp commit_logs và Pull_Requests cũ của repo trước khi đồng bộ repo mới
            try (PreparedStatement psClearCommits = conn.prepareStatement(
                    "DELETE c FROM commit_logs c JOIN git_repositories r ON c.repo_id = r.repo_id JOIN projects p ON r.project_id = p.project_id WHERE p.group_id = ?");
                 PreparedStatement psClearPRs = conn.prepareStatement(
                    "DELETE FROM Pull_Requests WHERE group_id = ?")) {
                psClearCommits.setInt(1, groupId);
                psClearCommits.executeUpdate();
                psClearPRs.setInt(1, groupId);
                psClearPRs.executeUpdate();
            }

            // 2. Cập nhật repo_url
            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                ps.setString(1, trimmed);
                ps.setInt(2, groupId);
                int rows = ps.executeUpdate();
                if (rows > 0) return true;
            }

            // 3. Nếu chưa có bản ghi git_repositories, thêm mới
            String insertSql = "INSERT INTO git_repositories (project_id, repo_url, last_synced_at) "
                    + "SELECT project_id, ?, GETDATE() FROM projects WHERE group_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setString(1, trimmed);
                ps.setInt(2, groupId);
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
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
