package com.aita.gitanalytics.dao;

import java.sql.SQLException;

public final class DataAccessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DataAccessException(String message, SQLException cause) {
        super(message, cause);
    }

    public boolean isConstraintViolation() {
        Throwable cause = getCause();
        while (cause != null) {
            if (cause instanceof SQLException) {
                String sqlState = ((SQLException) cause).getSQLState();
                return sqlState != null && sqlState.startsWith("23");
            }
            cause = cause.getCause();
        }
        return false;
    }
}