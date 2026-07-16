package com.java2nb.novel.core.config;

import org.springframework.boot.web.server.ErrorPage;
import org.springframework.boot.web.server.ErrorPageRegistrar;
import org.springframework.boot.web.server.ErrorPageRegistry;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;

/**
 * Cấu hình trang lỗi
 * @author xiongxiaoyang
 */
@Configuration
public class ErrorPageConfig implements ErrorPageRegistrar {

    @Override
    public void registerErrorPages(ErrorPageRegistry registry) {
        /*1. Lỗi 404 mặc định hiển thị trang 404.html*/
        ErrorPage e404 = new ErrorPage(HttpStatus.NOT_FOUND, "/404.html");
        /**
        TODO 2. Lỗi 500 là lỗi phản hồi máy chủ, mặc định hiển thị /500.html.
        */
        registry.addErrorPages(e404);
    }
}
