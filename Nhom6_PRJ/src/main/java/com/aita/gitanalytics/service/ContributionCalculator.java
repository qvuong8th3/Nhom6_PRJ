package com.aita.gitanalytics.service;

import java.util.Map;
import java.util.HashMap;

public class ContributionCalculator {

    // Trọng số đánh giá (Có thể cấu hình từ Database sau này)
    private static final double WEIGHT_COMMITS = 0.3; // 30% dựa trên số lượng commit
    private static final double WEIGHT_LOC = 0.5;     // 50% dựa trên dòng code (Lines of Code)
    private static final double WEIGHT_REGULARITY = 0.2; // 20% dựa trên độ đều đặn

    /**
     * Tính toán phần trăm đóng góp cho một sinh viên.
     * 
     * @param studentCommits Số commit của sinh viên
     * @param totalGroupCommits Tổng số commit của cả nhóm
     * @param studentLoc Số dòng code sinh viên đóng góp (Added - Deleted/Penalty)
     * @param totalGroupLoc Tổng số dòng code của cả nhóm
     * @param regularityScore Điểm đều đặn (Thang điểm 0 - 100) - tính dựa trên phân bố commit theo ngày
     * @return Phần trăm đóng góp (0 - 100%)
     */
    public double calculateContributionPercentage(
            int studentCommits, int totalGroupCommits,
            int studentLoc, int totalGroupLoc,
            double regularityScore) {
        
        // 1. Tính điểm % Commits
        double commitScore = (totalGroupCommits == 0) ? 0 : ((double) studentCommits / totalGroupCommits) * 100;

        // 2. Tính điểm % LOC
        // Lọc "Free-riding": Nếu thêm nhiều dòng trắng/rác thì LOC có thể bị giảm trừ bởi module phân tích NLP trước đó.
        double locScore = (totalGroupLoc == 0) ? 0 : ((double) studentLoc / totalGroupLoc) * 100;

        // 3. Tổng hợp dựa trên trọng số
        double finalPercentage = (commitScore * WEIGHT_COMMITS) 
                               + (locScore * WEIGHT_LOC) 
                               + (regularityScore * WEIGHT_REGULARITY);
        
        // Làm tròn 2 chữ số thập phân
        return Math.round(finalPercentage * 100.0) / 100.0;
    }

    /**
     * Hàm giả lập tính điểm cho cả nhóm
     */
    public Map<String, Double> calculateGroupContributions(Map<String, StudentGitStats> groupStats) {
        int totalCommits = groupStats.values().stream().mapToInt(StudentGitStats::getCommits).sum();
        int totalLoc = groupStats.values().stream().mapToInt(StudentGitStats::getLoc).sum();

        Map<String, Double> results = new HashMap<>();
        
        for (Map.Entry<String, StudentGitStats> entry : groupStats.entrySet()) {
            StudentGitStats stat = entry.getValue();
            double percentage = calculateContributionPercentage(
                    stat.getCommits(), totalCommits, 
                    stat.getLoc(), totalLoc, 
                    stat.getRegularityScore()
            );
            results.put(entry.getKey(), percentage);
        }
        return results;
    }

    // Lớp nội bộ để chứa thống kê của sinh viên
    public static class StudentGitStats {
        private int commits;
        private int loc;
        private double regularityScore;

        public StudentGitStats(int commits, int loc, double regularityScore) {
            this.commits = commits;
            this.loc = loc;
            this.regularityScore = regularityScore;
        }

        public int getCommits() { return commits; }
        public int getLoc() { return loc; }
        public double getRegularityScore() { return regularityScore; }
    }
}
