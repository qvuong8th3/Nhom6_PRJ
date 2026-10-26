package com.aita.gitanalytics.dao;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

public class DBContext {

    private static final Properties PROPS = loadConfiguration();

    // 1. Cấu hình CSDL Online trên Web / Cloud Database
    private static final String CLOUD_URL = getSetting("AITA_DB_URL", "db.url", null);

    // 2. Cấu hình CSDL Server nội bộ
    private static final String SERVER_NAME = getSetting("AITA_DB_SERVER", "db.server", "localhost");
    private static final String CONFIGURED_PORT = getSetting("AITA_DB_PORT", "db.port", "1433");
    private static final String DATABASE_NAME = getSetting("AITA_DB_NAME", "db.name", "AITA_DB");
    private static final String USER = getSetting("AITA_DB_USER", "db.user", "sa");
    private static final String PASSWORD = getSetting("AITA_DB_PASSWORD", "db.password", "123");

    // Port hoạt động hiện tại (tự động điều chỉnh nếu phát hiện port thay thế)
    private static volatile String activePort = CONFIGURED_PORT;

    static {
        try {
            Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        } catch (ClassNotFoundException e) {
            System.err.println("❌ Lỗi: Không tìm thấy Driver SQL Server (Thiếu thư viện mssql-jdbc)!");
            e.printStackTrace();
        }
    }

    private static Properties loadConfiguration() {
        Properties props = new Properties();
        // 1. Đọc từ classpath (/db.properties)
        try (InputStream is = DBContext.class.getResourceAsStream("/db.properties")) {
            if (is != null) {
                props.load(is);
            }
        } catch (Exception ignored) {}

        // 2. Nếu có file db.properties ở thư mục làm việc bên ngoài, ghi đè
        File external = new File("db.properties");
        if (external.exists() && external.isFile()) {
            try (FileInputStream fis = new FileInputStream(external)) {
                props.load(fis);
            } catch (Exception ignored) {}
        }
        return props;
    }

    private static String getSetting(String envKey, String propKey, String defaultValue) {
        String envVal = System.getenv(envKey);
        if (envVal != null && !envVal.trim().isEmpty()) {
            return envVal.trim();
        }
        String sysVal = System.getProperty(envKey);
        if (sysVal != null && !sysVal.trim().isEmpty()) {
            return sysVal.trim();
        }
        if (propKey != null && PROPS.containsKey(propKey)) {
            String propVal = PROPS.getProperty(propKey);
            if (propVal != null && !propVal.trim().isEmpty()) {
                return propVal.trim();
            }
        }
        return defaultValue;
    }

    private static String buildJdbcUrl(String server, String port, String dbName) {
        return "jdbc:sqlserver://" + server + ":" + port + ";"
                + "databaseName=" + dbName + ";"
                + "encrypt=false;trustServerCertificate=true;loginTimeout=5;";
    }

    public static Connection getConnection() {
        // TH1: Kết nối Cloud / Web Database dùng chung qua AITA_DB_URL hoặc db.url
        if (CLOUD_URL != null && !CLOUD_URL.trim().isEmpty()) {
            try {
                Connection conn = DriverManager.getConnection(CLOUD_URL);
                configureSession(conn);
                return conn;
            } catch (SQLException e) {
                System.err.println("❌ Lỗi kết nối CSDL Cloud (db.url): " + e.getMessage());
            }
        }

        // TH2: Kết nối máy chủ cụ thể (mặc định localhost)
        String currentPort = activePort;
        String url = buildJdbcUrl(SERVER_NAME, currentPort, DATABASE_NAME);

        try {
            Connection conn = DriverManager.getConnection(url, USER, PASSWORD);
            configureSession(conn);
            return conn;
        } catch (SQLException ex) {
            // Tự động kiểm tra cổng 1434 nếu đang kết nối localhost:1433 hoặc ngược lại
            if (isLocalhost(SERVER_NAME)) {
                String fallbackPort = "1433".equals(currentPort) ? "1434" : "1433";
                String fallbackUrl = buildJdbcUrl(SERVER_NAME, fallbackPort, DATABASE_NAME);
                try {
                    Connection conn = DriverManager.getConnection(fallbackUrl, USER, PASSWORD);
                    configureSession(conn);
                    activePort = fallbackPort;
                    System.out.println("ℹ️ [DBContext] Tự động phát hiện SQL Server tại cổng " + fallbackPort + "!");
                    return conn;
                } catch (SQLException ignored) {
                    // Cả 2 cổng đều không thành công
                }
            }

            System.err.println("❌ Lỗi kết nối CSDL SQL Server! Kiểm tra lại User, Password hoặc Port.");
            System.err.println("   URL đang thử: " + url + " (User: " + USER + ")");
            System.err.println("   Chi tiết lỗi: " + ex.getMessage());
        }
        return null;
    }

    private static boolean isLocalhost(String host) {
        return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host);
    }

    private static void configureSession(Connection conn) {
        if (conn != null) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("SET QUOTED_IDENTIFIER ON;");
            } catch (SQLException ignored) {}
        }
    }

    // Hàm test nhanh kết nối (Bấm Run để kiểm tra)
    public static void main(String[] args) {
        System.out.println("🔍 Đang kiểm tra kết nối đến cơ sở dữ liệu...");
        Connection conn = getConnection();
        if (conn != null) {
            System.out.println("✅ KẾT NỐI CƠ SỞ DỮ LIỆU AITA_DB (SQL SERVER) THÀNH CÔNG!");
            try {
                conn.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            System.err.println("❌ KẾT NỐI THẤT BẠI! Vui lòng kiểm tra file db.properties hoặc trạng thái SQL Server.");
        }
    }
}
