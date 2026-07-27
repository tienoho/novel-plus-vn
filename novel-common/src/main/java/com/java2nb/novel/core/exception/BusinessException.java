package com.java2nb.novel.core.exception;

import io.github.xxyopen.model.resp.IResultCode;

public class BusinessException extends io.github.xxyopen.web.exception.BusinessException {

    public BusinessException(String message) {
        super(new IResultCode() {
            @Override
            public int getCode() {
                return 500;
            }

            @Override
            public String getMsg() {
                return message;
            }
        });
    }

    public BusinessException(IResultCode resultCode) {
        super(resultCode);
    }
}
