package com.java2nb.common.xss;


import org.apache.commons.lang3.StringUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import java.util.Arrays;
import java.util.List;

public class XssAndSqlHttpServletRequestWrapper extends HttpServletRequestWrapper {
    private HttpServletRequest request;

    /**
     * Nếu tự gửi mã HTML, cần cấu hình name tương ứng để bỏ qua bộ lọc
     */
    private static final List<String> noFilterNames = Arrays.asList("attach","push_ip","content");

    public XssAndSqlHttpServletRequestWrapper(HttpServletRequest request) {
        super(request);
        this.request = request;
    }


    @Override
    public String getParameter(String name) {
        String value = request.getParameter(name);
        if (!StringUtils.isEmpty(value) && !noFilterNames.contains(name)) {
            value = htmlEncodeByRegExp(value);
        }
        return value;
    }

    @Override
    public String[] getParameterValues(String name) {
        String[] parameterValues = super.getParameterValues(name);
        if (parameterValues == null || noFilterNames.contains(name)) {
            return parameterValues;
        }
        for (int i = 0; i < parameterValues.length; i++) {
            String value = parameterValues[i];
            if (!StringUtils.isEmpty(value)){
                parameterValues[i] = htmlEncodeByRegExp(value);
            }
        }
        return parameterValues;
    }


    private String htmlEncodeByRegExp(String str) {
        String temp = "" ;
        temp = str
                //.replaceAll("&", "&amp;")
                .replaceAll("<", "&lt;")
                .replaceAll(">", "&gt;");
                //.replaceAll("\\s", "&nbsp;")
                //.replaceAll("\'", "&#39;")
                //.replaceAll("\"", "&quot;");
        return temp;
    }
}
