package com.java2nb.common.exception;

import lombok.Data;

/**
 * Ngoại lệ nghiệp vụ tùy chỉnh
 */
@Data
public class BusinessException extends RuntimeException {
	private static final long serialVersionUID = 1L;
	
    private String msg;
    private int code;
    
    public BusinessException(int code,String msg) {
		//Không gọi fillInStackTrace() của Throwable để tạo stack trace, giúp tăng hiệu năng
		// Lời gọi giữa các hàm dựng phải nằm ở dòng đầu tiên
		super(msg, null, false, false);
		this.code = code;
		this.msg = msg;
	}
	

	
}
