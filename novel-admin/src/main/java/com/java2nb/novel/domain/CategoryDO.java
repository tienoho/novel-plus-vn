package com.java2nb.novel.domain;

import java.io.Serializable;


import java.math.BigDecimal;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.java2nb.common.jsonserializer.LongToStringSerializer;


import org.springframework.format.annotation.DateTimeFormat;
import java.util.Date;



/**
 * Bảng danh mục tin tức
 * 
 * @author xiongxy
 * @email 1179705413@qq.com
 * @date 2020-12-01 10:03:41
 */
public class CategoryDO implements Serializable {
	private static final long serialVersionUID = 1L;

	
	// Khóa chính
			private Integer id;
	// Tên danh mục
			private String name;
	// Thứ tự
			private Integer sort;
	//
		// Kiểu long của Java có phạm vi lớn hơn number của JavaScript, nên một số giá trị không thể biểu diễn chính xác trong JavaScript
	// Vì vậy, giá trị được tuần tự hóa thành chuỗi để xử lý
	@JsonSerialize(using = LongToStringSerializer.class)
			private Long createUserId;
	//
			@DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
		private Date createTime;
	//
		// Kiểu long của Java có phạm vi lớn hơn number của JavaScript, nên một số giá trị không thể biểu diễn chính xác trong JavaScript
	// Vì vậy, giá trị được tuần tự hóa thành chuỗi để xử lý
	@JsonSerialize(using = LongToStringSerializer.class)
			private Long updateUserId;
	//
			@DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
		private Date updateTime;

	/**
	 * Đặt: khóa chính
	 */
	public void setId(Integer id) {
		this.id = id;
	}
	/**
	 * Lấy: khóa chính
	 */
	public Integer getId() {
		return id;
	}
	/**
	 * Đặt: tên danh mục
	 */
	public void setName(String name) {
		this.name = name;
	}
	/**
	 * Lấy: tên danh mục
	 */
	public String getName() {
		return name;
	}
	/**
	 * Đặt: thứ tự
	 */
	public void setSort(Integer sort) {
		this.sort = sort;
	}
	/**
	 * Lấy: thứ tự
	 */
	public Integer getSort() {
		return sort;
	}
	/**
	 * Đặt:
	 */
	public void setCreateUserId(Long createUserId) {
		this.createUserId = createUserId;
	}
	/**
	 * Lấy:
	 */
	public Long getCreateUserId() {
		return createUserId;
	}
	/**
	 * Đặt:
	 */
	public void setCreateTime(Date createTime) {
		this.createTime = createTime;
	}
	/**
	 * Lấy:
	 */
	public Date getCreateTime() {
		return createTime;
	}
	/**
	 * Đặt:
	 */
	public void setUpdateUserId(Long updateUserId) {
		this.updateUserId = updateUserId;
	}
	/**
	 * Lấy:
	 */
	public Long getUpdateUserId() {
		return updateUserId;
	}
	/**
	 * Đặt:
	 */
	public void setUpdateTime(Date updateTime) {
		this.updateTime = updateTime;
	}
	/**
	 * Lấy:
	 */
	public Date getUpdateTime() {
		return updateTime;
	}
}
