package com.aita.gitanalytics.dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnection {
    // Thông tin kết nối CSDL (Cần thay đổi theo môi trường của bạn)
    private static final String URL = "jdbc:mysql://localhost:3306/AITA_DB?useSSL=false&serverTimezone=UTC";
    private static final String USER = "root";
    private static final String PASSWORD = "password"; // Đổi password thành mật khẩu thật

    public static Connection getConnection() {
        Connection conn = null;
        try {
            // Đăng ký Driver MySQL
            Class.forName("com.mysql.cj.jdbc.Driver");
            conn = DriverManager.getConnection(URL, USER, PASSWORD);
        } catch (ClassNotFoundException e) {
            System.err.println("Lỗi: Không tìm thấy MySQL Driver!");
            e.printStackTrace();
        } catch (SQLException e) {
            System.err.println("Lỗi kết nối CSDL MySQL!");
            e.printStackTrace();
        }
        return conn;
    }
}
