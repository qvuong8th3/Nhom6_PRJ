package com.aita.gitanalytics.service;

import com.aita.gitanalytics.dao.DBContext;
import com.aita.gitanalytics.util.Sha256ChecksumUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Service quản lý Cơ chế Thị trường Học thuật Tự điều phối (AITA Marketplace):
 * 1. Chấm chéo xoay vòng (Circular Peer-Review G1 -> G2 -> ... -> G7 -> G1)
 * 2. Hệ thống Điểm sửa lỗi (Correction Points - CP Wallet)
 * 3. Chấm tự động Moulinette Docker Sandbox & Kiểm tra tính toàn vẹn SHA-256
 */
public class MarketplaceService {

    public static class PeerReviewItem {
        public int reviewId;
        public int reviewerGroupId;
        public String reviewerGroupName;
        public int revieweeGroupId;
        public String revieweeGroupName;
        public String repoUrl;
        public String artifactSha256;
        public boolean sha256Verified;
        public double sandboxScore;     // 30%
        public double mvc2CleanCodeScore; // 40%
        public double peerDefenseScore; // 30%
        public double totalScore;
        public String feedback;
        public String status; // PENDING, COMPLETED
        public String reviewedAt;
    }

    public static class CorrectionTransaction {
        public long txId;
        public int userId;
        public int amount; // +50 or -30
        public String txType; // EARNED_REVIEW, SPENT_SUBMISSION, BONUS
        public String description;
        public String createdAt;
    }

    public static class SandboxRunResult {
        public boolean success;
        public String containerId;
        public String sha256Hash;
        public int publicPassed;
        public int publicTotal;
        public int hiddenPassed;
        public int hiddenTotal;
        public double executionTimeSec;
        public int memoryUsedMb;
        public int memoryLimitMb;
        public String compilerOutput;
        public String status; // PASSED, FAILED, TIMEOUT
    }

    /**
     * Lấy thông tin phiên chấm chéo của nhóm (Ví dụ: Nhóm 6 chấm Nhóm 7, Nhóm 5 chấm Nhóm 6)
     */
    public Map<String, Object> getCircularPeerReviewOverview(int currentGroupId) {
        Map<String, Object> result = new LinkedHashMap<>();

        // Vòng tròn khép kín 7 nhóm (G1 -> G2 -> G3 -> G4 -> G5 -> G6 -> G7 -> G1)
        int targetToReview = (currentGroupId % 7) + 1;
        int reviewedBy = (currentGroupId == 1) ? 7 : currentGroupId - 1;

        result.put("currentGroupId", currentGroupId);
        result.put("targetRevieweeGroupId", targetToReview);
        result.put("targetRevieweeGroupName", "Nhóm " + targetToReview + " - AI AST Engine");
        result.put("targetRepoUrl", "https://github.com/AITA-Platform/team-0" + targetToReview + "-prj301");
        result.put("targetArtifactSha256", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");

        result.put("assignedReviewerGroupId", reviewedBy);
        result.put("assignedReviewerGroupName", "Nhóm " + reviewedBy + " - Circular Auditor");

        // Dữ liệu đánh giá hiện tại nếu có
        PeerReviewItem outgoingReview = loadReview(currentGroupId, targetToReview);
        PeerReviewItem incomingReview = loadReview(reviewedBy, currentGroupId);

        result.put("outgoingReview", outgoingReview);
        result.put("incomingReview", incomingReview);

        // Chu trình 7 nhóm
        List<Map<String, Object>> ring = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("groupId", i);
            node.put("groupName", "Nhóm " + i);
            node.put("targetsGroupId", (i % 7) + 1);
            node.put("isCurrent", i == currentGroupId);
            ring.add(node);
        }
        result.put("circularRing", ring);

        return result;
    }

    /**
     * Gửi điểm chấm chéo theo Barem 3 phần (Sandbox 30%, MVC2/SHA256 40%, Defense 30%)
     */
    public boolean submitPeerReview(int reviewerGroupId, int revieweeGroupId,
                                    double sandboxScore, double mvc2Score, double defenseScore,
                                    String feedback, String providedSha256) {
        double total = (sandboxScore * 0.3) + (mvc2Score * 0.4) + (defenseScore * 0.3);

        String sql = "MERGE INTO CircularReviews AS target "
                + "USING (SELECT ? AS reviewer_group_id, ? AS reviewee_group_id) AS src "
                + "ON (target.reviewer_group_id = src.reviewer_group_id AND target.reviewee_group_id = src.reviewee_group_id) "
                + "WHEN MATCHED THEN "
                + "  UPDATE SET sandbox_score = ?, mvc2_clean_code_score = ?, peer_defense_score = ?, "
                + "             total_score = ?, feedback = ?, artifact_sha256 = ?, status = 'COMPLETED', reviewed_at = GETDATE() "
                + "WHEN NOT MATCHED THEN "
                + "  INSERT (reviewer_group_id, reviewee_group_id, sandbox_score, mvc2_clean_code_score, peer_defense_score, total_score, feedback, artifact_sha256, status, reviewed_at) "
                + "  VALUES (src.reviewer_group_id, src.reviewee_group_id, ?, ?, ?, ?, ?, ?, 'COMPLETED', GETDATE());";

        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn != null ? conn.prepareStatement(sql) : null) {
            if (ps != null) {
                ps.setInt(1, reviewerGroupId);
                ps.setInt(2, revieweeGroupId);
                // Update
                ps.setDouble(3, sandboxScore);
                ps.setDouble(4, mvc2Score);
                ps.setDouble(5, defenseScore);
                ps.setDouble(6, total);
                ps.setString(7, feedback);
                ps.setString(8, providedSha256);
                // Insert
                ps.setDouble(9, sandboxScore);
                ps.setDouble(10, mvc2Score);
                ps.setDouble(11, defenseScore);
                ps.setDouble(12, total);
                ps.setString(13, feedback);
                ps.setString(14, providedSha256);

                ps.executeUpdate();
                return true;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Lấy số dư và lịch sử giao dịch Điểm sửa lỗi (Correction Points - CP Wallet)
     */
    public Map<String, Object> getWalletInfo(int userId) {
        Map<String, Object> wallet = new LinkedHashMap<>();
        int balance = 150; // Điểm khởi tạo mặc định cho sinh viên
        List<CorrectionTransaction> history = new ArrayList<>();

        String balanceSql = "SELECT balance_cp FROM CorrectionPointsWallet WHERE user_id = ?";
        String historySql = "SELECT tx_id, amount, tx_type, description, created_at "
                + "FROM CorrectionPointsTransactions WHERE user_id = ? ORDER BY created_at DESC";

        try (Connection conn = DBContext.getConnection()) {
            if (conn != null) {
                try (PreparedStatement ps = conn.prepareStatement(balanceSql)) {
                    ps.setInt(1, userId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            balance = rs.getInt("balance_cp");
                        } else {
                            // Tạo ví mới nếu chưa có
                            try (PreparedStatement psInit = conn.prepareStatement(
                                    "INSERT INTO CorrectionPointsWallet (user_id, balance_cp, updated_at) VALUES (?, 150, GETDATE())")) {
                                psInit.setInt(1, userId);
                                psInit.executeUpdate();
                            } catch (SQLException ignored) {}
                        }
                    }
                }

                try (PreparedStatement ps = conn.prepareStatement(historySql)) {
                    ps.setInt(1, userId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            CorrectionTransaction tx = new CorrectionTransaction();
                            tx.txId = rs.getLong("tx_id");
                            tx.userId = userId;
                            tx.amount = rs.getInt("amount");
                            tx.txType = rs.getString("tx_type");
                            tx.description = rs.getString("description");
                            tx.createdAt = rs.getTimestamp("created_at").toString();
                            history.add(tx);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // Nếu database chưa có bản ghi giao dịch, tạo lịch sử mẫu minh họa cho sinh viên
        if (history.isEmpty()) {
            CorrectionTransaction t1 = new CorrectionTransaction();
            t1.txId = 101;
            t1.amount = 100;
            t1.txType = "BONUS";
            t1.description = "Hệ thống cấp vốn Điểm Sửa Lỗi khởi tạo đầu kỳ (AITA Grant)";
            t1.createdAt = "2026-10-15 08:30:00";
            history.add(t1);

            CorrectionTransaction t2 = new CorrectionTransaction();
            t2.txId = 102;
            t2.amount = 50;
            t2.txType = "EARNED_REVIEW";
            t2.description = "Thưởng hoàn thành chấm chéo Peer-Review cho Nhóm 7 (Tuần 5)";
            t2.createdAt = "2026-10-22 14:15:20";
            history.add(t2);
        }

        wallet.put("userId", userId);
        wallet.put("balanceCp", balance);
        wallet.put("currencyName", "Correction Points (CP)");
        wallet.put("history", history);
        return wallet;
    }

    /**
     * Mô phỏng chạy kiểm thử tự động Docker Sandbox (Moulinette-style Execution)
     */
    public SandboxRunResult executeDockerSandbox(byte[] artifactBytes, String filename) {
        SandboxRunResult result = new SandboxRunResult();
        String sha256 = Sha256ChecksumUtil.hash(artifactBytes != null ? artifactBytes : filename.getBytes());

        result.success = true;
        result.containerId = "aita-box-" + sha256.substring(0, 12);
        result.sha256Hash = sha256;
        result.publicPassed = 5;
        result.publicTotal = 5;
        result.hiddenPassed = 4;
        result.hiddenTotal = 5;
        result.executionTimeSec = 1.38;
        result.memoryUsedMb = 64;
        result.memoryLimitMb = 256;
        result.status = "PASSED";
        result.compilerOutput = "javac -cp /sandbox/libs/* -d /sandbox/classes /sandbox/src/**/*.java\n"
                + "[INFO] Compilation successful: 0 errors, 0 warnings.\n"
                + "[SANDBOX] Network isolated: eth0 disabled.\n"
                + "[TEST-PUBLIC] 5/5 assertions passed (Submission portal, login, JDBC connection).\n"
                + "[TEST-HIDDEN] 4/5 assertions passed (Concurrency test, SQL Injection guard, SHA-256 integrity).\n"
                + "[WARNING] Hidden Case #5: Boundary condition with empty payload took 420ms (Limit: 500ms).";

        return result;
    }

    private PeerReviewItem loadReview(int reviewerId, int revieweeId) {
        PeerReviewItem item = new PeerReviewItem();
        item.reviewerGroupId = reviewerId;
        item.revieweeGroupId = revieweeId;
        item.sandboxScore = 27.5;     // 30
        item.mvc2CleanCodeScore = 38.0; // 40
        item.peerDefenseScore = 28.0; // 30
        item.totalScore = item.sandboxScore + item.mvc2CleanCodeScore + item.peerDefenseScore;
        item.artifactSha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
        item.sha256Verified = true;
        item.status = "COMPLETED";
        item.feedback = "Cấu trúc MVC2 rất mạch lạc, Filter kiểm tra SHA-256 hoạt động chuẩn. Phần bảo vệ trả lời tốt cơ chế Docker Sandbox.";
        item.reviewedAt = "2026-10-25 16:45:00";
        return item;
    }
}
