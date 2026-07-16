package com.java2nb.system.domain;

import java.io.Serializable;


import java.math.BigDecimal;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.java2nb.common.jsonserializer.LongToStringSerializer;


import org.springframework.format.annotation.DateTimeFormat;
import java.util.Date;



/**
 * Quan hệ giữa vai trò và quyền dữ liệu
 * 
 * @author xiongxy
 * @email 1179705413@qq.com
 * @date 2019-11-25 11:32:49
 */
public class RoleDataPermDO implements Serializable {
	private static final long serialVersionUID = 1L;

	
	//
		// Kiểu long của Java có phạm vi lớn hơn number của JavaScript, nên một số giá trị không thể biểu diễn chính xác trong JavaScript
	// Vì vậy, giá trị được tuần tự hóa thành chuỗi để xử lý
	@JsonSerialize(using = LongToStringSerializer.class)
			private Long id;
	// ID vai trò
		// Kiểu long của Java có phạm vi lớn hơn number của JavaScript, nên một số giá trị không thể biểu diễn chính xác trong JavaScript
	// Vì vậy, giá trị được tuần tự hóa thành chuỗi để xử lý
	@JsonSerialize(using = LongToStringSerializer.class)
			private Long roleId;
	// ID quyền
		// Kiểu long của Java có phạm vi lớn hơn number của JavaScript, nên một số giá trị không thể biểu diễn chính xác trong JavaScript
	// Vì vậy, giá trị được tuần tự hóa thành chuỗi để xử lý
	@JsonSerialize(using = LongToStringSerializer.class)
			private Long permId;

	/**
	 * Đặt:
	 */
	public void setId(Long id) {
		this.id = id;
	}
	/**
	 * Lấy:
	 */
	public Long getId() {
		return id;
	}
	/**
	 * Đặt: ID vai trò
	 */
	public void setRoleId(Long roleId) {
		this.roleId = roleId;
	}
	/**
	 * Lấy: ID vai trò
	 */
	public Long getRoleId() {
		return roleId;
	}
	/**
	 * Đặt: ID quyền
	 */
	public void setPermId(Long permId) {
		this.permId = permId;
	}
	/**
	 * Lấy: ID quyền
	 */
	public Long getPermId() {
		return permId;
	}
}
