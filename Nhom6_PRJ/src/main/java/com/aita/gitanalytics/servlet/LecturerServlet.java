package com.aita.gitanalytics.servlet;

import com.aita.gitanalytics.dao.CourseDAO;
import com.aita.gitanalytics.dao.DataAccessException;
import com.aita.gitanalytics.dao.UserDAO;
import com.aita.gitanalytics.model.UserAccount;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.Map;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet("/api/v1/admin/lecturers")
public class LecturerServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final Gson gson = new Gson();
    private final CourseDAO courseDAO = new CourseDAO();
    private final UserDAO userDAO = new UserDAO();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!requireAdministrator(request, response)) {
            return;
        }
        try {
            writeJson(response, HttpServletResponse.SC_OK, courseDAO.listLecturers());
        } catch (DataAccessException e) {
            writeDataAccessError(response, e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        request.setCharacterEncoding("UTF-8");
        if (!requireAdministrator(request, response)) {
            return;
        }
        try {
            boolean created = userDAO.createLecturer(
                    request.getParameter("username"),
                    request.getParameter("fullName"),
                    request.getParameter("email"),
                    request.getParameter("initialPassword"));
            if (!created) {
                writeError(response, HttpServletResponse.SC_CONFLICT,
                        "Không thể tạo tài khoản giảng viên.");
                return;
            }
            writeJson(response, HttpServletResponse.SC_CREATED, Map.of("status", "success"));
        } catch (IllegalArgumentException e) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (DataAccessException e) {
            writeDataAccessError(response, e);
        }
    }

    private boolean requireAdministrator(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Object value = request.getSession(false) == null
                ? null : request.getSession(false).getAttribute("authenticatedUser");
        if (!(value instanceof UserAccount)) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "Vui lòng đăng nhập lại.");
            return false;
        }
        if (!"ADMIN".equals(((UserAccount) value).getRole())) {
            writeError(response, HttpServletResponse.SC_FORBIDDEN,
                    "Chỉ quản trị viên được cấp tài khoản giảng viên.");
            return false;
        }
        return true;
    }

    private void writeDataAccessError(HttpServletResponse response, DataAccessException error)
            throws IOException {
        if (error.isConstraintViolation()) {
            writeError(response, HttpServletResponse.SC_CONFLICT,
                    "Tên đăng nhập hoặc email đã được sử dụng.");
        } else {
            error.printStackTrace();
            writeError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Không thể tạo tài khoản giảng viên lúc này.");
        }
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        JsonObject error = new JsonObject();
        error.addProperty("error", message);
        writeJson(response, status, error);
    }

    private void writeJson(HttpServletResponse response, int status, Object value) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().print(gson.toJson(value));
    }
}
