package com.aita.gitanalytics.dao;

import com.aita.gitanalytics.model.Student;
import com.aita.gitanalytics.model.UserAccount;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class UserDAO {

    public UserAccount authenticate(String identifier, String password) {
        if (isBlank(identifier) || isBlank(password)) {
            return null;
        }

        String sql = "SELECT user_id, username, full_name, email, role "
                + "FROM users WHERE (email = ? OR username = ?) AND password_hash = ?";

        try (Connection conn = DBContext.getConnection()) {
            if (conn == null) {
                return null;
            }
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, identifier.trim());
                ps.setString(2, identifier.trim());
                ps.setString(3, password.trim());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return new UserAccount(
                                rs.getInt("user_id"),
                                rs.getString("username"),
                                rs.getString("full_name"),
                                rs.getString("email"),
                                rs.getString("role"));
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public boolean saveRememberToken(String tokenHash, int userId, Timestamp expiresAt) {
        String sql = "INSERT INTO remember_tokens (token_hash, user_id, expires_at) VALUES (?, ?, ?)";
        try (Connection conn = DBContext.getConnection()) {
            if (conn == null) {
                return false;
            }
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, tokenHash);
                ps.setInt(2, userId);
                ps.setTimestamp(3, expiresAt);
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public UserAccount findRememberedUser(String tokenHash) {
        String sql = "SELECT u.user_id, u.username, u.full_name, u.email, u.role "
                + "FROM remember_tokens t JOIN users u ON u.user_id = t.user_id "
                + "WHERE t.token_hash = ? AND t.expires_at > GETDATE()";
        try (Connection conn = DBContext.getConnection()) {
            if (conn == null) {
                return null;
            }
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, tokenHash);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return new UserAccount(rs.getInt("user_id"), rs.getString("username"),
                                rs.getString("full_name"), rs.getString("email"), rs.getString("role"));
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void deleteRememberToken(String tokenHash) {
        String sql = "DELETE FROM remember_tokens WHERE token_hash = ?";
        try (Connection conn = DBContext.getConnection()) {
            if (conn == null) {
                return;
            }
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, tokenHash);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<Student> getAllStudents() {
        List<Student> students = new ArrayList<>();
        String sql = "SELECT user_id, username, full_name, email, github_username "
                + "FROM users WHERE role = 'STUDENT' ORDER BY full_name";

        try (Connection conn = DBContext.getConnection()) {
            if (conn == null) {
                throw new DataAccessException("Database connection is unavailable.", null);
            }
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    students.add(new Student(
                            rs.getInt("user_id"),
                            rs.getString("username"),
                            rs.getString("full_name"),
                            rs.getString("email"),
                            rs.getString("github_username")));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not load students.", e);
        }
        return students;
    }

    public Integer getStudentGroupId(int userId) {
        String sql = "SELECT TOP (1) group_id FROM Group_Members "
                + "WHERE user_id = ? ORDER BY group_id";
        try (Connection conn = DBContext.getConnection()) {
            if (conn == null) {
                return null;
            }
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getInt("group_id") : null;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public boolean updateStudent(int userId, String username, String fullName, String email,
                                 String githubUsername, String password) {
        if (isBlank(username) || isBlank(fullName) || isBlank(email)) {
            return false;
        }

        boolean updatePassword = !isBlank(password);
        String sql = "UPDATE users SET username = ?, "
                + (updatePassword ? "password_hash = ?, " : "")
                + "full_name = ?, email = ?, github_username = ? "
                + "WHERE user_id = ? AND role = 'STUDENT'";

        try (Connection conn = DBContext.getConnection()) {
            if (conn == null) {
                throw new DataAccessException("Database connection is unavailable.", null);
            }
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                int index = 1;
                ps.setString(index++, username.trim());
                if (updatePassword) {
                    ps.setString(index++, password.trim());
                }
                ps.setString(index++, fullName.trim());
                ps.setString(index++, email.trim());
                ps.setString(index++, isBlank(githubUsername) ? null : githubUsername.trim());
                ps.setInt(index, userId);
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not update student.", e);
        }
    }

    public boolean deleteStudent(int userId) {
        String sql = "DELETE FROM users WHERE user_id = ? AND role = 'STUDENT'";
        try (Connection conn = DBContext.getConnection()) {
            if (conn == null) {
                throw new DataAccessException("Database connection is unavailable.", null);
            }
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, userId);
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not delete student.", e);
        }
    }

    // =====================================
    // THÊM USER
    // =====================================

    public boolean createUser(String username,
                              String fullName,
                              String email,
                              String githubUsername,
                              String password,
                              String role) {

        if (isBlank(username)
                || isBlank(fullName)
                || isBlank(email)
                || isBlank(password)) {
            return false;
        }

        String checkSql =
                "SELECT 1 FROM users "
                + "WHERE username = ? OR email = ?";

        String insertSql =
                "INSERT INTO users "
                + "(username, password_hash, full_name, email, "
                + "github_username, role, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, GETDATE())";

        try (Connection conn = DBContext.getConnection()) {

            if (conn == null) {
                throw new DataAccessException("Database connection is unavailable.", null);
            }

            // Kiểm tra Username hoặc Email đã tồn tại
            try (PreparedStatement checkPs =
                         conn.prepareStatement(checkSql)) {

                checkPs.setString(1, username.trim());
                checkPs.setString(2, email.trim());

                try (ResultSet rs = checkPs.executeQuery()) {

                    if (rs.next()) {
                        System.err.println(
                                "❌ Username hoặc Email đã tồn tại!"
                        );
                        return false;
                    }
                }
            }

            // Thêm user
            try (PreparedStatement insertPs =
                         conn.prepareStatement(insertSql)) {

                insertPs.setString(1, username.trim());
                insertPs.setString(2, password.trim());
                insertPs.setString(3, fullName.trim());
                insertPs.setString(4, email.trim());

                if (isBlank(githubUsername)) {
                    insertPs.setString(5, null);
                } else {
                    insertPs.setString(5, githubUsername.trim());
                }

                if (isBlank(role)) {
                    insertPs.setString(6, "STUDENT");
                } else {
                    insertPs.setString(
                            6,
                            role.trim().toUpperCase()
                    );
                }

                return insertPs.executeUpdate() > 0;
            }

        } catch (SQLException e) {
            throw new DataAccessException("Could not create user.", e);
        }
    }


    // =====================================
    // ĐĂNG NHẬP BẰNG EMAIL
    // =====================================

    public boolean login(String email, String password) {

        if (isBlank(email) || isBlank(password)) {
            return false;
        }

        String sql =
                "SELECT 1 FROM Users "
                + "WHERE email = ? "
                + "AND password_hash = ?";

        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps =
                     conn.prepareStatement(sql)) {

            ps.setString(1, email.trim());
            ps.setString(2, password.trim());

            try (ResultSet rs = ps.executeQuery()) {

                return rs.next();
            }

        } catch (SQLException e) {

            System.err.println("❌ Lỗi đăng nhập!");
            e.printStackTrace();

            return false;
        }
    }


    // =====================================
    // SỬA USER
    // =====================================

    public boolean updateUser(int userId,
                              String username,
                              String fullName,
                              String email,
                              String githubUsername,
                              String password,
                              String role) {

        if (isBlank(username)
                || isBlank(fullName)
                || isBlank(email)) {
            return false;
        }

        String sql =
                "UPDATE users "
                + "SET username = ?, "
                + "password_hash = ?, "
                + "full_name = ?, "
                + "email = ?, "
                + "github_username = ?, "
                + "role = ? "
                + "WHERE user_id = ?";

        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps =
                     conn.prepareStatement(sql)) {

            ps.setString(1, username.trim());

            // Nếu không nhập password mới
            // thì giữ password cũ
            if (isBlank(password)) {

                ps.setString(
                        2,
                        getCurrentPassword(userId)
                );

            } else {

                ps.setString(
                        2,
                        password.trim()
                );
            }

            ps.setString(3, fullName.trim());
            ps.setString(4, email.trim());

            if (isBlank(githubUsername)) {

                ps.setString(5, null);

            } else {

                ps.setString(
                        5,
                        githubUsername.trim()
                );
            }

            if (isBlank(role)) {

                ps.setString(6, "STUDENT");

            } else {

                ps.setString(
                        6,
                        role.trim().toUpperCase()
                );
            }

            ps.setInt(7, userId);

            return ps.executeUpdate() > 0;

        } catch (SQLException e) {

            System.err.println("❌ Lỗi sửa user!");
            e.printStackTrace();

            return false;
        }
    }


    // =====================================
    // LẤY PASSWORD HIỆN TẠI
    // =====================================

    private String getCurrentPassword(int userId) {

        String sql =
                "SELECT password_hash "
                + "FROM users "
                + "WHERE user_id = ?";

        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps =
                     conn.prepareStatement(sql)) {

            ps.setInt(1, userId);

            try (ResultSet rs = ps.executeQuery()) {

                if (rs.next()) {

                    return rs.getString(
                            "password_hash"
                    );
                }
            }

        } catch (SQLException e) {

            e.printStackTrace();
        }

        return "";
    }


    // =====================================
    // XÓA USER
    // =====================================

    public boolean deleteUser(int userId) {

        String sql =
                "DELETE FROM users "
                + "WHERE user_id = ?";

        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps =
                     conn.prepareStatement(sql)) {

            ps.setInt(1, userId);

            return ps.executeUpdate() > 0;

        } catch (SQLException e) {

            System.err.println("❌ Lỗi xóa user!");
            e.printStackTrace();

            return false;
        }
    }


    // =====================================
    // XEM DANH SÁCH USER
    // =====================================

    public void getAllUsers() {

        String sql =
                "SELECT user_id, username, full_name, email, "
                + "github_username, role, created_at "
                + "FROM users "
                + "ORDER BY user_id";

        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps =
                     conn.prepareStatement(sql);
             ResultSet rs =
                     ps.executeQuery()) {

            boolean hasUser = false;

            System.out.println();
            System.out.println(
                    "=================================================="
            );
            System.out.println(
                    "                 DANH SÁCH USER"
            );
            System.out.println(
                    "=================================================="
            );

            while (rs.next()) {

                hasUser = true;

                System.out.println(
                        "----------------------------------------------"
                );

                System.out.println(
                        "ID       : "
                        + rs.getInt("user_id")
                );

                System.out.println(
                        "Username : "
                        + rs.getString("username")
                );

                System.out.println(
                        "Họ tên   : "
                        + rs.getString("full_name")
                );

                System.out.println(
                        "Email    : "
                        + rs.getString("email")
                );

                System.out.println(
                        "Github   : "
                        + rs.getString("github_username")
                );

                System.out.println(
                        "Role     : "
                        + rs.getString("role")
                );

                System.out.println(
                        "Ngày tạo : "
                        + rs.getTimestamp("created_at")
                );
            }

            if (!hasUser) {

                System.out.println(
                        "📭 Chưa có user nào!"
                );
            }

            System.out.println(
                    "=================================================="
            );

        } catch (SQLException e) {

            System.err.println(
                    "❌ Lỗi xem danh sách user!"
            );

            e.printStackTrace();
        }
    }


    // =====================================
    // KIỂM TRA EMAIL
    // =====================================

    public boolean isEmailExists(String email) {

        if (isBlank(email)) {
            return false;
        }

        String sql =
                "SELECT 1 FROM users "
                + "WHERE email = ?";

        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps =
                     conn.prepareStatement(sql)) {

            ps.setString(1, email.trim());

            try (ResultSet rs = ps.executeQuery()) {

                return rs.next();
            }

        } catch (SQLException e) {

            e.printStackTrace();

            return false;
        }
    }


    // =====================================
    // KIỂM TRA USERNAME
    // =====================================

    public boolean isUsernameExists(String username) {

        if (isBlank(username)) {
            return false;
        }

        String sql =
                "SELECT 1 FROM users "
                + "WHERE username = ?";

        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps =
                     conn.prepareStatement(sql)) {

            ps.setString(1, username.trim());

            try (ResultSet rs = ps.executeQuery()) {

                return rs.next();
            }

        } catch (SQLException e) {

            e.printStackTrace();

            return false;
        }
    }


    // =====================================
    // KIỂM TRA USER ID
    // =====================================

    public boolean isUserExists(int userId) {

        String sql =
                "SELECT 1 FROM users "
                + "WHERE user_id = ?";

        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps =
                     conn.prepareStatement(sql)) {

            ps.setInt(1, userId);

            try (ResultSet rs = ps.executeQuery()) {

                return rs.next();
            }

        } catch (SQLException e) {

            e.printStackTrace();

            return false;
        }
    }


    // =====================================
    // THÊM USER DEMO
    // =====================================

    public boolean insertSampleUser() {

        return createUser(
                "lecturer01",
                "Giảng viên Demo",
                "lecturer01@university.edu.vn",
                "lecturer-demo",
                "123456",
                "LECTURER"
        );
    }


    // =====================================
    // KIỂM TRA CHUỖI RỖNG
    // =====================================

    private boolean isBlank(String value) {

        return value == null
                || value.trim().isEmpty();
    }
}

