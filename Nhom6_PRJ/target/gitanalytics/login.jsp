<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%
    if (session.getAttribute("authenticatedUser") != null) {
        response.sendRedirect(request.getContextPath() + "/index.jsp");
        return;
    }
%>
<jsp:include page="/login.html" />