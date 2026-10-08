package com.aita.gitanalytics.servlet;

import com.aita.gitanalytics.dao.DataAccessException;
import com.aita.gitanalytics.model.UserAccount;
import com.aita.gitanalytics.service.StudentService;
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
    private final StudentService studentService = new StudentService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!requireLecturer(request, response)) {
            return;
        }
        try {
            writeJson(response, HttpServletResponse.SC_OK, gson.toJson(studentService.getAllStudents()));
        } catch (DataAccessException e) {
            writeDataAccessError(response, e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        request.setCharacterEncoding("UTF-8");
        if (!requireLecturer(request, response)) {
            return;
        }

        try {
            boolean saved = studentService.saveStudent(
                    request.getParameter("id"),
                    request.getParameter("username"),
                    request.getParameter("fullName"),
                    request.getParameter("email"),
                    request.getParameter("githubUsername"),
                    request.getParameter("password"));
            if (!saved) {
                writeError(response, HttpServletResponse.SC_CONFLICT,
                        "Không thể lưu sinh viên; username/email có thể đã tồn tại hoặc ID không còn hợp lệ.");
                return;
            }
            writeJson(response, HttpServletResponse.SC_OK, "{\"status\":\"success\"}");
        } catch (IllegalArgumentException e) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (DataAccessException e) {
            writeDataAccessError(response, e);
        }
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!requireLecturer(request, response)) {
            return;
        }

        try {
            if (studentService.deleteStudent(request.getParameter("id"))) {
                writeJson(response, HttpServletResponse.SC_OK, "{\"status\":\"success\"}");
            } else {
                writeError(response, HttpServletResponse.SC_CONFLICT,
                        "Không thể xóa sinh viên; có thể dữ liệu đang được tham chiếu hoặc ID không tồn tại.");
            }
        } catch (IllegalArgumentException e) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (DataAccessException e) {
            writeDataAccessError(response, e);
        }
    }

    private boolean requireLecturer(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Object value = request.getSession(false) == null
                ? null : request.getSession(false).getAttribute("authenticatedUser");
        if (!(value instanceof UserAccount)) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "Vui lòng đăng nhập lại.");
            return false;
        }
        if (!"LECTURER".equals(((UserAccount) value).getRole())) {
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

    private void writeDataAccessError(HttpServletResponse response, DataAccessException error)
            throws IOException {
        if (error.isConstraintViolation()) {
            writeError(response, HttpServletResponse.SC_CONFLICT,
                    "Dữ liệu bị trùng hoặc vi phạm ràng buộc trong database.");
        } else {
            writeError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Không thể truy cập database lúc này.");
        }
    }
}