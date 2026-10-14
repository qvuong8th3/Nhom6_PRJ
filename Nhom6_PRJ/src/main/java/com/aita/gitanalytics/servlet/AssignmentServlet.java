package com.aita.gitanalytics.servlet;

import com.aita.gitanalytics.dao.AssignmentDAO;
import com.aita.gitanalytics.dao.AssignmentDAO.StoredFile;
import com.aita.gitanalytics.dao.DataAccessException;
import com.aita.gitanalytics.model.UserAccount;
import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;

@WebServlet("/api/v1/assignments/*")
@MultipartConfig(maxFileSize = 15 * 1024 * 1024, maxRequestSize = 16 * 1024 * 1024)
public class AssignmentServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final String ALLOWED_EXTENSIONS = ".pdf,.doc,.docx,.odt,.rtf,.txt";
    private final Gson gson = new Gson();
    private final AssignmentDAO assignmentDAO = new AssignmentDAO();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        UserAccount user = requireUser(request, response);
        if (user == null) {
            return;
        }

        String[] path = pathParts(request);
        try {
            boolean lecturer = "LECTURER".equals(user.getRole());
            if (path.length == 0) {
                writeJson(response, HttpServletResponse.SC_OK,
                        assignmentDAO.listAssignments(user.getUserId(), lecturer));
                return;
            }

            int assignmentId = parseId(path[0]);
            if (path.length == 2 && "submissions".equals(path[1])) {
                if (!requireRole(user, "LECTURER", response)) {
                    return;
                }
                writeJson(response, HttpServletResponse.SC_OK,
                        assignmentDAO.listSubmissions(assignmentId, user.getUserId()));
                return;
            }
            if (path.length == 2 && "file".equals(path[1])) {
                StoredFile file = assignmentDAO.getPromptFile(assignmentId, user.getUserId(), lecturer);
                if (file == null) {
                    writeError(response, HttpServletResponse.SC_NOT_FOUND, "Không tìm thấy file đề.");
                } else {
                    writeFile(response, file);
                }
                return;
            }
            if (path.length == 4 && "submissions".equals(path[1]) && "file".equals(path[3])) {
                int studentId = parseId(path[2]);
                if (!lecturer && studentId != user.getUserId()) {
                    writeError(response, HttpServletResponse.SC_FORBIDDEN,
                            "Bạn chỉ được tải bài nộp của chính mình.");
                    return;
                }
                StoredFile file = assignmentDAO.getSubmissionFile(
                        assignmentId, studentId, user.getUserId(), lecturer);
                if (file == null) {
                    writeError(response, HttpServletResponse.SC_NOT_FOUND, "Không tìm thấy file bài nộp.");
                } else {
                    writeFile(response, file);
                }
                return;
            }
            writeError(response, HttpServletResponse.SC_NOT_FOUND, "Không tìm thấy đường dẫn.");
        } catch (IllegalArgumentException e) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (DataAccessException e) {
            writeDataAccessError(response, e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        UserAccount user = requireUser(request, response);
        if (user == null) {
            return;
        }

        String[] path = pathParts(request);
        try {
            request.setCharacterEncoding("UTF-8");
            if (path.length == 0) {
                if (!requireRole(user, "LECTURER", response)) {
                    return;
                }
                String title = request.getParameter("title");
                String description = request.getParameter("description");
                String courseIdParam = request.getParameter("courseId");
                if (isBlank(courseIdParam)) {
                    throw new IllegalArgumentException("Vui lòng chọn môn học bạn được phân công để giao bài tập.");
                }
                int courseId = parseId(courseIdParam);
                if (isBlank(title) || title.trim().length() > 160) {
                    throw new IllegalArgumentException("Tên bài tập bắt buộc và tối đa 160 ký tự.");
                }
                if (description != null && description.length() > 2000) {
                    throw new IllegalArgumentException("Mô tả tối đa 2000 ký tự.");
                }
                Part prompt = request.getPart("file");
                String fileName = null;
                byte[] fileData = null;
                if (prompt != null && prompt.getSize() > 0) {
                    fileName = validateFileName(prompt.getSubmittedFileName());
                    fileData = readPart(prompt);
                }

                int assignmentId = assignmentDAO.createAssignment(user.getUserId(), courseId, title.trim(),
                        isBlank(description) ? null : description.trim(), fileName, fileData,
                        parseDueAt(request.getParameter("dueAt")));
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("status", "success");
                result.put("assignmentId", assignmentId);
                writeJson(response, HttpServletResponse.SC_CREATED, result);
                return;
            }

            if (path.length == 2 && "deadline".equals(path[1])) {
                if (!requireRole(user, "LECTURER", response)) {
                    return;
                }
                boolean updated = assignmentDAO.updateDeadline(parseId(path[0]),
                        user.getUserId(), parseDueAt(request.getParameter("dueAt")));
                if (!updated) {
                    writeError(response, HttpServletResponse.SC_NOT_FOUND,
                            "Không tìm thấy bài tập hoặc bạn không có quyền chỉnh sửa.");
                    return;
                }
                writeJson(response, HttpServletResponse.SC_OK, Map.of("status", "success"));
                return;
            }

            if (path.length == 2 && "submissions".equals(path[1])) {
                if (!requireRole(user, "STUDENT", response)) {
                    return;
                }
                Part submission = request.getPart("file");
                if (submission == null || submission.getSize() == 0) {
                    throw new IllegalArgumentException("Vui lòng chọn file bài làm.");
                }
                String fileName = validateFileName(submission.getSubmittedFileName());
                byte[] fileData = readPart(submission);
                boolean saved = assignmentDAO.saveSubmission(
                        parseId(path[0]), user.getUserId(), fileName, fileData);
                if (!saved) {
                    writeError(response, HttpServletResponse.SC_CONFLICT,
                            "Bài tập đã hết hạn nộp hoặc không còn tồn tại.");
                    return;
                }
                writeJson(response, HttpServletResponse.SC_OK, Map.of("status", "success"));
                return;
            }

            writeError(response, HttpServletResponse.SC_NOT_FOUND, "Không tìm thấy đường dẫn.");
        } catch (IllegalArgumentException e) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            writeError(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                    "File vượt quá giới hạn 15 MB.");
        } catch (DataAccessException e) {
            writeDataAccessError(response, e);
        }
    }

    private UserAccount requireUser(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Object value = request.getSession(false) == null ? null
                : request.getSession(false).getAttribute("authenticatedUser");
        if (!(value instanceof UserAccount)) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "Vui lòng đăng nhập lại.");
            return null;
        }
        return (UserAccount) value;
    }

    private boolean requireRole(UserAccount user, String role, HttpServletResponse response)
            throws IOException {
        if (!role.equals(user.getRole())) {
            writeError(response, HttpServletResponse.SC_FORBIDDEN,
                    "Bạn không có quyền thực hiện thao tác này.");
            return false;
        }
        return true;
    }

    private String[] pathParts(HttpServletRequest request) {
        String path = request.getPathInfo();
        return path == null || "/".equals(path) ? new String[0] : path.substring(1).split("/");
    }

    private int parseId(String value) {
        try {
            int id = Integer.parseInt(value);
            if (id <= 0) {
                throw new NumberFormatException();
            }
            return id;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Mã bài tập không hợp lệ.", e);
        }
    }

    private String validateFileName(String submittedName) {
        if (isBlank(submittedName)) {
            throw new IllegalArgumentException("Tên file không hợp lệ.");
        }
        String fileName = submittedName.replace('\\', '/');
        fileName = fileName.substring(fileName.lastIndexOf('/') + 1);
        if (fileName.length() > 255 || fileName.contains("..")) {
            throw new IllegalArgumentException("Tên file không hợp lệ.");
        }
        String lowerName = fileName.toLowerCase(Locale.ROOT);
        boolean allowed = false;
        for (String extension : ALLOWED_EXTENSIONS.split(",")) {
            allowed |= lowerName.endsWith(extension);
        }
        if (!allowed) {
            throw new IllegalArgumentException("Chỉ nhận file PDF, DOC, DOCX, ODT, RTF hoặc TXT.");
        }
        return fileName;
    }

    private byte[] readPart(Part part) throws IOException {
        try (InputStream input = part.getInputStream()) {
            return input.readAllBytes();
        }
    }

    private void writeFile(HttpServletResponse response, StoredFile file) throws IOException {
        String encodedName = URLEncoder.encode(file.getFileName(), StandardCharsets.UTF_8.name())
                .replace("+", "%20");
        response.setContentType("application/octet-stream");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"submission\"; filename*=UTF-8''" + encodedName);
        response.setContentLength(file.getData().length);
        response.getOutputStream().write(file.getData());
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private Timestamp parseDueAt(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            Instant dueAt = Instant.parse(value.trim());
            if (!dueAt.isAfter(Instant.now())) {
                throw new IllegalArgumentException("Hạn nộp phải ở thời điểm tương lai.");
            }
            return Timestamp.from(dueAt);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Thời hạn nộp không hợp lệ.", e);
        }
    }

    private void writeDataAccessError(HttpServletResponse response, DataAccessException error)
            throws IOException {
        if (error.isConstraintViolation()) {
            writeError(response, HttpServletResponse.SC_CONFLICT,
                    "Bài tập không tồn tại hoặc dữ liệu nộp bị trùng.");
            return;
        }
        error.printStackTrace();
        writeError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                "Không thể truy cập database lúc này.");
    }

    private void writeError(HttpServletResponse response, int status, String message)
            throws IOException {
        writeJson(response, status, Map.of("error", message));
    }

    private void writeJson(HttpServletResponse response, int status, Object value)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().print(gson.toJson(value));
    }
}
