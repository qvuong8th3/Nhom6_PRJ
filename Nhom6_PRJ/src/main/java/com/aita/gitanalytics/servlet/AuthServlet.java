package com.aita.gitanalytics.servlet;

import com.aita.gitanalytics.dao.DataAccessException;
import com.aita.gitanalytics.dao.UserDAO;
import com.aita.gitanalytics.model.UserAccount;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

@WebServlet(urlPatterns = {"/api/v1/auth/login", "/api/v1/auth/logout", "/api/v1/auth/register",
        "/api/v1/auth/change-password"})
public class AuthServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final Gson gson = new Gson();
    private final UserDAO userDAO = new UserDAO();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        if (request.getServletPath().endsWith("/register")) {
            registerStudent(request, response);
            return;
        }

        if (request.getServletPath().endsWith("/change-password")) {
            changePassword(request, response);
            return;
        }

        if (request.getServletPath().endsWith("/logout")) {
            RememberMeService.revoke(request, response, userDAO);
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().print("{\"status\":\"success\"}");
            return;
        }

        UserAccount account = userDAO.authenticate(
            request.getParameter("identifier") != null
                ? request.getParameter("identifier") : request.getParameter("email"),
                request.getParameter("password"));
        if (account == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().print("{\"error\":\"Email hoặc mật khẩu không đúng.\"}");
            return;
        }

        HttpSession existingSession = request.getSession(false);
        if (existingSession != null) {
            existingSession.invalidate();
        }
        request.getSession(true).setAttribute("authenticatedUser", account);
        boolean rememberRequested = "true".equalsIgnoreCase(request.getParameter("rememberMe"));
        boolean remembered = rememberRequested
                ? RememberMeService.issue(account, request, response, userDAO)
                : RememberMeService.revoke(request, response, userDAO);
        JsonObject result = new JsonObject();
        result.addProperty("status", "success");
        result.addProperty("remembered", rememberRequested && remembered);
        result.add("user", gson.toJsonTree(account));
        response.getWriter().print(gson.toJson(result));
    }

    private void registerStudent(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String password = request.getParameter("password");
        if (password == null || !password.equals(request.getParameter("confirmPassword"))) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST, "Mật khẩu xác nhận không khớp.");
            return;
        }

        try {
            boolean registered = userDAO.registerStudent(
                    request.getParameter("username"),
                    request.getParameter("fullName"),
                    request.getParameter("email"),
                    request.getParameter("githubUsername"),
                    password);
            if (!registered) {
                writeError(response, HttpServletResponse.SC_CONFLICT,
                        "Tên đăng nhập hoặc email đã được sử dụng.");
                return;
            }
            response.setStatus(HttpServletResponse.SC_CREATED);
            response.getWriter().print("{\"status\":\"success\"}");
        } catch (IllegalArgumentException e) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (DataAccessException e) {
            if (e.isConstraintViolation()) {
                writeError(response, HttpServletResponse.SC_CONFLICT,
                        "Tên đăng nhập hoặc email đã được sử dụng.");
            } else {
                e.printStackTrace();
                writeError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        "Không thể lưu tài khoản vào database lúc này.");
            }
        }
    }

    private void changePassword(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Object value = request.getSession(false) == null
                ? null : request.getSession(false).getAttribute("authenticatedUser");
        if (!(value instanceof UserAccount)) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "Vui lòng đăng nhập lại.");
            return;
        }

        String currentPassword = request.getParameter("currentPassword");
        String newPassword = request.getParameter("newPassword");
        String confirmPassword = request.getParameter("confirmPassword");
        if (currentPassword == null || currentPassword.isEmpty()) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST, "Vui lòng nhập mật khẩu hiện tại.");
            return;
        }
        if (currentPassword.length() > 128) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Mật khẩu hiện tại tối đa 128 ký tự.");
            return;
        }
        if (newPassword == null || newPassword.length() < 8 || newPassword.length() > 128) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Mật khẩu mới phải có từ 8 đến 128 ký tự.");
            return;
        }
        if (!newPassword.equals(confirmPassword)) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST, "Mật khẩu xác nhận không khớp.");
            return;
        }
        if (currentPassword.equals(newPassword)) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Mật khẩu mới phải khác mật khẩu hiện tại.");
            return;
        }

        try {
            UserAccount account = (UserAccount) value;
            if (!userDAO.changePassword(account.getUserId(), currentPassword, newPassword)) {
                writeError(response, HttpServletResponse.SC_BAD_REQUEST, "Mật khẩu hiện tại không đúng.");
                return;
            }
            RememberMeService.revoke(request, response, userDAO);
            request.getSession(false).setAttribute("authenticatedUser",
                    new UserAccount(account.getUserId(), account.getUsername(), account.getFullName(),
                            account.getEmail(), account.getRole(), false));
            response.getWriter().print("{\"status\":\"success\"}");
        } catch (DataAccessException e) {
            e.printStackTrace();
            writeError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Không thể cập nhật mật khẩu lúc này.");
        }
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        JsonObject error = new JsonObject();
        error.addProperty("error", message);
        response.setStatus(status);
        response.getWriter().print(gson.toJson(error));
    }
}