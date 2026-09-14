package com.aita.gitanalytics.servlet;

import com.aita.gitanalytics.dao.ContributionDAO;
import com.aita.gitanalytics.dao.ContributionDAO.StudentContribution;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Servlet API trả về dữ liệu Dashboard Git Analytics
 * Framework: Java Servlet (Chuẩn bị cho Tuần 2)
 */
@WebServlet("/api/v1/git-analytics/dashboard")
public class GitAnalyticsServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final Gson gson = new Gson();
    private final ContributionDAO contributionDAO = new ContributionDAO();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();

        // 1. Kiểm tra JWT Token (Giả lập)
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Unauthorized. Missing JWT Token.");
            out.print(gson.toJson(error));
            return;
        }

        // Lấy groupId từ params
        String groupIdStr = request.getParameter("groupId");
        if (groupIdStr == null) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Missing groupId parameter.");
            out.print(gson.toJson(error));
            return;
        }

        int groupId = 1; // Default
        try {
            groupId = Integer.parseInt(groupIdStr);
        } catch (NumberFormatException e) {
            // ignore
        }

        // 2. Gọi tầng DAO để lấy dữ liệu thực tế từ Database
        List<StudentContribution> contributions = contributionDAO.getGroupContributions(groupId);

        // 3. Chuẩn bị Map dữ liệu trả về và dùng Gson để chuyển thành JSON
        Map<String, Object> data = new HashMap<>();
        data.put("groupName", "Nhóm 6 - SE1234 (Từ DB)");
        data.put("repoUrl", "https://github.com/SE-Group6/AITA-Project");
        data.put("contributions", contributions);

        // Timeline mock data 
        Map<String, Object> timeline = new HashMap<>();
        timeline.put("labels", new String[]{"Week 1", "Week 2", "Week 3", "Week 4", "Week 5"});
        
        // Mocking datasets for the chart
        Map<String, Object> ds1 = new HashMap<>(); ds1.put("label", "Nguyễn Văn A (DB Mock)"); ds1.put("data", new int[]{5,10,8,12,7}); ds1.put("borderColor", "#3b82f6");
        Map<String, Object> ds2 = new HashMap<>(); ds2.put("label", "Trần Thị B (DB Mock)"); ds2.put("data", new int[]{4,8,5,10,3}); ds2.put("borderColor", "#10b981");
        Map<String, Object> ds3 = new HashMap<>(); ds3.put("label", "Lê Văn C (DB Mock)"); ds3.put("data", new int[]{0,0,0,0,2}); ds3.put("borderColor", "#ef4444");
        Map<String, Object> ds4 = new HashMap<>(); ds4.put("label", "Phạm Văn D (DB Mock)"); ds4.put("data", new int[]{3,5,10,4,6}); ds4.put("borderColor", "#f59e0b");
        
        timeline.put("datasets", new Object[]{ds1, ds2, ds3, ds4});
        data.put("timeline", timeline);

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("status", "success");
        responseBody.put("data", data);

        // 4. Trả về JSON chuẩn bằng Gson
        out.print(gson.toJson(responseBody));
        out.flush();
    }
}
