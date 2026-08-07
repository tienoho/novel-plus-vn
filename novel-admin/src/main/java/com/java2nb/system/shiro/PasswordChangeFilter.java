package com.java2nb.system.shiro;

import com.java2nb.common.utils.Messages;
import com.java2nb.system.domain.UserDO;
import java.io.IOException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.web.filter.AccessControlFilter;
import org.apache.shiro.web.util.WebUtils;

/**
 * Giới hạn phiên quản trị ở màn hình đổi mật khẩu cho đến khi hoàn tất yêu cầu bootstrap.
 */
public class PasswordChangeFilter extends AccessControlFilter {

    private static final String PASSWORD_PAGE = "/sys/user/personal";
    private static final String PASSWORD_UPDATE = "/sys/user/resetPwd";
    private static final int HTTP_PRECONDITION_REQUIRED = 428;

    @Override
    protected boolean isAccessAllowed(ServletRequest request, ServletResponse response,
                                      Object mappedValue) {
        Subject subject = getSubject(request, response);
        Object principal = subject.getPrincipal();
        if (!(principal instanceof UserDO)) {
            return true;
        }
        UserDO user = (UserDO) principal;
        return !Boolean.TRUE.equals(user.getMustChangePassword()) || isPasswordChangeRequest(request);
    }

    @Override
    protected boolean onAccessDenied(ServletRequest request, ServletResponse response)
        throws IOException {
        HttpServletRequest httpRequest = WebUtils.toHttp(request);
        HttpServletResponse httpResponse = WebUtils.toHttp(response);
        if (isAjax(httpRequest) || !"GET".equalsIgnoreCase(httpRequest.getMethod())) {
            httpResponse.sendError(
                HTTP_PRECONDITION_REQUIRED,
                Messages.getDefault("auth.password.changeRequired"));
            return false;
        }
        WebUtils.issueRedirect(request, response, PASSWORD_PAGE);
        return false;
    }

    private boolean isPasswordChangeRequest(ServletRequest request) {
        HttpServletRequest httpRequest = WebUtils.toHttp(request);
        String contextPath = httpRequest.getContextPath();
        String path = httpRequest.getRequestURI().substring(contextPath.length());
        return (PASSWORD_PAGE.equals(path) && "GET".equalsIgnoreCase(httpRequest.getMethod()))
            || (PASSWORD_UPDATE.equals(path) && "POST".equalsIgnoreCase(httpRequest.getMethod()));
    }

    private boolean isAjax(HttpServletRequest request) {
        String requestedWith = request.getHeader("X-Requested-With");
        String accept = request.getHeader("Accept");
        return "XMLHttpRequest".equalsIgnoreCase(requestedWith)
            || (accept != null && accept.contains("application/json"));
    }
}
