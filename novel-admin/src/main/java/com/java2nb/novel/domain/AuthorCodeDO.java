package com.java2nb.novel.domain;

import java.io.Serializable;


import java.math.BigDecimal;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.java2nb.common.jsonserializer.LongToStringSerializer;


import org.springframework.format.annotation.DateTimeFormat;
import java.util.Date;



/**
 * Bảng mã mời tác giả
 * 
 * @author xiongxy
 * @email 1179705413@qq.com
 * @date 2020-05-13 11:29:15
 */
public class AuthorCodeDO implements Serializable {
	private static final long serialVersionUID = 1L;

	
	// Khóa chính
		// Kiểu long của Java có phạm vi lớn hơn number của JavaScript, nên một số giá trị không thể biểu diễn chính xác trong JavaScript
	// Vì vậy, giá trị được tuần tự hóa thành chuỗi để xử lý
	@JsonSerialize(using = LongToStringSerializer.class)
			private Long id;
	// Mã mời
			private String inviteCode;
	// Thời hạn hiệu lực
			@DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
		private Date validityTime;
	// Đã sử dụng: 0 chưa, 1 rồi
			private Integer isUse;
	// Thời gian tạo
			@DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
		private Date createTime;
	// ID người tạo
		// Kiểu long của Java có phạm vi lớn hơn number của JavaScript, nên một số giá trị không thể biểu diễn chính xác trong JavaScript
	// Vì vậy, giá trị được tuần tự hóa thành chuỗi để xử lý
	@JsonSerialize(using = LongToStringSerializer.class)
			private Long createUserId;

	/**
	 * Đặt: khóa chính
	 */
	public void setId(Long id) {
		this.id = id;
	}
	/**
	 * Lấy: khóa chính
	 */
	public Long getId() {
		return id;
	}
	/**
	 * Đặt: mã mời
	 */
	public void setInviteCode(String inviteCode) {
		this.inviteCode = inviteCode;
	}
	/**
	 * Lấy: mã mời
	 */
	public String getInviteCode() {
		return inviteCode;
	}
	/**
	 * Đặt: thời hạn hiệu lực
	 */
	public void setValidityTime(Date validityTime) {
		this.validityTime = validityTime;
	}
	/**
	 * Lấy: thời hạn hiệu lực
	 */
	public Date getValidityTime() {
		return validityTime;
	}
	/**
	 * Đặt: đã sử dụng: 0 chưa, 1 rồi
	 */
	public void setIsUse(Integer isUse) {
		this.isUse = isUse;
	}
	/**
	 * Lấy: đã sử dụng: 0 chưa, 1 rồi
	 */
	public Integer getIsUse() {
		return isUse;
	}
	/**
	 * Đặt: thời gian tạo
	 */
	public void setCreateTime(Date createTime) {
		this.createTime = createTime;
	}
	/**
	 * Lấy: thời gian tạo
	 */
	public Date getCreateTime() {
		return createTime;
	}
	/**
	 * Đặt: ID người tạo
	 */
	public void setCreateUserId(Long createUserId) {
		this.createUserId = createUserId;
	}
	/**
	 * Lấy: ID người tạo
	 */
	public Long getCreateUserId() {
		return createUserId;
	}
}
