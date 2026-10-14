package com.aita.gitanalytics.servlet;

import com.aita.gitanalytics.dao.DataAccessException;
import com.aita.gitanalytics.dao.UserDAO;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

@WebListener
public class AdministratorBootstrapListener implements ServletContextListener {

    private static final String USERNAME = "AITA_ADMIN_USERNAME";
    private static final String FULL_NAME = "AITA_ADMIN_FULL_NAME";
    private static final String EMAIL = "AITA_ADMIN_EMAIL";
    private static final String PASSWORD = "AITA_ADMIN_PASSWORD";

    @Override
    public void contextInitialized(ServletContextEvent event) {
        String username = System.getenv(USERNAME);
        String fullName = System.getenv(FULL_NAME);
        String email = System.getenv(EMAIL);
        String password = System.getenv(PASSWORD);
        if (isBlank(username) && isBlank(fullName) && isBlank(email) && isBlank(password)) {
            event.getServletContext().log("Bootstrap administrator is not configured.");
            return;
        }
        if (isBlank(username) || isBlank(fullName) || isBlank(email) || isBlank(password)) {
            event.getServletContext().log(
                    "Administrator bootstrap skipped: configure all AITA_ADMIN_* environment variables.");
            return;
        }
        try {
            boolean created = new UserDAO().createBootstrapAdministrator(username, fullName, email, password);
            event.getServletContext().log(created
                    ? "Bootstrap administrator account created; password change is required at first login."
                    : "Bootstrap administrator was not created because an administrator already exists.");
        } catch (IllegalArgumentException | DataAccessException e) {
            event.getServletContext().log("Administrator bootstrap failed: " + e.getMessage(), e);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
