package com.aita.gitanalytics.dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBContext {

    private static final String SERVER_NAME = "localhost";
    private static final String INSTANCE_NAME = "MSSQLSERVER01";
    private static final String DATABASE_NAME = "AITA_DB";
    private static final String USER = "sa";
    private static final String PASSWORD = "Aita@12345";

    private static final String URL =
            "jdbc:sqlserver://" + SERVER_NAME
            + ";instanceName=" + INSTANCE_NAME
            + ";databaseName=" + DATABASE_NAME
            + ";encrypt=false"
            + ";trustServerCertificate=true";

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