package com.aita.gitanalytics.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CourseDAO {

    public List<Map<String, Object>> listCourses(int lecturerId, boolean administrator) {
        String sql = "SELECT c.course_id, c.course_code, c.course_name, c.semester, "
                + "(SELECT COUNT(*) FROM Course_Classes cl WHERE cl.course_id = c.course_id) AS class_count, "
                + "u.user_id AS lecturer_id, u.full_name AS lecturer_name, u.email AS lecturer_email "
                + "FROM Courses c "
                + "LEFT JOIN Lecturer_Courses lc ON lc.course_id = c.course_id "
                + (administrator ? "" : "AND lc.lecturer_id = ? ")
                + "LEFT JOIN Users u ON u.user_id = lc.lecturer_id "
                + (administrator ? "" : "WHERE EXISTS (SELECT 1 FROM Lecturer_Courses own "
                        + "WHERE own.course_id = c.course_id AND own.lecturer_id = ?) ")
                + "ORDER BY c.course_name, u.full_name";
        Map<Integer, Map<String, Object>> courses = new LinkedHashMap<>();
        try (Connection conn = DBContext.getConnection()) {
            if (conn == null) {
                throw new DataAccessException("Database connection is unavailable.", null);
            }
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                if (!administrator) {
                    ps.setInt(1, lecturerId);
                    ps.setInt(2, lecturerId);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int courseId = rs.getInt("course_id");
                        Map<String, Object> course = courses.get(courseId);
                        if (course == null) {
                            course = new LinkedHashMap<>();
                            course.put("courseId", courseId);
                            course.put("courseCode", rs.getString("course_code"));
                            course.put("courseName", rs.getString("course_name"));
                            course.put("semester", rs.getString("semester"));
                            course.put("classCount", rs.getInt("class_count"));
                            course.put("lecturers", new ArrayList<Map<String, Object>>());
                            courses.put(courseId, course);
                        }
                        int assignedLecturerId = rs.getInt("lecturer_id");
                        if (!rs.wasNull()) {
                            @SuppressWarnings("unchecked")
                            List<Map<String, Object>> lecturers =
                                    (List<Map<String, Object>>) course.get("lecturers");
                            Map<String, Object> lecturer = new LinkedHashMap<>();
                            lecturer.put("userId", assignedLecturerId);
                            lecturer.put("fullName", rs.getString("lecturer_name"));
                            lecturer.put("email", rs.getString("lecturer_email"));
                            lecturers.add(lecturer);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not load courses.", e);
        }
        return new ArrayList<>(courses.values());
    }

    public List<Map<String, Object>> listLecturers() {
        List<Map<String, Object>> lecturers = new ArrayList<>();
        String sql = "SELECT user_id, username, full_name, email FROM Users "
                + "WHERE role = 'LECTURER' ORDER BY full_name";
        try (Connection conn = DBContext.getConnection()) {
            if (conn == null) {
                throw new DataAccessException("Database connection is unavailable.", null);
            }
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> lecturer = new LinkedHashMap<>();
                    lecturer.put("userId", rs.getInt("user_id"));
                    lecturer.put("username", rs.getString("username"));
                    lecturer.put("fullName", rs.getString("full_name"));
                    lecturer.put("email", rs.getString("email"));
                    lecturers.add(lecturer);
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not load lecturers.", e);
        }
        return lecturers;
    }

    public List<Map<String, Object>> listStudents() {
        List<Map<String, Object>> students = new ArrayList<>();
        String sql = "SELECT user_id, username, full_name, email FROM Users "
                + "WHERE role = 'STUDENT' ORDER BY full_name";
        try (Connection conn = DBContext.getConnection()) {
            if (conn == null) {
                throw new DataAccessException("Database connection is unavailable.", null);
            }
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> student = new LinkedHashMap<>();
                    student.put("userId", rs.getInt("user_id"));
                    student.put("username", rs.getString("username"));
                    student.put("fullName", rs.getString("full_name"));
                    student.put("email", rs.getString("email"));
                    students.add(student);
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not load students.", e);
        }
        return students;
    }

    public List<Map<String, Object>> listClasses(int courseId, int lecturerId, boolean administrator) {
        String sql = "SELECT cl.class_id, cl.class_name, u.user_id AS student_id, "
                + "u.username, u.full_name, u.email "
                + "FROM Course_Classes cl "
                + "JOIN Courses c ON c.course_id = cl.course_id "
                + "LEFT JOIN Class_Students cs ON cs.class_id = cl.class_id "
                + "LEFT JOIN Users u ON u.user_id = cs.student_id AND u.role = 'STUDENT' "
                + "WHERE c.course_id = ? AND (? = 1 OR EXISTS ("
                + "SELECT 1 FROM Lecturer_Courses lc WHERE lc.course_id = c.course_id "
                + "AND lc.lecturer_id = ?)) "
                + "ORDER BY cl.class_name, u.full_name";
        Map<Integer, Map<String, Object>> classes = new LinkedHashMap<>();
        try (Connection conn = DBContext.getConnection()) {
            if (conn == null) {
                throw new DataAccessException("Database connection is unavailable.", null);
            }
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, courseId);
                ps.setBoolean(2, administrator);
                ps.setInt(3, lecturerId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int classId = rs.getInt("class_id");
                        Map<String, Object> courseClass = classes.get(classId);
                        if (courseClass == null) {
                            courseClass = new LinkedHashMap<>();
                            courseClass.put("classId", classId);
                            courseClass.put("className", rs.getString("class_name"));
                            courseClass.put("students", new ArrayList<Map<String, Object>>());
                            classes.put(classId, courseClass);
                        }
                        int studentId = rs.getInt("student_id");
                        if (!rs.wasNull()) {
                            @SuppressWarnings("unchecked")
                            List<Map<String, Object>> students =
                                    (List<Map<String, Object>>) courseClass.get("students");
                            Map<String, Object> student = new LinkedHashMap<>();
                            student.put("userId", studentId);
                            student.put("username", rs.getString("username"));
                            student.put("fullName", rs.getString("full_name"));
                            student.put("email", rs.getString("email"));
                            students.add(student);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not load classes.", e);
        }
        return new ArrayList<>(classes.values());
    }

    public int createClass(int courseId, String className, List<Integer> studentIds) {
        Set<Integer> uniqueStudentIds = new LinkedHashSet<>(studentIds);
        if (uniqueStudentIds.size() != studentIds.size()
                || uniqueStudentIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("Danh sách học sinh không hợp lệ.");
        }
        try (Connection conn = DBContext.getConnection()) {
            if (conn == null) {
                throw new DataAccessException("Database connection is unavailable.", null);
            }
            conn.setAutoCommit(false);
            try {
                validateStudents(conn, uniqueStudentIds);
                int classId;
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO Course_Classes (course_id, class_name) VALUES (?, ?)",
                        new String[]{"class_id"})) {
                    ps.setInt(1, courseId);
                    ps.setString(2, className.trim());
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (keys.next()) {
                            classId = keys.getInt(1);
                        } else {
                            throw new DataAccessException("Class ID was not generated.", null);
                        }
                    }
                }
                insertClassStudents(conn, classId, uniqueStudentIds);
                conn.commit();
                return classId;
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not create class.", e);
        }
    }

    public void replaceClassStudents(int classId, List<Integer> studentIds) {
        Set<Integer> uniqueStudentIds = new LinkedHashSet<>(studentIds);
        if (uniqueStudentIds.size() != studentIds.size()
                || uniqueStudentIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("Danh sách học sinh không hợp lệ.");
        }
        try (Connection conn = DBContext.getConnection()) {
            if (conn == null) {
                throw new DataAccessException("Database connection is unavailable.", null);
            }
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement check = conn.prepareStatement(
                        "SELECT class_id FROM Course_Classes WITH (UPDLOCK, ROWLOCK) WHERE class_id = ?")) {
                    check.setInt(1, classId);
                    try (ResultSet rs = check.executeQuery()) {
                        if (!rs.next()) {
                            throw new IllegalArgumentException("Không tìm thấy lớp học.");
                        }
                    }
                }
                validateStudents(conn, uniqueStudentIds);
                try (PreparedStatement delete = conn.prepareStatement(
                        "DELETE FROM Class_Students WHERE class_id = ?")) {
                    delete.setInt(1, classId);
                    delete.executeUpdate();
                }
                insertClassStudents(conn, classId, uniqueStudentIds);
                conn.commit();
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not update class roster.", e);
        }
    }

    private void validateStudents(Connection conn, Set<Integer> studentIds) throws SQLException {
        if (studentIds.isEmpty()) {
            return;
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(studentIds.size(), "?"));
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM Users WHERE role = 'STUDENT' AND user_id IN (" + placeholders + ")")) {
            int index = 1;
            for (Integer studentId : studentIds) {
                ps.setInt(index++, studentId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || rs.getInt(1) != studentIds.size()) {
                    throw new IllegalArgumentException("Danh sách học sinh không hợp lệ.");
                }
            }
        }
    }

    private void insertClassStudents(Connection conn, int classId, Set<Integer> studentIds)
            throws SQLException {
        try (PreparedStatement insert = conn.prepareStatement(
                "INSERT INTO Class_Students (class_id, student_id) VALUES (?, ?)")) {
            for (Integer studentId : studentIds) {
                insert.setInt(1, classId);
                insert.setInt(2, studentId);
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    public boolean deleteClass(int classId) {
        try (Connection conn = DBContext.getConnection()) {
            if (conn == null) {
                throw new DataAccessException("Database connection is unavailable.", null);
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM Course_Classes WHERE class_id = ?")) {
                ps.setInt(1, classId);
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not delete class.", e);
        }
    }

    public void saveCourse(Integer courseId, String courseCode, String courseName,
                           String semester, List<Integer> lecturerIds) {
        String normalizedCode = courseCode.trim();
        String normalizedName = courseName.trim();
        String normalizedSemester = semester == null || semester.trim().isEmpty()
                ? null : semester.trim();
        Set<Integer> uniqueLecturerIds = new LinkedHashSet<>(lecturerIds);
        if (uniqueLecturerIds.size() != lecturerIds.size() || uniqueLecturerIds.isEmpty()
                || uniqueLecturerIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("Vui lòng chọn ít nhất một giảng viên hợp lệ.");
        }

        try (Connection conn = DBContext.getConnection()) {
            if (conn == null) {
                throw new DataAccessException("Database connection is unavailable.", null);
            }
            conn.setAutoCommit(false);
            try {
                validateLecturers(conn, uniqueLecturerIds);
                int savedCourseId;
                if (courseId == null) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO Courses (course_code, course_name, semester) "
                                    + "VALUES (?, ?, ?)", new String[]{"course_id"})) {
                        ps.setString(1, normalizedCode);
                        ps.setString(2, normalizedName);
                        ps.setString(3, normalizedSemester);
                        ps.executeUpdate();
                        try (ResultSet keys = ps.getGeneratedKeys()) {
                            if (!keys.next()) {
                                throw new SQLException("Course ID was not generated.");
                            }
                            savedCourseId = keys.getInt(1);
                        }
                    }
                } else {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE Courses SET course_code = ?, course_name = ?, semester = ? "
                                    + "WHERE course_id = ?")) {
                        ps.setString(1, normalizedCode);
                        ps.setString(2, normalizedName);
                        ps.setString(3, normalizedSemester);
                        ps.setInt(4, courseId);
                        if (ps.executeUpdate() != 1) {
                            throw new IllegalArgumentException("Không tìm thấy môn học cần cập nhật.");
                        }
                    }
                    savedCourseId = courseId;
                    try (PreparedStatement ps = conn.prepareStatement(
                            "DELETE FROM Lecturer_Courses WHERE course_id = ?")) {
                        ps.setInt(1, savedCourseId);
                        ps.executeUpdate();
                    }
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO Lecturer_Courses (course_id, lecturer_id) VALUES (?, ?)")) {
                    for (Integer lecturerId : uniqueLecturerIds) {
                        ps.setInt(1, savedCourseId);
                        ps.setInt(2, lecturerId);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                conn.commit();
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not save course.", e);
        }
    }

    private void validateLecturers(Connection conn, Set<Integer> lecturerIds) throws SQLException {
        String placeholders = String.join(",", java.util.Collections.nCopies(lecturerIds.size(), "?"));
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM Users WHERE role = 'LECTURER' AND user_id IN (" + placeholders + ")")) {
            int index = 1;
            for (Integer lecturerId : lecturerIds) {
                ps.setInt(index++, lecturerId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || rs.getInt(1) != lecturerIds.size()) {
                    throw new IllegalArgumentException("Danh sách giảng viên không hợp lệ.");
                }
            }
        }
    }

    public boolean deleteCourse(int courseId) {
        try (Connection conn = DBContext.getConnection()) {
            if (conn == null) {
                throw new DataAccessException("Database connection is unavailable.", null);
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM Courses WHERE course_id = ?")) {
                ps.setInt(1, courseId);
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not delete course.", e);
        }
    }
}
