<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" import="java.io.InputStream,java.nio.charset.StandardCharsets" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<c:if test="${empty sessionScope.authenticatedUser}">
    <c:redirect url="/login.jsp" />
</c:if>
<%
    if (session.getAttribute("authenticatedUser") == null) {
        return;
    }
    String pageContent;
    try (InputStream source = application.getResourceAsStream("/index.html")) {
        if (source == null) {
            throw new java.io.IOException("Dashboard page resource is missing.");
        }
        pageContent = new String(source.readAllBytes(), StandardCharsets.UTF_8);
    }
%>
<%= pageContent %>