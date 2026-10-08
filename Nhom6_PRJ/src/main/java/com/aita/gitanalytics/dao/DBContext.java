package com.aita.gitanalytics.dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBContext {
    private static final String SERVER_NAME = getSetting("AITA_DB_SERVER", "localhost");
    private static final String PORT = getSetting("AITA_DB_PORT", "1433");
    private static final String DATABASE_NAME = getSetting("AITA_DB_NAME", "AITA_DB");
    private static final String USER = getSetting("AITA_DB_USER", "sa");
    private static final String PASSWORD = getSetting("AITA_DB_PASSWORD", "123");

    // Chuỗi kết nối chuẩn cho SQL Server Driver
    private static final String URL = "jdbc:sqlserver://" + SERVER_NAME + ":" + PORT + ";"
            + "databaseName=" + DATABASE_NAME + ";"
            + "encrypt=false;trustServerCertificate=true;loginTimeout=5;";

    private static String getSetting(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.trim().isEmpty() ? defaultValue : value.trim();
    }

    public static Connection getConnection() {
        Connection conn = null;
        try {
            // Nạp Driver SQL Server
            Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
            conn = DriverManager.getConnection(URL, USER, PASSWORD);
        } catch (ClassNotFoundException e) {
            System.err.println("❌ Lỗi: Không tìm thấy Driver SQL Server (Thiếu thư viện mssql-jdbc)!");
            e.printStackTrace();
        } catch (SQLException e) {
            System.err.println("❌ Lỗi kết nối CSDL SQL Server! Kiểm tra lại User, Password hoặc Port 1433.");
            e.printStackTrace();
        }
        return conn;
    }

    // Hàm test nhanh kết nối (Bấm Run để kiểm tra)
    public static void main(String[] args) {
        Connection conn = getConnection();
        if (conn != null) {
            System.out.println("✅ KẾT NỐI CƠ SỞ DỮ LIỆU AITA_DB (SQL SERVER) THÀNH CÔNG!");
            try {
                conn.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            System.err.println("❌ KẾT NỐI THẤT BẠI!");
        }
    }
}
