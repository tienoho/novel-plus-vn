package com.java2nb.common.utils;

import jakarta.servlet.http.HttpServletRequest;

public class HttpServletUtils {
    public static boolean jsAjax(HttpServletRequest req){
        //Kiểm tra có phải yêu cầu Ajax hay không; mặc định là không
        boolean isAjaxRequest = false;
        if(!StringUtils.isBlank(req.getHeader("x-requested-with")) && req.getHeader("x-requested-with").equals("XMLHttpRequest")){
            isAjaxRequest = true;
        }
        return isAjaxRequest;
    }
}
