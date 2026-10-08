package com.aita.gitanalytics.dao;

import com.aita.gitanalytics.service.ExamService;
import com.aita.gitanalytics.service.ExamService.QuestionDraft;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ExamDAO {

    public int createExam(int lecturerId, String title, String description,
                          List<QuestionDraft> questions) {
        String examSql = "INSERT INTO Exams (title, description, created_by) "
                + "OUTPUT INSERTED.exam_id VALUES (?, ?, ?)";
        String questionSql = "INSERT INTO Exam_Questions "
                + "(exam_id, question_order, question_type, question_text, points, correct_answer) "
                + "OUTPUT INSERTED.question_id VALUES (?, ?, ?, ?, ?, ?)";
        String optionSql = "INSERT INTO Exam_Options (question_id, option_key, option_text) "
                + "VALUES (?, ?, ?)";

        try (Connection connection = requireConnection()) {
            connection.setAutoCommit(false);
            try {
                int examId;
                try (PreparedStatement statement = connection.prepareStatement(examSql)) {
                    statement.setString(1, title);
                    statement.setString(2, description);
                    statement.setInt(3, lecturerId);
                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) {
                            throw new SQLException("Exam insert did not return an ID.");
                        }
                        examId = result.getInt(1);
                    }
                }

                try (PreparedStatement questionStatement = connection.prepareStatement(questionSql);
                     PreparedStatement optionStatement = connection.prepareStatement(optionSql)) {
                    int order = 1;
                    for (QuestionDraft question : questions) {
                        questionStatement.setInt(1, examId);
                        questionStatement.setInt(2, order++);
                        questionStatement.setString(3, question.getType());
                        questionStatement.setString(4, question.getQuestion());
                        questionStatement.setBigDecimal(5, question.getPoints());
                        questionStatement.setString(6, question.getCorrectAnswer());
                        int questionId;
                        try (ResultSet result = questionStatement.executeQuery()) {
                            if (!result.next()) {
                                throw new SQLException("Question insert did not return an ID.");
                            }
                            questionId = result.getInt(1);
                        }

                        for (Map.Entry<String, String> option : question.getOptions().entrySet()) {
                            optionStatement.setInt(1, questionId);
                            optionStatement.setString(2, option.getKey());
                            optionStatement.setString(3, option.getValue());
                            optionStatement.addBatch();
                        }
                    }
                    optionStatement.executeBatch();
                }
                connection.commit();
                return examId;
            } catch (SQLException | RuntimeException e) {
                rollback(connection, e);
                if (e instanceof SQLException) {
                    throw new DataAccessException("Could not save exam.", (SQLException) e);
                }
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not save exam.", e);
        }
    }

    public List<Map<String, Object>> listExams(int userId, boolean lecturer) {
        String sql = "SELECT e.exam_id, e.title, e.description, e.created_at, "
                + "(SELECT COUNT(*) FROM Exam_Questions q WHERE q.exam_id = e.exam_id) AS question_count, "
            + "(SELECT COUNT(*) FROM Exam_Attempts a2 WHERE a2.exam_id = e.exam_id) AS attempt_count, "
                + "a.score, a.max_score, a.submitted_at "
                + "FROM Exams e LEFT JOIN Exam_Attempts a "
                + "ON a.exam_id = e.exam_id AND a.student_id = ? "
                + (lecturer ? "WHERE e.created_by = ? " : "")
                + "ORDER BY e.created_at DESC, e.exam_id DESC";
        List<Map<String, Object>> exams = new ArrayList<>();
        try (Connection connection = requireConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            if (lecturer) {
                statement.setInt(2, userId);
            }
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    Map<String, Object> exam = new LinkedHashMap<>();
                    exam.put("examId", result.getInt("exam_id"));
                    exam.put("title", result.getString("title"));
                    exam.put("description", result.getString("description"));
                    exam.put("createdAt", result.getTimestamp("created_at").toString());
                    exam.put("questionCount", result.getInt("question_count"));
                    exam.put("attemptCount", result.getInt("attempt_count"));
                    BigDecimal score = result.getBigDecimal("score");
                    exam.put("submitted", score != null);
                    exam.put("score", score);
                    exam.put("maxScore", result.getBigDecimal("max_score"));
                    exam.put("submittedAt", result.getTimestamp("submitted_at") == null
                            ? null : result.getTimestamp("submitted_at").toString());
                    exams.add(exam);
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not load exams.", e);
        }
        return exams;
    }

    public Map<String, Object> getExam(int examId, int userId, boolean lecturer) {
        String examSql = "SELECT exam_id, title, description, created_at FROM Exams WHERE exam_id = ?"
                + (lecturer ? " AND created_by = ?" : "");
        String questionSql = "SELECT question_id, question_type, question_text, points "
                + "FROM Exam_Questions WHERE exam_id = ? ORDER BY question_order";
        String optionSql = "SELECT option_key, option_text FROM Exam_Options "
                + "WHERE question_id = ? ORDER BY option_key";
        Map<String, Object> exam = null;
        try (Connection connection = requireConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(examSql)) {
                statement.setInt(1, examId);
                if (lecturer) {
                    statement.setInt(2, userId);
                }
                try (ResultSet result = statement.executeQuery()) {
                    if (result.next()) {
                        exam = new LinkedHashMap<>();
                        exam.put("examId", result.getInt("exam_id"));
                        exam.put("title", result.getString("title"));
                        exam.put("description", result.getString("description"));
                        exam.put("createdAt", result.getTimestamp("created_at").toString());
                    }
                }
            }
            if (exam == null) {
                return null;
            }

            List<Map<String, Object>> questions = new ArrayList<>();
            BigDecimal maxScore = BigDecimal.ZERO;
            try (PreparedStatement questionStatement = connection.prepareStatement(questionSql);
                 PreparedStatement optionStatement = connection.prepareStatement(optionSql)) {
                questionStatement.setInt(1, examId);
                try (ResultSet result = questionStatement.executeQuery()) {
                    while (result.next()) {
                        int questionId = result.getInt("question_id");
                        String type = result.getString("question_type");
                        BigDecimal points = result.getBigDecimal("points");
                        Map<String, Object> question = new LinkedHashMap<>();
                        question.put("questionId", questionId);
                        question.put("type", type);
                        question.put("text", result.getString("question_text"));
                        question.put("points", points);
                        maxScore = maxScore.add(points);

                        List<Map<String, String>> options = new ArrayList<>();
                        if ("MCQ".equals(type)) {
                            optionStatement.setInt(1, questionId);
                            try (ResultSet optionResult = optionStatement.executeQuery()) {
                                while (optionResult.next()) {
                                    Map<String, String> option = new LinkedHashMap<>();
                                    option.put("key", optionResult.getString("option_key"));
                                    option.put("text", optionResult.getString("option_text"));
                                    options.add(option);
                                }
                            }
                        }
                        question.put("options", options);
                        questions.add(question);
                    }
                }
            }
            exam.put("questions", questions);
            exam.put("maxScore", maxScore);
            if (!lecturer) {
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT score, max_score, submitted_at FROM Exam_Attempts "
                                + "WHERE exam_id = ? AND student_id = ?")) {
                    statement.setInt(1, examId);
                    statement.setInt(2, userId);
                    try (ResultSet result = statement.executeQuery()) {
                        if (result.next()) {
                            exam.put("submitted", true);
                            exam.put("score", result.getBigDecimal("score"));
                            exam.put("maxScore", result.getBigDecimal("max_score"));
                            exam.put("submittedAt", result.getTimestamp("submitted_at").toString());
                        } else {
                            exam.put("submitted", false);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not load exam.", e);
        }
        return exam;
    }

    public Map<String, Object> getResults(int examId, int lecturerId) {
        String examSql = "SELECT title FROM Exams WHERE exam_id = ? AND created_by = ?";
        String resultsSql = "SELECT u.full_name, u.username, a.score, a.max_score, a.submitted_at "
                + "FROM Exam_Attempts a JOIN Users u ON u.user_id = a.student_id "
                + "WHERE a.exam_id = ? ORDER BY u.full_name";
        Map<String, Object> data = new LinkedHashMap<>();
        try (Connection connection = requireConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(examSql)) {
                statement.setInt(1, examId);
                statement.setInt(2, lecturerId);
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) {
                        return null;
                    }
                    data.put("title", result.getString("title"));
                }
            }
            List<Map<String, Object>> results = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(resultsSql)) {
                statement.setInt(1, examId);
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("fullName", result.getString("full_name"));
                        row.put("username", result.getString("username"));
                        row.put("score", result.getBigDecimal("score"));
                        row.put("maxScore", result.getBigDecimal("max_score"));
                        row.put("submittedAt", result.getTimestamp("submitted_at").toString());
                        results.add(row);
                    }
                }
            }
            data.put("results", results);
        } catch (SQLException e) {
            throw new DataAccessException("Could not load exam results.", e);
        }
        return data;
    }

    public Map<String, Object> submit(int examId, int studentId, Map<Integer, String> answers) {
        String questionSql = "SELECT question_id, question_type, points, correct_answer "
                + "FROM Exam_Questions WHERE exam_id = ? ORDER BY question_order";
        try (Connection connection = requireConnection()) {
            connection.setAutoCommit(false);
            try {
                List<GradedAnswer> gradedAnswers = new ArrayList<>();
                BigDecimal score = BigDecimal.ZERO;
                BigDecimal maxScore = BigDecimal.ZERO;
                try (PreparedStatement statement = connection.prepareStatement(questionSql)) {
                    statement.setInt(1, examId);
                    try (ResultSet result = statement.executeQuery()) {
                        while (result.next()) {
                            int questionId = result.getInt("question_id");
                            String type = result.getString("question_type");
                            String expected = result.getString("correct_answer");
                            BigDecimal points = result.getBigDecimal("points");
                            String answer = answers.get(questionId);
                            if (answer != null && answer.length() > 1000) {
                                throw new IllegalArgumentException("Câu trả lời vượt quá 1000 ký tự.");
                            }
                            boolean correct = ExamService.isCorrect(type, expected, answer);
                            BigDecimal awarded = correct ? points : BigDecimal.ZERO;
                            maxScore = maxScore.add(points);
                            score = score.add(awarded);
                            gradedAnswers.add(new GradedAnswer(questionId, answer, correct, awarded));
                        }
                    }
                }
                if (gradedAnswers.isEmpty()) {
                    throw new IllegalArgumentException("Đề thi không có câu hỏi.");
                }
                for (Integer answerQuestionId : answers.keySet()) {
                    if (gradedAnswers.stream().noneMatch(answer -> answer.questionId == answerQuestionId)) {
                        throw new IllegalArgumentException("Bài làm chứa câu hỏi không thuộc đề này.");
                    }
                }

                int attemptId;
                String attemptSql = "INSERT INTO Exam_Attempts "
                        + "(exam_id, student_id, score, max_score) OUTPUT INSERTED.attempt_id "
                        + "VALUES (?, ?, ?, ?)";
                try (PreparedStatement statement = connection.prepareStatement(attemptSql)) {
                    statement.setInt(1, examId);
                    statement.setInt(2, studentId);
                    statement.setBigDecimal(3, score.setScale(2, RoundingMode.HALF_UP));
                    statement.setBigDecimal(4, maxScore.setScale(2, RoundingMode.HALF_UP));
                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) {
                            throw new SQLException("Attempt insert did not return an ID.");
                        }
                        attemptId = result.getInt(1);
                    }
                }

                String answerSql = "INSERT INTO Exam_Answers "
                        + "(attempt_id, question_id, submitted_answer, is_correct, awarded_points) "
                        + "VALUES (?, ?, ?, ?, ?)";
                try (PreparedStatement statement = connection.prepareStatement(answerSql)) {
                    for (GradedAnswer answer : gradedAnswers) {
                        statement.setInt(1, attemptId);
                        statement.setInt(2, answer.questionId);
                        statement.setString(3, answer.answer);
                        statement.setBoolean(4, answer.correct);
                        statement.setBigDecimal(5, answer.awarded);
                        statement.addBatch();
                    }
                    statement.executeBatch();
                }
                connection.commit();
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("score", score.setScale(2, RoundingMode.HALF_UP));
                result.put("maxScore", maxScore.setScale(2, RoundingMode.HALF_UP));
                result.put("submitted", true);
                return result;
            } catch (SQLException | RuntimeException e) {
                rollback(connection, e);
                if (e instanceof SQLException) {
                    throw new DataAccessException("Could not submit exam.", (SQLException) e);
                }
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not submit exam.", e);
        }
    }

    private Connection requireConnection() {
        Connection connection = DBContext.getConnection();
        if (connection == null) {
            throw new DataAccessException("Database connection is unavailable.", null);
        }
        return connection;
    }

    private void rollback(Connection connection, Exception error) {
        try {
            connection.rollback();
        } catch (SQLException rollbackError) {
            error.addSuppressed(rollbackError);
        }
    }

    private static final class GradedAnswer {
        private final int questionId;
        private final String answer;
        private final boolean correct;
        private final BigDecimal awarded;

        private GradedAnswer(int questionId, String answer, boolean correct, BigDecimal awarded) {
            this.questionId = questionId;
            this.answer = answer;
            this.correct = correct;
            this.awarded = awarded;
        }
    }
}