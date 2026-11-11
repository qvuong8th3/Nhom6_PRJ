package com.aita.gitanalytics.dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import com.aita.gitanalytics.util.Sha256ChecksumUtil;

public class AssignmentDAO {

    public int createAssignment(int lecturerId, int courseId, String title, String description,
                                String fileName, byte[] fileData, Timestamp dueAt) {
        return createAssignment(lecturerId, "LECTURER", courseId, title, description, fileName, fileData, dueAt);
    }

    public int createAssignment(int userId, String role, int courseId, String title, String description,
                                String fileName, byte[] fileData, Timestamp dueAt) {
        String sql = "INSERT INTO Essay_Assignments "
                + "(title, description, created_by, course_id, prompt_file_name, prompt_file_data, due_at, due_date) "
                + "OUTPUT INSERTED.assignment_id VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = requireConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (!"ADMIN".equalsIgnoreCase(role)) {
                validateLecturerCourse(connection, userId, courseId);
            }
            statement.setString(1, title);
            statement.setString(2, description);
            statement.setInt(3, userId);
            statement.setInt(4, courseId);
            statement.setString(5, fileName);
            statement.setBytes(6, fileData);
            if (dueAt == null) {
                statement.setNull(7, Types.TIMESTAMP);
                statement.setNull(8, Types.TIMESTAMP);
            } else {
                statement.setTimestamp(7, dueAt, utcCalendar());
                statement.setTimestamp(8, dueAt, utcCalendar());
            }
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("Assignment insert did not return an ID.");
                }
                return result.getInt(1);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not save assignment.", e);
        }
    }

    public List<Map<String, Object>> listAssignments(int userId, boolean lecturer) {
        String sql = "SELECT a.assignment_id, a.course_id, a.title, a.description, a.created_at, a.due_at, "
                + "a.prompt_file_name, "
                + "(SELECT COUNT(*) FROM Essay_Submissions s "
                + "WHERE s.assignment_id = a.assignment_id) AS submission_count, "
                + "s.submitted_at, s.score, s.feedback, c.course_name, c.course_code "
                + "FROM Essay_Assignments a "
                + "LEFT JOIN Essay_Submissions s ON s.assignment_id = a.assignment_id "
                + "AND s.student_id = ? "
                + "LEFT JOIN Courses c ON c.course_id = a.course_id "
                + (lecturer ? "WHERE a.course_id IS NOT NULL AND EXISTS ("
                        + "SELECT 1 FROM Lecturer_Courses lc WHERE lc.course_id = a.course_id "
                        + "AND lc.lecturer_id = ?) " : "WHERE a.course_id IS NOT NULL AND EXISTS ("
                        + "SELECT 1 FROM Course_Classes cl JOIN Class_Students cs "
                        + "ON cs.class_id = cl.class_id WHERE cl.course_id = a.course_id "
                        + "AND cs.student_id = ?) ")
                + "ORDER BY a.created_at DESC, a.assignment_id DESC";
        List<Map<String, Object>> assignments = new ArrayList<>();
        try (Connection connection = requireConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            statement.setInt(2, userId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    Map<String, Object> assignment = new LinkedHashMap<>();
                    assignment.put("assignmentId", result.getInt("assignment_id"));
                    assignment.put("courseId", result.getInt("course_id"));
                    assignment.put("title", result.getString("title"));
                    assignment.put("description", result.getString("description"));
                    assignment.put("courseName", result.getString("course_name"));
                    assignment.put("courseCode", result.getString("course_code"));
                    assignment.put("createdAt", result.getTimestamp("created_at").toString());
                    Timestamp dueAt = result.getTimestamp("due_at", utcCalendar());
                    assignment.put("dueAt", dueAt == null ? null : dueAt.toInstant().toString());
                    assignment.put("promptFileName", result.getString("prompt_file_name"));
                    assignment.put("submissionCount", result.getInt("submission_count"));
                    assignment.put("submittedAt", result.getTimestamp("submitted_at") == null
                            ? null : result.getTimestamp("submitted_at").toString());
                    assignment.put("score", result.getBigDecimal("score"));
                    assignment.put("feedback", result.getString("feedback"));
                    assignments.add(assignment);
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not load assignments.", e);
        }
        return assignments;
    }

    public List<Map<String, Object>> listSubmissions(int assignmentId, int lecturerId) {
        String sql = "SELECT s.student_id, u.full_name, u.username, u.email, "
                + "s.file_name, s.submitted_at, s.score, s.feedback, s.graded_at "
                + "FROM Essay_Submissions s "
                + "JOIN Users u ON u.user_id = s.student_id "
                + "JOIN Essay_Assignments a ON a.assignment_id = s.assignment_id "
                + "WHERE s.assignment_id = ? AND EXISTS ("
                + "SELECT 1 FROM Lecturer_Courses lc WHERE lc.course_id = a.course_id "
                + "AND lc.lecturer_id = ?) "
                + "ORDER BY s.submitted_at DESC";
        List<Map<String, Object>> submissions = new ArrayList<>();
        try (Connection connection = requireConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, assignmentId);
            statement.setInt(2, lecturerId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    Map<String, Object> submission = new LinkedHashMap<>();
                    submission.put("studentId", result.getInt("student_id"));
                    submission.put("fullName", result.getString("full_name"));
                    submission.put("username", result.getString("username"));
                    submission.put("email", result.getString("email"));
                    submission.put("fileName", result.getString("file_name"));
                    submission.put("submittedAt", result.getTimestamp("submitted_at").toString());
                    submission.put("score", result.getBigDecimal("score"));
                    submission.put("feedback", result.getString("feedback"));
                    submission.put("gradedAt", result.getTimestamp("graded_at") == null
                            ? null : result.getTimestamp("graded_at").toString());
                    submissions.add(submission);
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not load assignment submissions.", e);
        }
        return submissions;
    }

    public boolean gradeSubmission(int assignmentId, int studentId, int graderId, BigDecimal score, String feedback) {
        String sql = "UPDATE s SET score = ?, grade = ?, feedback = ?, graded_at = SYSUTCDATETIME(), graded_by = ? "
                + "FROM Essay_Submissions s "
                + "JOIN Essay_Assignments a ON a.assignment_id = s.assignment_id "
                + "WHERE s.assignment_id = ? AND s.student_id = ? AND (EXISTS ("
                + "SELECT 1 FROM Lecturer_Courses lc WHERE lc.course_id = a.course_id "
                + "AND lc.lecturer_id = ?) OR EXISTS ("
                + "SELECT 1 FROM Users u WHERE u.user_id = ? AND u.role = 'ADMIN'))";
        try (Connection connection = requireConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (score == null) {
                statement.setNull(1, Types.DECIMAL);
                statement.setNull(2, Types.DECIMAL);
            } else {
                statement.setBigDecimal(1, score);
                statement.setBigDecimal(2, score);
            }
            statement.setString(3, feedback == null || feedback.trim().isEmpty() ? null : feedback.trim());
            statement.setInt(4, graderId);
            statement.setInt(5, assignmentId);
            statement.setInt(6, studentId);
            statement.setInt(7, graderId);
            statement.setInt(8, graderId);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DataAccessException("Could not grade assignment submission.", e);
        }
    }

    public boolean saveAutoGradeResult(int assignmentId, int studentId, BigDecimal score, String feedback) {
        String sql = "UPDATE Essay_Submissions SET score = ?, grade = ?, feedback = ?, "
                + "graded_at = SYSUTCDATETIME(), graded_by = NULL "
                + "WHERE assignment_id = ? AND student_id = ?";
        try (Connection connection = requireConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (score == null) {
                statement.setNull(1, Types.DECIMAL);
                statement.setNull(2, Types.DECIMAL);
            } else {
                statement.setBigDecimal(1, score);
                statement.setBigDecimal(2, score);
            }
            statement.setString(3, feedback == null || feedback.trim().isEmpty() ? null : feedback.trim());
            statement.setInt(4, assignmentId);
            statement.setInt(5, studentId);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DataAccessException("Could not save auto-grade result.", e);
        }
    }

    public List<Map<String, Object>> listSandboxJobs() {
        String sql = "SELECT s.submission_id, s.assignment_id, s.student_id, u.full_name, u.username, "
                + "s.file_name, s.file_size_bytes, s.submitted_at, s.score, s.grade, s.feedback, s.sha256_hash, "
                + "a.title AS assignment_title "
                + "FROM Essay_Submissions s "
                + "JOIN Users u ON u.user_id = s.student_id "
                + "JOIN Essay_Assignments a ON a.assignment_id = s.assignment_id "
                + "ORDER BY s.submitted_at DESC, s.submission_id DESC";
        List<Map<String, Object>> jobs = new ArrayList<>();
        try (Connection connection = requireConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> job = new LinkedHashMap<>();
                job.put("jobId", "JOB-" + (8800 + rs.getInt("submission_id")));
                job.put("submissionId", rs.getInt("submission_id"));
                job.put("assignmentId", rs.getInt("assignment_id"));
                job.put("studentId", rs.getInt("student_id"));
                job.put("studentName", rs.getString("full_name"));
                job.put("studentCode", rs.getString("username"));
                job.put("assignmentTitle", rs.getString("assignment_title"));
                job.put("fileName", rs.getString("file_name"));
                job.put("fileSizeBytes", rs.getLong("file_size_bytes"));
                BigDecimal s = rs.getBigDecimal("score");
                if (s == null) s = rs.getBigDecimal("grade");
                job.put("score", s != null ? s.doubleValue() : 0.0);
                job.put("feedback", rs.getString("feedback"));
                job.put("sha256", rs.getString("sha256_hash"));
                job.put("submittedAt", rs.getTimestamp("submitted_at") != null ? rs.getTimestamp("submitted_at").toString() : null);
                jobs.add(job);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return jobs;
    }

    public boolean updateDeadline(int assignmentId, int lecturerId, Timestamp dueAt) {
        String sql = "UPDATE a SET due_at = ? FROM Essay_Assignments a "
                + "WHERE a.assignment_id = ? AND EXISTS ("
                + "SELECT 1 FROM Lecturer_Courses lc WHERE lc.course_id = a.course_id "
                + "AND lc.lecturer_id = ?)";
        try (Connection connection = requireConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (dueAt == null) {
                statement.setNull(1, Types.TIMESTAMP);
            } else {
                statement.setTimestamp(1, dueAt, utcCalendar());
            }
            statement.setInt(2, assignmentId);
            statement.setInt(3, lecturerId);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DataAccessException("Could not update assignment deadline.", e);
        }
    }

    public boolean deleteAssignment(int assignmentId, int userId, boolean isAdmin) {
        String deleteSubmissionsSql = "DELETE FROM Essay_Submissions WHERE assignment_id = ?";
        String deleteAssignmentSql;
        if (isAdmin) {
            deleteAssignmentSql = "DELETE FROM Essay_Assignments WHERE assignment_id = ?";
        } else {
            deleteAssignmentSql = "DELETE FROM Essay_Assignments WHERE assignment_id = ? AND "
                    + "(created_by = ? OR EXISTS (SELECT 1 FROM Lecturer_Courses lc WHERE lc.course_id = Essay_Assignments.course_id AND lc.lecturer_id = ?))";
        }
        
        try (Connection connection = requireConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement stmtSub = connection.prepareStatement(deleteSubmissionsSql)) {
                    stmtSub.setInt(1, assignmentId);
                    stmtSub.executeUpdate();
                }
                
                boolean deleted;
                try (PreparedStatement stmt = connection.prepareStatement(deleteAssignmentSql)) {
                    stmt.setInt(1, assignmentId);
                    if (!isAdmin) {
                        stmt.setInt(2, userId);
                        stmt.setInt(3, userId);
                    }
                    deleted = stmt.executeUpdate() > 0;
                }
                connection.commit();
                return deleted;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not delete assignment.", e);
        }
    }

    public StoredFile getPromptFile(int assignmentId, int userId, boolean lecturer) {
        String sql = "SELECT a.prompt_file_name, a.prompt_file_data FROM Essay_Assignments a "
                + "WHERE a.assignment_id = ? AND a.prompt_file_data IS NOT NULL AND "
                + (lecturer ? "EXISTS (SELECT 1 FROM Lecturer_Courses lc "
                        + "WHERE lc.course_id = a.course_id AND lc.lecturer_id = ?)"
                        : "EXISTS (SELECT 1 FROM Course_Classes cl JOIN Class_Students cs "
                                + "ON cs.class_id = cl.class_id WHERE cl.course_id = a.course_id "
                                + "AND cs.student_id = ?)");
        try (Connection connection = requireConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, assignmentId);
            statement.setInt(2, userId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next()
                        ? new StoredFile(result.getString("prompt_file_name"), result.getBytes("prompt_file_data"))
                        : null;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not load assignment prompt file.", e);
        }
    }

    public StoredFile getSubmissionFile(int assignmentId, int studentId, int userId, boolean lecturer) {
        String sql = "SELECT s.file_name, s.file_data FROM Essay_Submissions s "
                + "JOIN Essay_Assignments a ON a.assignment_id = s.assignment_id "
                + "WHERE s.assignment_id = ? AND s.student_id = ? AND "
                + (lecturer ? "EXISTS (SELECT 1 FROM Lecturer_Courses lc "
                        + "WHERE lc.course_id = a.course_id AND lc.lecturer_id = ?)"
                        : "s.student_id = ? AND EXISTS (SELECT 1 FROM Course_Classes cl "
                                + "JOIN Class_Students cs ON cs.class_id = cl.class_id "
                                + "WHERE cl.course_id = a.course_id AND cs.student_id = ?)");
        try (Connection connection = requireConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, assignmentId);
            statement.setInt(2, studentId);
            statement.setInt(3, userId);
            if (!lecturer) {
                statement.setInt(4, userId);
            }
            try (ResultSet result = statement.executeQuery()) {
                return result.next()
                        ? new StoredFile(result.getString("file_name"), result.getBytes("file_data"))
                        : null;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not load submitted assignment file.", e);
        }
    }

    public boolean saveSubmission(int assignmentId, int studentId, String fileName, byte[] fileData) {
        long fileSize = fileData != null ? fileData.length : 0;
        String sha256 = fileData != null ? Sha256ChecksumUtil.hash(fileData) : null;
        String updateSql = "UPDATE Essay_Submissions SET file_name = ?, file_data = ?, "
                + "file_size_bytes = ?, sha256_hash = ?, submitted_at = SYSUTCDATETIME() WHERE assignment_id = ? AND student_id = ?";
        String insertSql = "INSERT INTO Essay_Submissions "
                + "(assignment_id, student_id, file_name, file_data, file_size_bytes, sha256_hash) VALUES (?, ?, ?, ?, ?, ?)";
        String deadlineSql = "SELECT CASE WHEN (a.due_at IS NULL AND a.due_date IS NULL) "
                + "OR ISNULL(a.due_at, a.due_date) > SYSUTCDATETIME() "
                + "THEN 1 ELSE 0 END FROM Essay_Assignments a WITH (UPDLOCK, HOLDLOCK) "
                + "WHERE a.assignment_id = ? AND a.course_id IS NOT NULL AND EXISTS ("
                + "SELECT 1 FROM Course_Classes cl JOIN Class_Students cs "
                + "ON cs.class_id = cl.class_id WHERE cl.course_id = a.course_id "
                + "AND cs.student_id = ?)";
        try (Connection connection = requireConnection()) {
            connection.setAutoCommit(false);
            try {
                boolean submissionOpen;
                try (PreparedStatement deadline = connection.prepareStatement(deadlineSql)) {
                    deadline.setInt(1, assignmentId);
                    deadline.setInt(2, studentId);
                    try (ResultSet result = deadline.executeQuery()) {
                        submissionOpen = result.next() && result.getBoolean(1);
                    }
                }
                if (!submissionOpen) {
                    connection.rollback();
                    return false;
                }
                int updated;
                try (PreparedStatement update = connection.prepareStatement(updateSql)) {
                    update.setString(1, fileName);
                    update.setBytes(2, fileData);
                    update.setLong(3, fileSize);
                    update.setString(4, sha256);
                    update.setInt(5, assignmentId);
                    update.setInt(6, studentId);
                    updated = update.executeUpdate();
                }
                if (updated == 0) {
                    try (PreparedStatement insert = connection.prepareStatement(insertSql)) {
                        insert.setInt(1, assignmentId);
                        insert.setInt(2, studentId);
                        insert.setString(3, fileName);
                        insert.setBytes(4, fileData);
                        insert.setLong(5, fileSize);
                        insert.setString(6, sha256);
                        insert.executeUpdate();
                    }
                }
                connection.commit();
                return true;
            } catch (SQLException e) {
                rollback(connection, e);
                throw new DataAccessException("Could not save assignment submission.", e);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not save assignment submission.", e);
        }
    }

    private void validateLecturerCourse(Connection connection, int lecturerId, int courseId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM Lecturer_Courses WHERE lecturer_id = ? AND course_id = ?")) {
            statement.setInt(1, lecturerId);
            statement.setInt(2, courseId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalArgumentException("Bạn chưa được phân công giảng dạy môn học này.");
                }
            }
        }
    }

    private Connection requireConnection() throws SQLException {
        Connection connection = DBContext.getConnection();
        if (connection == null) {
            throw new DataAccessException("Database connection is unavailable.", null);
        }
        return connection;
    }

    private Calendar utcCalendar() {
        return new GregorianCalendar(TimeZone.getTimeZone("UTC"));
    }

    private void rollback(Connection connection, Exception cause) {
        try {
            connection.rollback();
        } catch (SQLException rollbackError) {
            cause.addSuppressed(rollbackError);
        }
    }

    public static final class StoredFile {
        private final String fileName;
        private final byte[] data;

        private StoredFile(String fileName, byte[] data) {
            this.fileName = fileName;
            this.data = data;
        }

        public String getFileName() {
            return fileName;
        }

        public byte[] getData() {
            return data;
        }
    }
}
