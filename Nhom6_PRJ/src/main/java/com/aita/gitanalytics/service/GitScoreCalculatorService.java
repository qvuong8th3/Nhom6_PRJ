package com.aita.gitanalytics.service;

import java.util.HashMap;
import java.util.Map;

public class GitScoreCalculatorService {

    // Trọng số đánh giá thuật toán
    private static final double WEIGHT_COMMITS = 0.3;     // 30% cho số lượng commit
    private static final double WEIGHT_LOC = 0.5;         // 50% cho số dòng code (LOC)
    private static final double WEIGHT_REGULARITY = 0.2;  // 20% cho mức độ đều đặn (Regularity)

    /**
     * Tính điểm chuẩn hóa phần trăm đóng góp cho toàn nhóm
     */
    public Map<String, Double> calculateGroupContributions(Map<String, StudentGitData> groupData) {
        Map<String, Double> results = new HashMap<>();
        
        if (groupData == null || groupData.isEmpty()) {
            return results;
        }

        // 1. Tính tổng các chỉ số của cả nhóm
        int totalCommits = groupData.values().stream().mapToInt(StudentGitData::getCommits).sum();
        int totalLoc = groupData.values().stream().mapToInt(StudentGitData::getLoc).sum();
        double totalRegularity = groupData.values().stream().mapToDouble(StudentGitData::getRegularityScore).sum();

        // 2. Tính tỷ lệ % đóng góp chuẩn hóa cho từng thành viên
        for (Map.Entry<String, StudentGitData> entry : groupData.entrySet()) {
            StudentGitData data = entry.getValue();

            // Tính tỷ lệ đóng góp của cá nhân so với TỔNG CỦA NHÓM ở từng tiêu chí (thang 0.0 -> 1.0)
            double commitRatio = (totalCommits == 0) ? 0 : (double) data.getCommits() / totalCommits;
            double locRatio = (totalLoc == 0) ? 0 : (double) data.getLoc() / totalLoc;
            double regularityRatio = (totalRegularity == 0) ? 0 : data.getRegularityScore() / totalRegularity;

            // Tổng hợp điểm phần trăm đóng góp
            double finalPercentage = (commitRatio * WEIGHT_COMMITS 
                                    + locRatio * WEIGHT_LOC 
                                    + regularityRatio * WEIGHT_REGULARITY) * 100.0;

            // Làm tròn 2 chữ số thập phân
            double roundedPercentage = Math.round(finalPercentage * 100.0) / 100.0;
            results.put(entry.getKey(), roundedPercentage);
        }

        return results;
    }

    // Lớp DTO chứa dữ liệu đầu vào của Sinh viên
    public static class StudentGitData {
        private String name;
        private int commits;
        private int loc;
        private double regularityScore;

        public StudentGitData(String name, int commits, int loc, double regularityScore) {
            this.name = name;
            this.commits = commits;
            this.loc = loc;
            this.regularityScore = regularityScore;
        }

        public String getName() { return name; }
        public int getCommits() { return commits; }
        public int getLoc() { return loc; }
        public double getRegularityScore() { return regularityScore; }
    }

    // HÀM MAIN CHẠY DEMO THỬ THUẬT TOÁN
    public static void main(String[] args) {
        System.out.println("========== DEMO THUẬT TOÁN TÍNH ĐIỂM GIT (ĐÃ TỐI ƯU) ==========");
        
        Map<String, StudentGitData> mockGroup = new HashMap<>();
        
        // Dữ liệu Mock Data 5 thành viên nhóm
        mockGroup.put("SV_01", new StudentGitData("Nguyễn Đặng Trường Hải", 45, 2500, 95.0));
        mockGroup.put("SV_02", new StudentGitData("Nguyễn Quốc Vương", 20, 1800, 40.0));
        mockGroup.put("SV_03", new StudentGitData("Võ Xuân Long", 25, 1200, 80.0));
        mockGroup.put("SV_04", new StudentGitData("Nguyễn Tuấn Kiệt", 40, 300, 85.0));
        mockGroup.put("SV_05", new StudentGitData("Võ Thảo Nguyên", 2, 50, 10.0));

        GitScoreCalculatorService service = new GitScoreCalculatorService();
        Map<String, Double> finalScores = service.calculateGroupContributions(mockGroup);

        System.out.println("Tổng số lượng thành viên: 5");
        System.out.println("Trọng số: 30% Commits | 50% Lines of Code | 20% Độ đều đặn");
        System.out.println("-------------------------------------------------------------------------------");
       System.out.printf("%-8s %-25s %-10s %-10s %-15s %-15s\n", 
                        "Mã SV", "Họ Tên", "Commits", "LOC", "Độ Đều Đặn", "Tỷ lệ Đóng Góp");
        System.out.println("-------------------------------------------------------------------------------");

        double totalScore = 0;
        for (Map.Entry<String, StudentGitData> entry : mockGroup.entrySet()) {
            String key = entry.getKey();
            StudentGitData data = entry.getValue();
            double score = finalScores.get(key);
            totalScore += score;
            
            System.out.printf("%-10s %-25s %-10d %-10d %-15.1f %-15.2f%%\n", 
                    key, data.getName(), data.getCommits(), data.getLoc(), data.getRegularityScore(), score);
        }
        
        System.out.println("-------------------------------------------------------------------------------");
        System.out.printf("TỔNG CỘNG TẤT CẢ THÀNH VIÊN:                                      %.2f%%\n", totalScore);
        System.out.println("===============================================================================");
    }
}