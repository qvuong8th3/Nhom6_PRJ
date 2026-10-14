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
import java.util.Locale;

public class UserDAO {

    public UserAccount authenticate(String identifier, String password) {
        if (isBlank(identifier) || isBlank(password)) {
            return null;
        }

        try (Connection conn = DBContext.getConnection()) {
            if (conn == null) {
                return null;
            }
            int userId;
            String username;
            String fullName;
            String email;
            String role;
            boolean mustChangePassword;
            String storedPassword;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT user_id, username, full_name, email, role, password_hash, must_change_password "
                            + "FROM users WHERE email = ? OR username = ?")) {
                ps.setString(1, identifier.trim());
                ps.setString(2, identifier.trim());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return null;
                    }
                    userId = rs.getInt("user_id");
                    username = rs.getString("username");
                    fullName = rs.getString("full_name");
                    email = rs.getString("email");
                    role = rs.getString("role");
                    mustChangePassword = rs.getBoolean("must_change_password");
                    storedPassword = rs.getString("password_hash");
                }
            }
            if (!PasswordHasher.verify(password, storedPassword)) {
                return null;
            }
            if (PasswordHasher.needsUpgrade(storedPassword)) {
                upgradeLegacyPassword(conn, userId, password, storedPassword);
            }
            return new UserAccount(userId, username, fullName, email, role, mustChangePassword);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void upgradeLegacyPassword(Connection conn, int userId, String password, String storedPassword)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE users SET password_hash = ? WHERE user_id = ? AND password_hash = ?")) {
            ps.setString(1, PasswordHasher.hash(password));
            ps.setInt(2, userId);
            ps.setString(3, storedPassword);
            ps.executeUpdate();
        }
    }

    public boolean createBootstrapAdministrator(String username, String fullName, String email,
                                                String initialPassword) {
        validateNewAccount(username, fullName, email, initialPassword);
        String sql = "INSERT INTO Users (username, password_hash, full_name, email, role, "
                + "must_change_password, created_at) VALUES (?, ?, ?, ?, 'ADMIN', 1, GETDATE())";
        try (Connection conn = DBContext.getConnection()) {
            if (conn == null) {
                throw new DataAccessException("Database connection is unavailable.", null);
            }
            conn.setAutoCommit(false);
            try (PreparedStatement check = conn.prepareStatement(
                    "SELECT TOP (1) user_id FROM Users WITH (UPDLOCK, HOLDLOCK) WHERE role = 'ADMIN'");
                 ResultSet rs = check.executeQuery()) {
                if (rs.next()) {
                    conn.rollback();
                    return false;
                }
                try (PreparedStatement insert = conn.prepareStatement(sql)) {
                    bindNewAccount(insert, username, fullName, email, initialPassword);
                    insert.executeUpdate();
                }
                conn.commit();
                return true;
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not create bootstrap administrator.", e);
        }
    }

    public boolean createLecturer(String username, String fullName, String email, String initialPassword) {
        validateNewAccount(username, fullName, email, initialPassword);
        String sql = "INSERT INTO Users (username, password_hash, full_name, email, role, "
                + "must_change_password, created_at) VALUES (?, ?, ?, ?, 'LECTURER', 1, GETDATE())";
        try (Connection conn = DBContext.getConnection()) {
            if (conn == null) {
                throw new DataAccessException("Database connection is unavailable.", null);
            }
            try (PreparedStatement insert = conn.prepareStatement(sql)) {
                bindNewAccount(insert, username, fullName, email, initialPassword);
                return insert.executeUpdate() == 1;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not create lecturer account.", e);
        }
    }

    private void validateNewAccount(String username, String fullName, String email, String password) {
        if (isBlank(username) || !username.trim().matches("[A-Za-z0-9._-]{3,50}")) {
            throw new IllegalArgumentException(
                    "Tên đăng nhập phải có 3-50 ký tự chữ, số, dấu chấm, gạch dưới hoặc gạch ngang.");
        }
        if (isBlank(fullName) || fullName.trim().length() > 100) {
            throw new IllegalArgumentException("Họ và tên bắt buộc và tối đa 100 ký tự.");
        }
        if (isBlank(email) || email.trim().length() > 100
                || !email.trim().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {
            throw new IllegalArgumentException("Email không hợp lệ.");
        }
        if (password == null || password.length() < 12 || password.length() > 128) {
            throw new IllegalArgumentException("Mật khẩu khởi tạo phải có từ 12 đến 128 ký tự.");
        }
    }

    private void bindNewAccount(PreparedStatement statement, String username, String fullName,
                                String email, String password) throws SQLException {
        statement.setString(1, username.trim());
        statement.setString(2, PasswordHasher.hash(password));
        statement.setString(3, fullName.trim());
        statement.setString(4, email.trim().toLowerCase(Locale.ROOT));
    }

    public boolean changePassword(int userId, String currentPassword, String newPassword) {
        try (Connection conn = DBContext.getConnection()) {
            if (conn == null) {
                throw new DataAccessException("Database connection is unavailable.", null);
            }
            conn.setAutoCommit(false);
            try {
                String storedPassword;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT password_hash FROM users WITH (UPDLOCK, ROWLOCK) WHERE user_id = ?")) {
                    ps.setInt(1, userId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            return false;
                        }
                        storedPassword = rs.getString("password_hash");
                    }
                }
                if (!PasswordHasher.verify(currentPassword, storedPassword)) {
                    conn.rollback();
                    return false;
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE users SET password_hash = ?, must_change_password = 0 WHERE user_id = ?")) {
                    ps.setString(1, PasswordHasher.hash(newPassword));
                    ps.setInt(2, userId);
                    if (ps.executeUpdate() != 1) {
                        conn.rollback();
                        return false;
                    }
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM remember_tokens WHERE user_id = ?")) {
                    ps.setInt(1, userId);
                    ps.executeUpdate();
                }
                conn.commit();
                return true;
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not change password.", e);
        }
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
        String sql = "SELECT u.user_id, u.username, u.full_name, u.email, u.role, u.must_change_password "
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
                                rs.getString("full_name"), rs.getString("email"), rs.getString("role"),
                                rs.getBoolean("must_change_password"));
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
                + "FROM Users WHERE role = 'STUDENT' ORDER BY full_name";

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
        String sql = "UPDATE Users SET username = ?, "
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
                    ps.setString(index++, PasswordHasher.hash(password));
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

    public boolean registerStudent(String username,
                                   String fullName,
                                   String email,
                                   String githubUsername,
                                   String password) {
        String normalizedUsername = username == null ? null : username.trim();
        String normalizedFullName = fullName == null ? null : fullName.trim();
        String normalizedEmail = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
        String normalizedGithubUsername = githubUsername == null ? null : githubUsername.trim();

        if (isBlank(normalizedUsername)
                || !normalizedUsername.matches("[A-Za-z0-9._-]{3,50}")) {
            throw new IllegalArgumentException("Tên đăng nhập phải có 3-50 ký tự chữ, số, dấu chấm, gạch dưới hoặc gạch ngang.");
        }
        if (isBlank(normalizedFullName) || normalizedFullName.length() > 100) {
            throw new IllegalArgumentException("Họ và tên không được để trống và tối đa 100 ký tự.");
        }
        if (isBlank(normalizedEmail)
                || normalizedEmail.length() > 100
                || !normalizedEmail.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {
            throw new IllegalArgumentException("Email không hợp lệ.");
        }
        if (!isBlank(normalizedGithubUsername) && normalizedGithubUsername.length() > 50) {
            throw new IllegalArgumentException("GitHub username tối đa 50 ký tự.");
        }
        if (password == null || password.length() < 8 || password.length() > 128) {
            throw new IllegalArgumentException("Mật khẩu phải có từ 8 đến 128 ký tự.");
        }

        return createUser(normalizedUsername, normalizedFullName, normalizedEmail,
                normalizedGithubUsername, password, "STUDENT");
    }

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
                "SELECT 1 FROM Users "
                + "WHERE username = ? OR email = ?";

        String insertSql =
                "INSERT INTO Users "
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
                insertPs.setString(2, PasswordHasher.hash(password));
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
                "UPDATE Users "
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
                + "FROM Users "
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
                "DELETE FROM Users "
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
                + "FROM Users "
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
                "SELECT 1 FROM Users "
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
                "SELECT 1 FROM Users "
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
