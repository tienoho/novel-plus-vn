package com.java2nb.novel.domain;

import java.io.Serializable;


import java.math.BigDecimal;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.java2nb.common.jsonserializer.LongToStringSerializer;


import org.springframework.format.annotation.DateTimeFormat;
import java.util.Date;



/**
 * 
 * 
 * @author xiongxy
 * @email 1179705413@qq.com
 * @date 2020-12-01 03:49:08
 */
public class UserDO implements Serializable {
	private static final long serialVersionUID = 1L;

	
	// Khóa chính
		// Kiểu long của Java có phạm vi lớn hơn number của JavaScript, nên một số giá trị không thể biểu diễn chính xác trong JavaScript
	// Vì vậy, giá trị được tuần tự hóa thành chuỗi để xử lý
	@JsonSerialize(using = LongToStringSerializer.class)
			private Long id;
	// Tên đăng nhập
			private String username;
	// Mật khẩu đăng nhập
			private String password;
	// Biệt danh
			private String nickName;
	// Ảnh đại diện người dùng
			private String userPhoto;
	// Giới tính người dùng: 0 nam, 1 nữ
			private Integer userSex;
	// Số dư tài khoản
		// Kiểu long của Java có phạm vi lớn hơn number của JavaScript, nên một số giá trị không thể biểu diễn chính xác trong JavaScript
	// Vì vậy, giá trị được tuần tự hóa thành chuỗi để xử lý
	@JsonSerialize(using = LongToStringSerializer.class)
			private Long accountBalance;
	// Trạng thái người dùng: 0 bình thường
			private Integer status;
	// Thời gian tạo
			@DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
		private Date createTime;
	// Thời gian cập nhật
			@DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
		private Date updateTime;

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
	 * Đặt: tên đăng nhập
	 */
	public void setUsername(String username) {
		this.username = username;
	}
	/**
	 * Lấy: tên đăng nhập
	 */
	public String getUsername() {
		return username;
	}
	/**
	 * Đặt: mật khẩu đăng nhập
	 */
	public void setPassword(String password) {
		this.password = password;
	}
	/**
	 * Lấy: mật khẩu đăng nhập
	 */
	public String getPassword() {
		return password;
	}
	/**
	 * Đặt: biệt danh
	 */
	public void setNickName(String nickName) {
		this.nickName = nickName;
	}
	/**
	 * Lấy: biệt danh
	 */
	public String getNickName() {
		return nickName;
	}
	/**
	 * Đặt: ảnh đại diện người dùng
	 */
	public void setUserPhoto(String userPhoto) {
		this.userPhoto = userPhoto;
	}
	/**
	 * Lấy: ảnh đại diện người dùng
	 */
	public String getUserPhoto() {
		return userPhoto;
	}
	/**
	 * Đặt: giới tính: 0 nam, 1 nữ
	 */
	public void setUserSex(Integer userSex) {
		this.userSex = userSex;
	}
	/**
	 * Lấy: giới tính: 0 nam, 1 nữ
	 */
	public Integer getUserSex() {
		return userSex;
	}
	/**
	 * Đặt: số dư tài khoản
	 */
	public void setAccountBalance(Long accountBalance) {
		this.accountBalance = accountBalance;
	}
	/**
	 * Lấy: số dư tài khoản
	 */
	public Long getAccountBalance() {
		return accountBalance;
	}
	/**
	 * Đặt: trạng thái người dùng: 0 bình thường
	 */
	public void setStatus(Integer status) {
		this.status = status;
	}
	/**
	 * Lấy: trạng thái người dùng: 0 bình thường
	 */
	public Integer getStatus() {
		return status;
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
	 * Đặt: thời gian cập nhật
	 */
	public void setUpdateTime(Date updateTime) {
		this.updateTime = updateTime;
	}
	/**
	 * Lấy: thời gian cập nhật
	 */
	public Date getUpdateTime() {
		return updateTime;
	}
}
