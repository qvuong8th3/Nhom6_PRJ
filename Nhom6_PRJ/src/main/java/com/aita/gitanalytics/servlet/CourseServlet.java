package com.aita.gitanalytics.servlet;

import com.aita.gitanalytics.dao.CourseDAO;
import com.aita.gitanalytics.dao.DataAccessException;
import com.aita.gitanalytics.model.UserAccount;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet("/api/v1/courses/*")
public class CourseServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final Gson gson = new Gson();
    private final CourseDAO courseDAO = new CourseDAO();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        UserAccount user = requireUser(request, response);
        if (user == null) {
            return;
        }
        String path = request.getPathInfo();
        if ("/students".equals(path)) {
            if (!requireAdministrator(user, response)) {
                return;
            }
            try {
                writeJson(response, HttpServletResponse.SC_OK, courseDAO.listStudents());
            } catch (DataAccessException e) {
                writeDataAccessError(response, e);
            }
            return;
        }
        if ("/classes".equals(path)) {
            if (!"LECTURER".equals(user.getRole()) && !"ADMIN".equals(user.getRole())) {
                writeError(response, HttpServletResponse.SC_FORBIDDEN,
                        "Chỉ giảng viên hoặc quản trị viên được xem lớp học.");
                return;
            }
            try {
                int courseId = parseId(request.getParameter("courseId"));
                writeJson(response, HttpServletResponse.SC_OK,
                        courseDAO.listClasses(courseId, user.getUserId(), "ADMIN".equals(user.getRole())));
            } catch (IllegalArgumentException e) {
                writeError(response, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
            } catch (DataAccessException e) {
                writeDataAccessError(response, e);
            }
            return;
        }
        if ("/lecturers".equals(path)) {
            if (!requireAdministrator(user, response)) {
                return;
            }
            try {
                writeJson(response, HttpServletResponse.SC_OK, courseDAO.listLecturers());
            } catch (DataAccessException e) {
                writeDataAccessError(response, e);
            }
            return;
        }
        if (path != null && !path.isEmpty() && !"/".equals(path)) {
            writeError(response, HttpServletResponse.SC_NOT_FOUND, "Không tìm thấy đường dẫn.");
            return;
        }
        if (!"LECTURER".equals(user.getRole()) && !"ADMIN".equals(user.getRole())) {
            writeError(response, HttpServletResponse.SC_FORBIDDEN,
                    "Chỉ giảng viên hoặc quản trị viên được xem môn học.");
            return;
        }
        try {
            writeJson(response, HttpServletResponse.SC_OK,
                    courseDAO.listCourses(user.getUserId(), "ADMIN".equals(user.getRole())));
        } catch (DataAccessException e) {
            writeDataAccessError(response, e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        UserAccount user = requireUser(request, response);
        if (user == null || !requireAdministrator(user, response)) {
            return;
        }
        request.setCharacterEncoding("UTF-8");
        try {
            String courseCode = request.getParameter("courseCode");
            String courseName = request.getParameter("courseName");
            String semester = request.getParameter("semester");
            String path = request.getPathInfo();
            if ("/classes".equals(path)) {
                int courseId = parseId(request.getParameter("courseId"));
                String className = request.getParameter("className");
                if (isBlank(className) || className.trim().length() > 100) {
                    throw new IllegalArgumentException("Tên lớp bắt buộc và tối đa 100 ký tự.");
                }
                int classId = courseDAO.createClass(courseId, className,
                        parseIds(request.getParameterValues("studentIds")));
                JsonObject result = successResult();
                result.addProperty("classId", classId);
                writeJson(response, HttpServletResponse.SC_CREATED, result);
                return;
            }
            if (path != null && path.matches("/classes/[1-9][0-9]*/students")) {
                int classId = parseId(path.split("/")[2]);
                courseDAO.replaceClassStudents(classId,
                        parseIds(request.getParameterValues("studentIds")));
                writeJson(response, HttpServletResponse.SC_OK, successResult());
                return;
            }
            if (isBlank(courseCode) || courseCode.trim().length() > 30) {
                throw new IllegalArgumentException("Mã môn học bắt buộc và tối đa 30 ký tự.");
            }
            if (isBlank(courseName) || courseName.trim().length() > 150) {
                throw new IllegalArgumentException("Tên môn học bắt buộc và tối đa 150 ký tự.");
            }
            if (semester != null && semester.trim().length() > 50) {
                throw new IllegalArgumentException("Học kỳ tối đa 50 ký tự.");
            }

            String idValue = request.getParameter("id");
            Integer courseId = isBlank(idValue) ? null : parseId(idValue);
            List<Integer> lecturerIds = parseLecturerIds(request.getParameterValues("lecturerIds"));
            courseDAO.saveCourse(courseId, courseCode, courseName, semester, lecturerIds);
            writeJson(response, courseId == null
                    ? HttpServletResponse.SC_CREATED : HttpServletResponse.SC_OK,
                    successResult());
        } catch (IllegalArgumentException e) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (DataAccessException e) {
            writeDataAccessError(response, e);
        }
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {
        UserAccount user = requireUser(request, response);
        if (user == null || !requireAdministrator(user, response)) {
            return;
        }
        try {
            String path = request.getPathInfo();
            if (path != null && path.matches("/classes/[1-9][0-9]*")) {
                if (!courseDAO.deleteClass(parseId(path.substring("/classes/".length())))) {
                    writeError(response, HttpServletResponse.SC_NOT_FOUND, "Không tìm thấy lớp học.");
                    return;
                }
                writeJson(response, HttpServletResponse.SC_OK, successResult());
                return;
            }
            if (path == null || !path.matches("/[1-9][0-9]*")) {
                throw new IllegalArgumentException("ID môn học không hợp lệ.");
            }
            if (!courseDAO.deleteCourse(parseId(path.substring(1)))) {
                writeError(response, HttpServletResponse.SC_NOT_FOUND, "Không tìm thấy môn học.");
                return;
            }
            writeJson(response, HttpServletResponse.SC_OK, successResult());
        } catch (IllegalArgumentException e) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (DataAccessException e) {
            writeDataAccessError(response, e);
        }
    }

    private UserAccount requireUser(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Object value = request.getSession(false) == null
                ? null : request.getSession(false).getAttribute("authenticatedUser");
        if (!(value instanceof UserAccount)) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "Vui lòng đăng nhập lại.");
            return null;
        }
        return (UserAccount) value;
    }

    private boolean requireAdministrator(UserAccount user, HttpServletResponse response) throws IOException {
        if (!"ADMIN".equals(user.getRole())) {
            writeError(response, HttpServletResponse.SC_FORBIDDEN,
                    "Chỉ quản trị viên được quản lý môn học.");
            return false;
        }
        return true;
    }

    private List<Integer> parseLecturerIds(String[] values) {
        return parseIds(values);
    }

    private List<Integer> parseIds(String[] values) {
        List<Integer> ids = new ArrayList<>();
        if (values != null) {
            for (String value : values) {
                ids.add(parseId(value));
            }
        }
        return ids;
    }

    private int parseId(String value) {
        try {
            int id = Integer.parseInt(value);
            if (id <= 0) {
                throw new NumberFormatException("ID must be positive.");
            }
            return id;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("ID không hợp lệ.");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private void writeDataAccessError(HttpServletResponse response, DataAccessException error)
            throws IOException {
        if (error.isConstraintViolation()) {
            writeError(response, HttpServletResponse.SC_CONFLICT,
                    "Mã môn học hoặc tên lớp đã tồn tại, hoặc dữ liệu đang được tham chiếu.");
        } else {
            error.printStackTrace();
            writeError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Không thể truy cập database lúc này.");
        }
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        JsonObject error = new JsonObject();
        error.addProperty("error", message);
        writeJson(response, status, error);
    }

    private JsonObject successResult() {
        JsonObject result = new JsonObject();
        result.addProperty("status", "success");
        return result;
    }

    private void writeJson(HttpServletResponse response, int status, Object value) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().print(gson.toJson(value));
    }
}
