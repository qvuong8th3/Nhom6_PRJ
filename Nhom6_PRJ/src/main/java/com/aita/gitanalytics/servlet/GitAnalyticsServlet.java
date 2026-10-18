package com.aita.gitanalytics.servlet;

import com.aita.gitanalytics.dao.ContributionDAO;
import com.aita.gitanalytics.dao.ContributionDAO.StudentContribution;
import com.aita.gitanalytics.dao.UserDAO;
import com.aita.gitanalytics.model.UserAccount;
import com.aita.gitanalytics.service.GitScoreCalculatorService;
import com.aita.gitanalytics.service.GitScoreCalculatorService.AssessorWeights;
import com.aita.gitanalytics.service.GitScoreCalculatorService.EvaluatedContribution;
import com.aita.gitanalytics.service.GitScoreCalculatorService.StudentGitData;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet(urlPatterns = {"/api/v1/git-analytics/dashboard", "/api/v1/git-analytics/settings", "/api/v1/git-analytics/repository", "/api/v1/git-analytics/sync"})
public class GitAnalyticsServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final Gson gson = new Gson();
    private final ContributionDAO contributionDAO = new ContributionDAO();
    private final UserDAO userDAO = new UserDAO();
    private final GitScoreCalculatorService scoreCalculator = new GitScoreCalculatorService();
    private final com.aita.gitanalytics.service.GitHubSyncService gitHubSyncService = new com.aita.gitanalytics.service.GitHubSyncService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();

        UserAccount account = getAuthenticatedUser(request, response, out);
        if (account == null) return;

        int groupId = resolveGroupId(request, response, out, account);
        if (groupId < 0) return;

        boolean studentWithoutGroup = (groupId == 0);
        int targetGroupId = (groupId == 0) ? 1 : groupId; // fallback demo group 1

        // 1. Lấy dữ liệu Git thô (Commits, PRs, Reviews, Timeline)
        Map<Integer, StudentGitData> groupGitData = contributionDAO.getGroupGitRawData(targetGroupId);

        // Chuyển key sang String để truyền vào core evaluator
        Map<String, StudentGitData> evalInput = new LinkedHashMap<>();
        for (Map.Entry<Integer, StudentGitData> entry : groupGitData.entrySet()) {
            evalInput.put(String.valueOf(entry.getKey()), entry.getValue());
        }

        // 2. Lấy cấu hình trọng số W1, W2, W3, W4 từ DB
        AssessorWeights weights = contributionDAO.getGroupWeights(targetGroupId);

        // 3. Chạy thuật toán tính điểm toàn diện Assessor: (W1*Volume + W2*Freq + W3*Review - W4*Penalty)
        Map<String, EvaluatedContribution> evalResults = scoreCalculator.evaluateGroupContributions(evalInput, weights);

        // 4. Tổng hợp danh sách StudentContribution
        List<StudentContribution> contributions = new ArrayList<>();
        for (Map.Entry<Integer, StudentGitData> entry : groupGitData.entrySet()) {
            int uid = entry.getKey();
            StudentGitData raw = entry.getValue();
            EvaluatedContribution eval = evalResults.get(String.valueOf(uid));

            StudentContribution sc = new StudentContribution(
                    uid,
                    raw.getName(),
                    raw.getCommits(),
                    raw.getLoc(),
                    (int) raw.getRegularityScore(),
                    raw.getPullRequestsCount(),
                    raw.getReviewsCount(),
                    eval != null ? eval.penaltyScore : 0,
                    eval != null ? eval.normalizedPercentage : 0
            );
            if (eval != null) {
                sc.suggestedPercentage = eval.normalizedPercentage;
                sc.volumeScore = eval.volumeScore;
                sc.frequencyScore = eval.frequencyScore;
                sc.reviewScore = eval.reviewScore;
                sc.penaltyReasons = eval.penaltyReasons;
                sc.isFreeRiderSuspect = eval.isFreeRiderSuspect;
            }
            contributions.add(sc);
        }

        // 5. Chuẩn bị Map dữ liệu trả về client
        Map<String, Object> data = new HashMap<>();
        data.putAll(contributionDAO.getGroupMetadata(targetGroupId));
        if (studentWithoutGroup) {
            data.put("groupName", "Tài khoản chưa được thêm vào nhóm (Hiển thị dữ liệu mẫu)");
        }
        data.put("contributions", contributions);
        data.put("weights", weights);
        data.put("timeline", contributionDAO.getGroupTimeline(targetGroupId));

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("status", "success");
        responseBody.put("data", data);

        out.print(gson.toJson(responseBody));
        out.flush();
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();

        UserAccount account = getAuthenticatedUser(request, response, out);
        if (account == null) return;

        if (!"LECTURER".equals(account.getRole()) && !"ADMIN".equals(account.getRole())) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            out.print("{\"error\":\"Chỉ giảng viên hoặc quản trị viên mới có quyền điều chỉnh trọng số.\"}");
            return;
        }

        String servletPath = request.getServletPath();

        if (servletPath.endsWith("/sync")) {
            int groupId = resolveGroupId(request, response, out, account);
            int targetGroupId = (groupId <= 0) ? 1 : groupId;
            com.aita.gitanalytics.service.GitHubSyncService.SyncResult syncRes = gitHubSyncService.syncRepository(targetGroupId);
            if (syncRes.success) {
                out.print("{\"status\":\"success\",\"message\":\"" + syncRes.message + "\"}");
            } else {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                out.print("{\"error\":\"" + syncRes.message + "\"}");
            }
            return;
        }

        if (servletPath.endsWith("/repository")) {
            try {
                int groupId = Integer.parseInt(request.getParameter("groupId"));
                String repoUrl = request.getParameter("repoUrl");
                if (repoUrl == null || !repoUrl.trim().startsWith("https://github.com/")) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    out.print("{\"error\":\"Đường dẫn Repository phải bắt đầu bằng https://github.com/\"}");
                    return;
                }
                boolean success = contributionDAO.updateRepoUrl(groupId, repoUrl.trim());
                if (success) {
                    // Tự động kéo dữ liệu commit thực tế từ GitHub về ngay lập tức
                    com.aita.gitanalytics.service.GitHubSyncService.SyncResult syncRes = gitHubSyncService.syncRepository(groupId);
                    String msg = "Đã liên kết repo mới!";
                    if (syncRes.success) {
                        msg += " " + syncRes.message;
                    }
                    out.print("{\"status\":\"success\",\"message\":\"" + msg + "\"}");
                } else {
                    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    out.print("{\"error\":\"Không tìm thấy nhóm hoặc lỗi database khi lưu repo.\"}");
                }
            } catch (Exception e) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"error\":\"Dữ liệu Repository không hợp lệ.\"}");
            }
            return;
        }

        try {
            int groupId = Integer.parseInt(request.getParameter("groupId"));
            double volumeWeight = Double.parseDouble(request.getParameter("volumeWeight"));
            double frequencyWeight = Double.parseDouble(request.getParameter("frequencyWeight"));
            double reviewWeight = Double.parseDouble(request.getParameter("reviewWeight"));
            double penaltyWeight = Double.parseDouble(request.getParameter("penaltyWeight"));

            boolean success = contributionDAO.updateGroupWeights(groupId, volumeWeight, frequencyWeight, reviewWeight, penaltyWeight);
            if (success) {
                out.print("{\"status\":\"success\",\"message\":\"Cập nhật trọng số thành công.\"}");
            } else {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                out.print("{\"error\":\"Không thể cập nhật cấu hình vào database.\"}");
            }
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print("{\"error\":\"Tham số trọng số không hợp lệ.\"}");
        }
    }

    private UserAccount getAuthenticatedUser(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
        Object authenticatedUser = request.getSession(false) == null
                ? null : request.getSession(false).getAttribute("authenticatedUser");
        if (!(authenticatedUser instanceof UserAccount)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Vui lòng đăng nhập lại.");
            out.print(gson.toJson(error));
            return null;
        }
        return (UserAccount) authenticatedUser;
    }

    private int resolveGroupId(HttpServletRequest request, HttpServletResponse response, PrintWriter out, UserAccount account) {
        if ("STUDENT".equals(account.getRole())) {
            Integer assignedGroupId = userDAO.getStudentGroupId(account.getUserId());
            return assignedGroupId == null ? 0 : assignedGroupId;
        } else if ("LECTURER".equals(account.getRole()) || "ADMIN".equals(account.getRole())) {
            String groupIdStr = request.getParameter("groupId");
            if (groupIdStr == null || groupIdStr.trim().isEmpty()) {
                return 1; // Default Nhóm 1
            }
            try {
                return Integer.parseInt(groupIdStr);
            } catch (NumberFormatException e) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"error\":\"groupId không hợp lệ.\"}");
                return -1;
            }
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        out.print("{\"error\":\"Tài khoản không có quyền xem dashboard.\"}");
        return -1;
    }
}
