package com.aita.gitanalytics.servlet;

import com.aita.gitanalytics.model.UserAccount;
import com.aita.gitanalytics.service.PedagogicalGuidanceService;
import com.aita.gitanalytics.service.PedagogicalGuidanceService.PedagogicalFeedback;
import com.google.gson.Gson;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet("/api/v1/pedagogical/*")
public class PedagogicalServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final Gson gson = new Gson();
    private final PedagogicalGuidanceService guidanceService = new PedagogicalGuidanceService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        doPost(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();

        UserAccount account = getAuthenticatedUser(request, response, out);
        if (account == null) return;

        String rawError = request.getParameter("rawError");
        String codeSnippet = request.getParameter("codeSnippet");
        String studentName = account.getFullName();

        if (rawError == null || rawError.trim().isEmpty()) {
            rawError = "javac error: cannot find symbol\n  symbol:   variable userDAO\n  location: class com.aita.gitanalytics.servlet.AuthServlet";
        }

        PedagogicalFeedback feedback = guidanceService.transformError(rawError, codeSnippet, studentName);

        out.print(gson.toJson(Map.of("status", "success", "data", feedback)));
    }

    private UserAccount getAuthenticatedUser(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
        Object user = request.getSession(false) == null
                ? null : request.getSession(false).getAttribute("authenticatedUser");
        if (!(user instanceof UserAccount)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            out.print("{\"error\":\"Vui lòng đăng nhập lại.\"}");
            return null;
        }
        return (UserAccount) user;
    }
}
