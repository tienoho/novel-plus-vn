package com.java2nb.novel.core.advice;

import com.java2nb.novel.core.i18n.Messages;
import io.github.xxyopen.model.resp.RestResult;
import io.github.xxyopen.model.resp.SysResultCode;
import io.github.xxyopen.web.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.ModelAndView;

/**
 * Bộ xử lý ngoại lệ thống nhất
 *
 * @author xiongxiaoyang
 */
@Slf4j
@RestControllerAdvice
@ConditionalOnProperty(name = "novel.common.web-advice.enabled", havingValue = "true", matchIfMissing = true)
public class CommonExceptionHandler {

    private final Messages messages;

    public CommonExceptionHandler(Messages messages) {
        this.messages = messages;
    }

    @ExceptionHandler({BindException.class})
    public RestResult<Void> handlerBindException(BindException e) {
        log.warn("Dữ liệu không hợp lệ cho đối tượng {}", e.getObjectName());
        String validationMessage = e.getAllErrors().stream()
            .map(error -> error.getDefaultMessage())
            .filter(message -> message != null && !message.isBlank())
            .findFirst()
            .orElseGet(() -> messages.get("validation.failed"));
        RestResult<Void> result = RestResult.fail(SysResultCode.PARAM_ERROR);
        result.setMsg(validationMessage);
        return result;
    }

    @ExceptionHandler({BusinessException.class})
    public RestResult<Void> handlerBusinessException(BusinessException e) {
        log.error(e.getMessage(), e);
        return RestResult.fail(e.getResultCode());
    }

    @ExceptionHandler(Exception.class)
    public Object handleException(HttpServletRequest request, Exception e) {
        log.error(e.getMessage(), e);
        if (isJsonRequest(request)) {
            // Nếu là yêu cầu REST, trả phản hồi lỗi JSON
            RestResult<Void> result = RestResult.error();
            result.setMsg(messages.get("error.internal"));
            return result;
        } else {
            //Khi chuyển trang gặp lỗi, chuyển thống nhất tới trang 404
            return new ModelAndView("404");
        }
    }

    private boolean isJsonRequest(HttpServletRequest request) {
        String acceptHeader = request.getHeader("Accept");
        return acceptHeader != null && acceptHeader.contains(MediaType.APPLICATION_JSON_VALUE);
    }

}
