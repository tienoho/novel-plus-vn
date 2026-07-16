package com.java2nb.novel.domain;

import java.io.Serializable;


import java.math.BigDecimal;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.java2nb.common.jsonserializer.LongToStringSerializer;


import org.springframework.format.annotation.DateTimeFormat;
import java.util.Date;



/**
 * Bảng tác giả
 * 
 * @author xiongxy
 * @email 1179705413@qq.com
 * @date 2020-05-13 11:16:51
 */
public class AuthorDO implements Serializable {
	private static final long serialVersionUID = 1L;

	
	// Khóa chính
		// Kiểu long của Java có phạm vi lớn hơn number của JavaScript, nên một số giá trị không thể biểu diễn chính xác trong JavaScript
	// Vì vậy, giá trị được tuần tự hóa thành chuỗi để xử lý
	@JsonSerialize(using = LongToStringSerializer.class)
			private Long id;
	// ID người dùng
		// Kiểu long của Java có phạm vi lớn hơn number của JavaScript, nên một số giá trị không thể biểu diễn chính xác trong JavaScript
	// Vì vậy, giá trị được tuần tự hóa thành chuỗi để xử lý
	@JsonSerialize(using = LongToStringSerializer.class)
			private Long userId;
	// Mã mời
			private String inviteCode;
	// Bút danh
			private String penName;
	// Số điện thoại
			private String telPhone;
	// Tài khoản QQ hoặc WeChat
			private String chatAccount;
	// Email
			private String email;
	//Định hướng tác phẩm: 0 nam, 1 nữ
			private Integer workDirection;
	// Thời gian tạo
			@DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
		private Date createTime;
	// 0 bình thường, 1 bị khóa
			private Integer status;

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
	 * Đặt: ID người dùng
	 */
	public void setUserId(Long userId) {
		this.userId = userId;
	}
	/**
	 * Lấy: ID người dùng
	 */
	public Long getUserId() {
		return userId;
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
	 * Đặt: bút danh
	 */
	public void setPenName(String penName) {
		this.penName = penName;
	}
	/**
	 * Lấy: bút danh
	 */
	public String getPenName() {
		return penName;
	}
	/**
	 * Đặt: số điện thoại
	 */
	public void setTelPhone(String telPhone) {
		this.telPhone = telPhone;
	}
	/**
	 * Lấy: số điện thoại
	 */
	public String getTelPhone() {
		return telPhone;
	}
	/**
	 * Đặt: tài khoản QQ hoặc WeChat
	 */
	public void setChatAccount(String chatAccount) {
		this.chatAccount = chatAccount;
	}
	/**
	 * Lấy: tài khoản QQ hoặc WeChat
	 */
	public String getChatAccount() {
		return chatAccount;
	}
	/**
	 * Đặt: email
	 */
	public void setEmail(String email) {
		this.email = email;
	}
	/**
	 * Lấy: email
	 */
	public String getEmail() {
		return email;
	}
	/**
	 * Đặt: định hướng tác phẩm: 0 nam, 1 nữ
	 */
	public void setWorkDirection(Integer workDirection) {
		this.workDirection = workDirection;
	}
	/**
	 * Lấy: định hướng tác phẩm: 0 nam, 1 nữ
	 */
	public Integer getWorkDirection() {
		return workDirection;
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
	 * Đặt: 0 bình thường, 1 bị khóa
	 */
	public void setStatus(Integer status) {
		this.status = status;
	}
	/**
	 * Lấy: 0 bình thường, 1 bị khóa
	 */
	public Integer getStatus() {
		return status;
	}
}
