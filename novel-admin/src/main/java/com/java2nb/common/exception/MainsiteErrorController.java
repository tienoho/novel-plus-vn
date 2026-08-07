package com.java2nb.common.exception;


import com.java2nb.common.utils.R;
import com.java2nb.common.utils.Messages;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.error.ErrorAttributes;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RestController
public class MainsiteErrorController implements ErrorController {

    private static final String ERROR_PATH = "/error";

    @Autowired
    ErrorAttributes errorAttributes;
    @Autowired
    Messages messages;

    @RequestMapping(
        value = {ERROR_PATH},
        produces = {"text/html"}
    )
    public ModelAndView errorHtml(HttpServletRequest request, HttpServletResponse response) {
        int code = response.getStatus();
        if (404 == code) {
            return new ModelAndView("error/404");
        } else if (403 == code) {
            return new ModelAndView("error/403");
        } else if (401 == code) {
            return new ModelAndView("login");
        } else {
            return new ModelAndView("error/500");
        }

    }

    @RequestMapping(value = ERROR_PATH)
    public R handleError(HttpServletRequest request, HttpServletResponse response) {
        response.setStatus(200);
        int code = response.getStatus();
        if (404 == code) {
            return R.error(404, messages.get("error.notFound"));
        } else if (403 == code) {
            return R.error(403, messages.get("error.accessDenied"));
        } else if (401 == code) {
            return R.error(403, messages.get("error.loginExpired"));
        } else {
            return R.error(500, messages.get("error.internal"));
        }
    }

}
