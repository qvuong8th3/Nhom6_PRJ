package com.aita.gitanalytics.servlet;

import com.aita.gitanalytics.dao.UserDAO;
import com.aita.gitanalytics.model.UserAccount;
import java.io.IOException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebFilter("/*")
public class RememberMeFilter implements Filter {

    private final UserDAO userDAO = new UserDAO();

    @Override
    public void init(FilterConfig filterConfig) {
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
                         FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;
        Object account = request.getSession(false) == null
                ? null : request.getSession(false).getAttribute("authenticatedUser");
        if (!(account instanceof UserAccount)) {
            UserAccount remembered = RememberMeService.restore(request, response, userDAO);
            if (remembered != null) {
                request.getSession(true).setAttribute("authenticatedUser", remembered);
                account = remembered;
            }
        }
        if (account instanceof UserAccount
                && ((UserAccount) account).isMustChangePassword()
                && request.getServletPath().startsWith("/api/v1/")
                && !"/api/v1/auth/change-password".equals(request.getServletPath())
                && !"/api/v1/auth/logout".equals(request.getServletPath())) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().print("{\"error\":\"Bạn phải đổi mật khẩu trước khi tiếp tục.\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
    }
}