package com.aita.gitanalytics.servlet;

import com.aita.gitanalytics.model.UserAccount;
import com.aita.gitanalytics.service.MarketplaceService;
import com.aita.gitanalytics.service.MarketplaceService.SandboxRunResult;
import com.google.gson.Gson;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet("/api/v1/marketplace/*")
public class MarketplaceServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final Gson gson = new Gson();
    private final MarketplaceService marketplaceService = new MarketplaceService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();

        UserAccount account = getAuthenticatedUser(request, response, out);
        if (account == null) return;

        String pathInfo = request.getPathInfo();
        if (pathInfo == null || pathInfo.equals("/") || pathInfo.equals("/peer-review")) {
            // Lấy thông tin chấm chéo xoay vòng của nhóm (Mặc định nhóm 6)
            int groupId = 6;
            Map<String, Object> reviewData = marketplaceService.getCircularPeerReviewOverview(groupId);
            out.print(gson.toJson(Map.of("status", "success", "data", reviewData)));
            return;
        }

        if (pathInfo.equals("/wallet")) {
            // Lấy ví điểm sửa lỗi (Correction Points Wallet)
            Map<String, Object> wallet = marketplaceService.getWalletInfo(account.getUserId());
            out.print(gson.toJson(Map.of("status", "success", "data", wallet)));
            return;
        }

        if (pathInfo.equals("/sandbox-demo")) {
            // Test demo chạy Docker sandbox
            SandboxRunResult runRes = marketplaceService.executeDockerSandbox("public class Demo{}".getBytes(), "Demo.java");
            out.print(gson.toJson(Map.of("status", "success", "data", runRes)));
            return;
        }

        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        out.print("{\"error\":\"Endpoint không tồn tại.\"}");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();

        UserAccount account = getAuthenticatedUser(request, response, out);
        if (account == null) return;

        String pathInfo = request.getPathInfo();

        if (pathInfo != null && pathInfo.equals("/peer-review")) {
            try {
                int reviewerGroupId = Integer.parseInt(request.getParameter("reviewerGroupId"));
                int revieweeGroupId = Integer.parseInt(request.getParameter("revieweeGroupId"));
                double sandboxScore = Double.parseDouble(request.getParameter("sandboxScore"));
                double mvc2Score = Double.parseDouble(request.getParameter("mvc2Score"));
                double defenseScore = Double.parseDouble(request.getParameter("defenseScore"));
                String feedback = request.getParameter("feedback");
                String sha256 = request.getParameter("sha256");

                boolean ok = marketplaceService.submitPeerReview(
                        reviewerGroupId, revieweeGroupId, sandboxScore, mvc2Score, defenseScore, feedback, sha256);

                if (ok) {
                    out.print("{\"status\":\"success\",\"message\":\"Đã gửi điểm chấm chéo thành công! +50 Điểm Sửa Lỗi đã được cộng vào ví của nhóm.\"}");
                } else {
                    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    out.print("{\"error\":\"Không thể lưu điểm chấm chéo vào hệ thống.\"}");
                }
            } catch (Exception e) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"error\":\"Tham số chấm chéo không hợp lệ: " + e.getMessage() + "\"}");
            }
            return;
        }

        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        out.print("{\"error\":\"Endpoint không tồn tại.\"}");
    }

    private UserAccount getAuthenticatedUser(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
        Object user = request.getSession(false) == null
                ? null : request.getSession(false).getAttribute("authenticatedUser");
        if (!(user instanceof UserAccount)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            out.print("{\"error\":\"Vui lòng đăng nhập lại.\"}");
            return null;
        }
        return (UserAccount) user;
    }
}
