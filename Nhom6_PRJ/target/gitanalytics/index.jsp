<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" import="java.io.InputStream,java.nio.charset.StandardCharsets" %>
<%
    if (session.getAttribute("authenticatedUser") == null) {
        response.sendRedirect(request.getContextPath() + "/login.jsp");
        return;
    }
%>
<%
    String pageContent;
    try (InputStream source = application.getResourceAsStream("/index.html")) {
        if (source == null) {
            throw new java.io.IOException("Dashboard page resource is missing.");
        }
        pageContent = new String(source.readAllBytes(), StandardCharsets.UTF_8);
    }
%>
<%= pageContent %>