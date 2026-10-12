package com.aita.gitanalytics.dao;

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

public class AssignmentDAO {

    public int createAssignment(int lecturerId, String title, String description,
                                String fileName, byte[] fileData, Timestamp dueAt) {
        String sql = "INSERT INTO Essay_Assignments "
                + "(title, description, created_by, prompt_file_name, prompt_file_data, due_at) "
                + "OUTPUT INSERTED.assignment_id VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection connection = requireConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, title);
            statement.setString(2, description);
            statement.setInt(3, lecturerId);
            statement.setString(4, fileName);
            statement.setBytes(5, fileData);
            if (dueAt == null) {
                statement.setNull(6, Types.TIMESTAMP);
            } else {
                statement.setTimestamp(6, dueAt, utcCalendar());
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
        String sql = "SELECT a.assignment_id, a.title, a.description, a.created_at, a.due_at, "
                + "a.prompt_file_name, "
                + "(SELECT COUNT(*) FROM Essay_Submissions s "
                + "WHERE s.assignment_id = a.assignment_id) AS submission_count, "
                + "s.submitted_at "
                + "FROM Essay_Assignments a "
                + "LEFT JOIN Essay_Submissions s ON s.assignment_id = a.assignment_id "
                + "AND s.student_id = ? "
                + (lecturer ? "WHERE a.created_by = ? " : "")
                + "ORDER BY a.created_at DESC, a.assignment_id DESC";
        List<Map<String, Object>> assignments = new ArrayList<>();
        try (Connection connection = requireConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            if (lecturer) {
                statement.setInt(2, userId);
            }
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    Map<String, Object> assignment = new LinkedHashMap<>();
                    assignment.put("assignmentId", result.getInt("assignment_id"));
                    assignment.put("title", result.getString("title"));
                    assignment.put("description", result.getString("description"));
                    assignment.put("createdAt", result.getTimestamp("created_at").toString());
                    Timestamp dueAt = result.getTimestamp("due_at", utcCalendar());
                    assignment.put("dueAt", dueAt == null ? null : dueAt.toInstant().toString());
                    assignment.put("promptFileName", result.getString("prompt_file_name"));
                    assignment.put("submissionCount", result.getInt("submission_count"));
                    assignment.put("submittedAt", result.getTimestamp("submitted_at") == null
                            ? null : result.getTimestamp("submitted_at").toString());
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
                + "s.file_name, s.submitted_at "
                + "FROM Essay_Submissions s "
                + "JOIN Users u ON u.user_id = s.student_id "
                + "JOIN Essay_Assignments a ON a.assignment_id = s.assignment_id "
                + "WHERE s.assignment_id = ? AND a.created_by = ? "
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
                    submissions.add(submission);
                }

            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not load assignment submissions.", e);
        }
        return submissions;
    }

    public boolean updateDeadline(int assignmentId, int lecturerId, Timestamp dueAt) {
        String sql = "UPDATE Essay_Assignments SET due_at = ? "
                + "WHERE assignment_id = ? AND created_by = ?";
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

    public StoredFile getPromptFile(int assignmentId, int userId, boolean lecturer) {
        String sql = "SELECT prompt_file_name, prompt_file_data FROM Essay_Assignments "
                + "WHERE assignment_id = ? AND prompt_file_data IS NOT NULL"
                + (lecturer ? " AND created_by = ?" : "");
        try (Connection connection = requireConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, assignmentId);
            if (lecturer) {
                statement.setInt(2, userId);
            }
            try (ResultSet result = statement.executeQuery()) {
                return result.next()
                        ? new StoredFile(result.getString("prompt_file_name"),
                                result.getBytes("prompt_file_data"))
                        : null;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not load assignment file.", e);
        }
    }

    public StoredFile getSubmissionFile(int assignmentId, int studentId, int userId, boolean lecturer) {
        String sql = "SELECT s.file_name, s.file_data FROM Essay_Submissions s "
                + "JOIN Essay_Assignments a ON a.assignment_id = s.assignment_id "
                + "WHERE s.assignment_id = ? AND s.student_id = ? AND "
                + (lecturer ? "a.created_by = ?" : "s.student_id = ?");
        try (Connection connection = requireConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, assignmentId);
            statement.setInt(2, studentId);
            statement.setInt(3, userId);
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
        String updateSql = "UPDATE Essay_Submissions SET file_name = ?, file_data = ?, "
                + "submitted_at = SYSUTCDATETIME() WHERE assignment_id = ? AND student_id = ?";
        String insertSql = "INSERT INTO Essay_Submissions "
                + "(assignment_id, student_id, file_name, file_data) VALUES (?, ?, ?, ?)";
        String deadlineSql = "SELECT CASE WHEN due_at IS NULL OR due_at > SYSUTCDATETIME() "
                + "THEN 1 ELSE 0 END FROM Essay_Assignments WITH (UPDLOCK, HOLDLOCK) "
                + "WHERE assignment_id = ?";
        try (Connection connection = requireConnection()) {
            connection.setAutoCommit(false);
            try {
                boolean submissionOpen;
                try (PreparedStatement deadline = connection.prepareStatement(deadlineSql)) {
                    deadline.setInt(1, assignmentId);
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
                    update.setInt(3, assignmentId);
                    update.setInt(4, studentId);
                    updated = update.executeUpdate();
                }
                if (updated == 0) {
                    try (PreparedStatement insert = connection.prepareStatement(insertSql)) {
                        insert.setInt(1, assignmentId);
                        insert.setInt(2, studentId);
                        insert.setString(3, fileName);
                        insert.setBytes(4, fileData);
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
