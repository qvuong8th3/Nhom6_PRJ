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
        try {
            Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");

            Connection connection = DriverManager.getConnection(
                    URL,
                    USER,
                    PASSWORD
            );

            System.out.println("KET NOI SQL SERVER THANH CONG!");

            return connection;

        } catch (ClassNotFoundException e) {
            System.err.println("Khong tim thay SQL Server JDBC Driver!");
            e.printStackTrace();

        } catch (SQLException e) {
            System.err.println("Loi ket noi SQL Server!");
            e.printStackTrace();
        }

        return null;
    }

    public static void main(String[] args) {

        Connection connection = getConnection();

        if (connection != null) {
            System.out.println("KET NOI AITA_DB THANH CONG!");

            try {
                connection.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }

        } else {
            System.out.println("KET NOI THAT BAI!");
        }
    }
}