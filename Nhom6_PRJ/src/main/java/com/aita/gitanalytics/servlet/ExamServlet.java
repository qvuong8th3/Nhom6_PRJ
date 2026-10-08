package com.aita.gitanalytics.servlet;

import com.aita.gitanalytics.dao.DataAccessException;
import com.aita.gitanalytics.dao.ExamDAO;
import com.aita.gitanalytics.model.UserAccount;
import com.aita.gitanalytics.service.ExamService;
import com.aita.gitanalytics.service.ExamService.QuestionDraft;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;

@WebServlet("/api/v1/exams/*")
@MultipartConfig(maxFileSize = 5 * 1024 * 1024, maxRequestSize = 6 * 1024 * 1024)
public class ExamServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final Gson gson = new Gson();
    private final ExamDAO examDAO = new ExamDAO();
    private final ExamService examService = new ExamService();

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
                        examDAO.listExams(user.getUserId(), lecturer));
                return;
            }
            int examId = parseId(path[0]);
            if (path.length == 2 && "results".equals(path[1])) {
                if (!lecturer) {
                    writeError(response, HttpServletResponse.SC_FORBIDDEN,
                            "Chỉ giảng viên được xem kết quả.");
                    return;
                }
                Map<String, Object> results = examDAO.getResults(examId, user.getUserId());
                if (results == null) {
                    writeError(response, HttpServletResponse.SC_NOT_FOUND, "Không tìm thấy đề thi.");
                } else {
                    writeJson(response, HttpServletResponse.SC_OK, results);
                }
                return;
            }
            if (path.length != 1) {
                writeError(response, HttpServletResponse.SC_NOT_FOUND, "Không tìm thấy đường dẫn.");
                return;
            }
            Map<String, Object> exam = examDAO.getExam(examId, user.getUserId(), lecturer);
            if (exam == null) {
                writeError(response, HttpServletResponse.SC_NOT_FOUND, "Không tìm thấy đề thi.");
            } else {
                writeJson(response, HttpServletResponse.SC_OK, exam);
            }
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
            if (path.length == 0) {
                if (!requireRole(user, "LECTURER", response)) {
                    return;
                }
                request.setCharacterEncoding("UTF-8");
                String title = request.getParameter("title");
                String description = request.getParameter("description");
                Part file = request.getPart("file");
                if (isBlank(title) || title.trim().length() > 160) {
                    throw new IllegalArgumentException("Tên đề thi bắt buộc và tối đa 160 ký tự.");
                }
                if (file == null || file.getSize() == 0) {
                    throw new IllegalArgumentException("Vui lòng chọn file CSV hoặc XLSX.");
                }
                if (description != null && description.length() > 2000) {
                    throw new IllegalArgumentException("Mô tả tối đa 2000 ký tự.");
                }
                List<QuestionDraft> questions = examService.parseSpreadsheet(
                        file.getSubmittedFileName(), file.getInputStream());
                int examId = examDAO.createExam(user.getUserId(), title.trim(),
                        isBlank(description) ? null : description.trim(), questions);
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("status", "success");
                result.put("examId", examId);
                writeJson(response, HttpServletResponse.SC_CREATED, result);
                return;
            }
            if (path.length == 2 && "submit".equals(path[1])) {
                if (!requireRole(user, "STUDENT", response)) {
                    return;
                }
                Map<Integer, String> answers = parseAnswers(request);
                Map<String, Object> result = examDAO.submit(parseId(path[0]), user.getUserId(), answers);
                writeJson(response, HttpServletResponse.SC_OK, result);
                return;
            }
            writeError(response, HttpServletResponse.SC_NOT_FOUND, "Không tìm thấy đường dẫn.");
        } catch (IllegalArgumentException e) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            writeError(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                    "File tải lên vượt quá giới hạn 5 MB.");
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
        if (path == null || "/".equals(path)) {
            return new String[0];
        }
        return path.substring(1).split("/");
    }

    private int parseId(String value) {
        try {
            int id = Integer.parseInt(value);
            if (id <= 0) {
                throw new NumberFormatException();
            }
            return id;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Mã đề thi không hợp lệ.", e);
        }
    }

    private Map<Integer, String> parseAnswers(HttpServletRequest request) throws IOException {
        JsonObject body = JsonParser.parseReader(request.getReader()).getAsJsonObject();
        JsonObject answerValues = body.getAsJsonObject("answers");
        if (answerValues == null) {
            throw new IllegalArgumentException("Dữ liệu bài làm không hợp lệ.");
        }
        Map<Integer, String> answers = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : answerValues.entrySet()) {
            int questionId;
            try {
                questionId = Integer.parseInt(entry.getKey());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Mã câu hỏi không hợp lệ.", e);
            }
            answers.put(questionId, entry.getValue().isJsonNull()
                    ? null : entry.getValue().getAsString());
        }
        return answers;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private void writeDataAccessError(HttpServletResponse response, DataAccessException error)
            throws IOException {
        if (error.isConstraintViolation()) {
            writeError(response, HttpServletResponse.SC_CONFLICT,
                    "Bạn đã nộp đề này hoặc dữ liệu vi phạm ràng buộc.");
        } else {
            writeError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Không thể truy cập database lúc này.");
        }
    }

    private void writeError(HttpServletResponse response, int status, String message)
            throws IOException {
        Map<String, String> error = new LinkedHashMap<>();
        error.put("error", message);
        writeJson(response, status, error);
    }

    private void writeJson(HttpServletResponse response, int status, Object value)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().print(gson.toJson(value));
    }
}