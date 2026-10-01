package com.aita.gitanalytics.servlet;

import com.aita.gitanalytics.dao.UserDAO;
import com.aita.gitanalytics.dao.UserDAO.UserAccount;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet("/api/v1/students")
public class StudentServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final Gson gson = new Gson();
    private final UserDAO userDAO = new UserDAO();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!requireLecturer(request, response)) {
            return;
        }
        writeJson(response, HttpServletResponse.SC_OK, gson.toJson(userDAO.getAllStudents()));
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!requireLecturer(request, response)) {
            return;
        }

        String idValue = request.getParameter("id");
        String username = request.getParameter("username");
        String fullName = request.getParameter("fullName");
        String email = request.getParameter("email");
        String githubUsername = request.getParameter("githubUsername");
        String password = request.getParameter("password");

        if (isBlank(username) || isBlank(fullName) || isBlank(email)
                || (isBlank(idValue) && isBlank(password))) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST, "Vui lòng nhập đủ thông tin bắt buộc.");
            return;
        }

        boolean saved;
        if (isBlank(idValue)) {
            saved = userDAO.createUser(username, fullName, email, githubUsername, password, "STUDENT");
        } else {
            try {
                saved = userDAO.updateStudent(Integer.parseInt(idValue), username, fullName,
                    email, githubUsername, password);
            } catch (NumberFormatException e) {
                writeError(response, HttpServletResponse.SC_BAD_REQUEST, "Mã sinh viên không hợp lệ.");
                return;
            }
        }

        if (!saved) {
            writeError(response, HttpServletResponse.SC_CONFLICT,
                    "Không thể lưu sinh viên. Kiểm tra email/username đã tồn tại và kết nối database.");
            return;
        }
        writeJson(response, HttpServletResponse.SC_OK, "{\"status\":\"success\"}");
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!requireLecturer(request, response)) {
            return;
        }

        try {
            int userId = Integer.parseInt(request.getParameter("id"));
            if (userDAO.deleteStudent(userId)) {
                writeJson(response, HttpServletResponse.SC_OK, "{\"status\":\"success\"}");
            } else {
                writeError(response, HttpServletResponse.SC_CONFLICT,
                        "Không thể xóa sinh viên; có thể dữ liệu đang được tham chiếu hoặc ID không tồn tại.");
            }
        } catch (NumberFormatException e) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST, "Mã sinh viên không hợp lệ.");
        }
    }

    private boolean requireLecturer(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Object value = request.getSession(false) == null
                ? null : request.getSession(false).getAttribute("authenticatedUser");
        if (!(value instanceof UserAccount)) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "Vui lòng đăng nhập lại.");
            return false;
        }
        if (!"LECTURER".equals(((UserAccount) value).role)) {
            writeError(response, HttpServletResponse.SC_FORBIDDEN, "Chỉ giảng viên được quản lý sinh viên.");
            return false;
        }
        return true;
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        JsonObject error = new JsonObject();
        error.addProperty("error", message);
        writeJson(response, status, gson.toJson(error));
    }

    private void writeJson(HttpServletResponse response, int status, String json) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().print(json);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}