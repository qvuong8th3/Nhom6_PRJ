package com.aita.gitanalytics.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Git Analytics Teamwork Assessor Core Engine
 * 
 * Mô hình tính điểm tổng hợp theo tiêu chuẩn đánh giá đóng góp:
 * Score = (W1 * Volume) + (W2 * Frequency) + (W3 * Review) - (W4 * Penalty)
 * 
 * - Volume (W1): Khối lượng mã nguồn (Lines of Code thực chất, loại bỏ file rác, thêm/xóa)
 * - Frequency (W2): Tần suất & độ đều đặn theo thời gian, phát hiện last-minute coding
 * - Review (W3): Pull Requests tạo ra, Reviews & comments đóng góp cho đồng đội
 * - Penalty (W4): Phạt commit spam (message: 'fix', 'update', 'asdf'), commit dồn không qua PR review
 */
public class GitScoreCalculatorService {

    // Regex phát hiện các commit message vô nghĩa, mang tính chất spam số lượng
    private static final Pattern SPAM_MESSAGE_PATTERN = Pattern.compile(
            "^(fix|update|asdf|test|abc|xyz|fix bug|update code|commit|edit|done|change|wip|temp|ok|.)[0-9!._-]*$",
            Pattern.CASE_INSENSITIVE
    );

    // Trọng số mặc định nếu không có cấu hình từ database
    public static final double DEFAULT_W_VOLUME = 0.35;      // W1: Khối lượng code (LOC)
    public static final double DEFAULT_W_FREQUENCY = 0.25;   // W2: Tần suất & độ đều đặn
    public static final double DEFAULT_W_REVIEW = 0.25;      // W3: Hoạt động Review & PR
    public static final double DEFAULT_W_PENALTY = 0.15;     // W4: Hệ số phạt commit rác/free-riding

    /**
     * DTO chứa cấu hình trọng số W1, W2, W3, W4
     */
    public static class AssessorWeights {
        public double wVolume;
        public double wFrequency;
        public double wReview;
        public double wPenalty;

        public AssessorWeights() {
            this(DEFAULT_W_VOLUME, DEFAULT_W_FREQUENCY, DEFAULT_W_REVIEW, DEFAULT_W_PENALTY);
        }

        public AssessorWeights(double wVolume, double wFrequency, double wReview, double wPenalty) {
            this.wVolume = wVolume;
            this.wFrequency = wFrequency;
            this.wReview = wReview;
            this.wPenalty = wPenalty;
        }
    }

    /**
     * DTO chứa toàn bộ dữ liệu Git thô của một thành viên trong nhóm
     */
    public static class StudentGitData {
        private int userId;
        private String name;
        private int commits;
        private int loc;
        private double regularityScore; // 0 - 100
        private int pullRequestsCount;
        private int reviewsCount;
        private List<String> commitMessages;
        private boolean isLastMinuteDumper; // Dồn commit vào phút chót mà không chia đều

        public StudentGitData(int userId, String name, int commits, int loc, double regularityScore,
                              int pullRequestsCount, int reviewsCount, List<String> commitMessages,
                              boolean isLastMinuteDumper) {
            this.userId = userId;
            this.name = name;
            this.commits = commits;
            this.loc = loc;
            this.regularityScore = regularityScore;
            this.pullRequestsCount = pullRequestsCount;
            this.reviewsCount = reviewsCount;
            this.commitMessages = commitMessages != null ? commitMessages : Collections.emptyList();
            this.isLastMinuteDumper = isLastMinuteDumper;
        }

        public int getUserId() { return userId; }
        public String getName() { return name; }
        public int getCommits() { return commits; }
        public int getLoc() { return loc; }
        public double getRegularityScore() { return regularityScore; }
        public int getPullRequestsCount() { return pullRequestsCount; }
        public int getReviewsCount() { return reviewsCount; }
        public List<String> getCommitMessages() { return commitMessages; }
        public boolean isLastMinuteDumper() { return isLastMinuteDumper; }
    }

    /**
     * Kết quả tính điểm và phân tích rủi ro đóng góp (Free-rider Flag)
     */
    public static class EvaluatedContribution {
        public double normalizedPercentage; // Điểm đóng góp cuối cùng (%)
        public double rawScore;              // Điểm tổng hợp thô
        public double volumeScore;           // Tỉ lệ volume (0.0 - 1.0)
        public double frequencyScore;        // Tỉ lệ frequency (0.0 - 1.0)
        public double reviewScore;           // Tỉ lệ review (0.0 - 1.0)
        public double penaltyScore;          // Điểm phạt (0.0 - 1.0)
        public List<String> penaltyReasons;  // Danh sách các cảnh báo phạt
        public boolean isFreeRiderSuspect;   // Cờ cảnh báo Free-riding

        public EvaluatedContribution() {
            this.penaltyReasons = new ArrayList<>();
        }
    }

    /**
     * Tính toán toàn diện tỷ lệ đóng góp của từng thành viên với trọng số W1, W2, W3, W4
     */
    public Map<String, EvaluatedContribution> evaluateGroupContributions(
            Map<String, StudentGitData> groupData, AssessorWeights weights) {
        
        Map<String, EvaluatedContribution> results = new HashMap<>();
        if (groupData == null || groupData.isEmpty()) {
            return results;
        }

        if (weights == null) {
            weights = new AssessorWeights();
        }

        // 1. Tính tổng các chỉ số trong cả nhóm
        int totalLoc = groupData.values().stream().mapToInt(StudentGitData::getLoc).sum();
        double totalRegularity = groupData.values().stream().mapToDouble(StudentGitData::getRegularityScore).sum();
        int totalReviewsAndPrs = groupData.values().stream()
                .mapToInt(d -> (d.getPullRequestsCount() * 2) + d.getReviewsCount()).sum();

        Map<String, Double> preliminaryScores = new HashMap<>();

        // 2. Tính điểm từng thành viên theo mô hình:
        // RawScore = (W1 * VolumeRatio) + (W2 * FrequencyRatio) + (W3 * ReviewRatio) - (W4 * PenaltyRatio)
        for (Map.Entry<String, StudentGitData> entry : groupData.entrySet()) {
            String key = entry.getKey();
            StudentGitData data = entry.getValue();
            EvaluatedContribution eval = new EvaluatedContribution();

            // Chỉ số 1: Code Volume Ratio
            double volumeRatio = (totalLoc <= 0) ? 0 : Math.max(0, (double) data.getLoc() / totalLoc);

            // Chỉ số 2: Frequency & Consistency Ratio
            double freqRatio = (totalRegularity <= 0) ? 0 : Math.max(0, data.getRegularityScore() / totalRegularity);

            // Chỉ số 3: Review & PR Management Ratio (1 PR = 2 review points)
            int personalReviewPoints = (data.getPullRequestsCount() * 2) + data.getReviewsCount();
            double reviewRatio = (totalReviewsAndPrs <= 0) ? 0 : (double) personalReviewPoints / totalReviewsAndPrs;

            // Chỉ số 4: Hệ số Phạt (Penalty)
            double penaltyPoints = 0.0;

            // Kiểm tra commit spam / tin nhắn rác
            int spamCount = 0;
            for (String msg : data.getCommitMessages()) {
                if (msg != null && isSpamCommitMessage(msg.trim())) {
                    spamCount++;
                }
            }
            if (data.getCommits() > 0 && ((double) spamCount / data.getCommits() >= 0.4)) {
                penaltyPoints += 0.40;
                eval.penaltyReasons.add("Có " + spamCount + "/" + data.getCommits() + " commits nội dung không rõ ràng ('fix', 'update', 'asdf').");
            }

            // Kiểm tra dồn việc phút chót (Last-minute coding / big dump)
            if (data.isLastMinuteDumper()) {
                penaltyPoints += 0.35;
                eval.penaltyReasons.add("Phát hiện dồn toàn bộ code vào sát deadline (Last-minute commit).");
            }

            // Kiểm tra ít hoặc không tham gia Code Review cho đồng đội
            if (data.getCommits() > 0 && personalReviewPoints == 0 && totalReviewsAndPrs > 0) {
                penaltyPoints += 0.15;
                eval.penaltyReasons.add("Chưa từng tạo Pull Request hoặc Review code cho đồng đội.");
            }

            // Giới hạn penalty tối đa 1.0
            double penaltyRatio = Math.min(1.0, penaltyPoints);

            // Áp dụng công thức có trọng số
            double rawScore = (weights.wVolume * volumeRatio)
                            + (weights.wFrequency * freqRatio)
                            + (weights.wReview * reviewRatio)
                            - (weights.wPenalty * penaltyRatio);

            // Không để điểm âm
            rawScore = Math.max(0.01, rawScore);

            eval.rawScore = rawScore;
            eval.volumeScore = Math.round(volumeRatio * 1000.0) / 10.0;
            eval.frequencyScore = Math.round(freqRatio * 1000.0) / 10.0;
            eval.reviewScore = Math.round(reviewRatio * 1000.0) / 10.0;
            eval.penaltyScore = Math.round(penaltyRatio * 100.0);

            preliminaryScores.put(key, rawScore);
            results.put(key, eval);
        }

        // 3. Chuẩn hóa về thang 100% cho cả nhóm
        double totalRawScore = preliminaryScores.values().stream().mapToDouble(Double::doubleValue).sum();
        double averagePercentage = groupData.size() > 0 ? 100.0 / groupData.size() : 0.0;

        for (Map.Entry<String, EvaluatedContribution> entry : results.entrySet()) {
            EvaluatedContribution eval = entry.getValue();
            double normalized = (totalRawScore <= 0) 
                    ? averagePercentage 
                    : (eval.rawScore / totalRawScore) * 100.0;

            eval.normalizedPercentage = Math.round(normalized * 100.0) / 100.0;

            // Xác định Free-rider: Điểm thấp hơn 50% mức bình quân hoặc có điểm phạt cao
            if (eval.normalizedPercentage < (averagePercentage * 0.5) || eval.penaltyScore >= 40.0) {
                eval.isFreeRiderSuspect = true;
            }
        }

        return results;
    }

    /**
     * Phương thức backward compatibility tương thích mã cũ
     */
    public Map<String, Double> calculateGroupContributions(Map<String, StudentGitData> groupData) {
        Map<String, EvaluatedContribution> evaluated = evaluateGroupContributions(groupData, new AssessorWeights());
        Map<String, Double> simpleResults = new HashMap<>();
        for (Map.Entry<String, EvaluatedContribution> entry : evaluated.entrySet()) {
            simpleResults.put(entry.getKey(), entry.getValue().normalizedPercentage);
        }
        return simpleResults;
    }

    /**
     * Nhận diện thông điệp commit rác
     */
    public static boolean isSpamCommitMessage(String msg) {
        if (msg == null || msg.trim().length() <= 3) {
            return true;
        }
        String clean = msg.trim().toLowerCase(Locale.ROOT);
        return SPAM_MESSAGE_PATTERN.matcher(clean).matches();
    }
}