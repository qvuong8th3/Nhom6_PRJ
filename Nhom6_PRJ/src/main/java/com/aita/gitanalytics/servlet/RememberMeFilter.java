package com.aita.gitanalytics.servlet;

import com.aita.gitanalytics.dao.UserDAO;
import com.aita.gitanalytics.dao.UserDAO.UserAccount;
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
            }
        }
        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
    }
}