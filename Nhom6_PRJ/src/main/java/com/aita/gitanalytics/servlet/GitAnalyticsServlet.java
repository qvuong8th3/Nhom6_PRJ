package com.aita.gitanalytics.servlet;

import com.aita.gitanalytics.dao.ContributionDAO;
import com.aita.gitanalytics.dao.ContributionDAO.StudentContribution;
import com.aita.gitanalytics.dao.UserDAO;
import com.aita.gitanalytics.model.UserAccount;
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
    private final UserDAO userDAO = new UserDAO();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();

        Object authenticatedUser = request.getSession(false) == null
            ? null : request.getSession(false).getAttribute("authenticatedUser");
        if (!(authenticatedUser instanceof UserAccount)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Vui lòng đăng nhập lại.");
            out.print(gson.toJson(error));
            return;
        }

        UserAccount account = (UserAccount) authenticatedUser;
        int groupId;
        boolean studentWithoutGroup = false;
        if ("STUDENT".equals(account.getRole())) {
            Integer assignedGroupId = userDAO.getStudentGroupId(account.userId);
            if (assignedGroupId == null) {
                groupId = 0;
                studentWithoutGroup = true;
            } else {
                groupId = assignedGroupId;
            }
        } else if ("LECTURER".equals(account.role)) {
            String groupIdStr = request.getParameter("groupId");
            try {
                groupId = Integer.parseInt(groupIdStr);
            } catch (NumberFormatException e) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"error\":\"groupId không hợp lệ.\"}");
                return;
            }
        } else {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            out.print("{\"error\":\"Tài khoản không có quyền xem dashboard.\"}");
            return;
        }

        // 2. Gọi tầng DAO để lấy dữ liệu thực tế từ Database
        List<StudentContribution> contributions = contributionDAO.getGroupContributions(groupId);

        // 3. Chuẩn bị Map dữ liệu trả về và dùng Gson để chuyển thành JSON
        Map<String, Object> data = new HashMap<>();
        data.putAll(contributionDAO.getGroupMetadata(groupId));
        if (studentWithoutGroup) {
            data.put("groupName", "Tài khoản chưa được thêm vào nhóm");
        }
        data.put("contributions", contributions);

        data.put("timeline", contributionDAO.getGroupTimeline(groupId));

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("status", "success");
        responseBody.put("data", data);

        // 4. Trả về JSON chuẩn bằng Gson
        out.print(gson.toJson(responseBody));
        out.flush();
    }
}
