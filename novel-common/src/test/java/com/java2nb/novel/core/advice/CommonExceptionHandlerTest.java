package com.java2nb.novel.core.advice;

import com.java2nb.novel.core.i18n.Messages;
import io.github.xxyopen.model.resp.RestResult;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommonExceptionHandlerTest {

    private final Messages messages = mock(Messages.class);
    private final CommonExceptionHandler handler = new CommonExceptionHandler(messages);

    @Test
    void validationUsesResolvedVietnameseMessageInsteadOfDependencyDefault() {
        BindException exception = new BindException(new Object(), "search");
        exception.addError(new FieldError("search", "keyword", "Từ khóa không được vượt quá 100 ký tự."));

        RestResult<Void> result = handler.handlerBindException(exception);

        assertThat(result.getCode()).isEqualTo(400);
        assertThat(result.getMsg()).isEqualTo("Từ khóa không được vượt quá 100 ký tự.");
    }

    @Test
    void validationFallsBackToCommonVietnameseCatalog() {
        when(messages.get("validation.failed")).thenReturn("Dữ liệu không hợp lệ");
        BindException exception = new BindException(new Object(), "search");
        exception.addError(new FieldError("search", "keyword", null));

        assertThat(handler.handlerBindException(exception).getMsg()).isEqualTo("Dữ liệu không hợp lệ");
    }

    @Test
    void genericJsonErrorUsesCommonVietnameseCatalog() {
        when(messages.get("error.internal")).thenReturn("Hệ thống đang bận, vui lòng thử lại sau");
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Accept")).thenReturn(MediaType.APPLICATION_JSON_VALUE);

        Object result = handler.handleException(request, new IllegalStateException("boom"));

        assertThat(result).isInstanceOf(RestResult.class);
        assertThat(((RestResult<?>) result).getMsg()).isEqualTo("Hệ thống đang bận, vui lòng thử lại sau");
    }
}

