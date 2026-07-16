package com.java2nb.novel.domain;

import java.io.Serializable;


import java.math.BigDecimal;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.java2nb.common.jsonserializer.LongToStringSerializer;


import org.springframework.format.annotation.DateTimeFormat;
import java.util.Date;



/**
 * Bảng tin tức
 * 
 * @author xiongxy
 * @email 1179705413@qq.com
 * @date 2020-12-01 10:05:51
 */
public class NewsDO implements Serializable {
	private static final long serialVersionUID = 1L;

	
	// Khóa chính
		// Kiểu long của Java có phạm vi lớn hơn number của JavaScript, nên một số giá trị không thể biểu diễn chính xác trong JavaScript
	// Vì vậy, giá trị được tuần tự hóa thành chuỗi để xử lý
	@JsonSerialize(using = LongToStringSerializer.class)
			private Long id;
	// ID loại
			private Integer catId;
	// Tên danh mục
			private String catName;
	// Nguồn
			private String sourceName;
	// Tiêu đề
			private String title;
	// Nội dung
			private String content;
	// Thời gian đăng
			@DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
		private Date createTime;
	// ID người đăng
		// Kiểu long của Java có phạm vi lớn hơn number của JavaScript, nên một số giá trị không thể biểu diễn chính xác trong JavaScript
	// Vì vậy, giá trị được tuần tự hóa thành chuỗi để xử lý
	@JsonSerialize(using = LongToStringSerializer.class)
			private Long createUserId;
	// Thời gian cập nhật
			@DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
		private Date updateTime;
	// ID người cập nhật
		// Kiểu long của Java có phạm vi lớn hơn number của JavaScript, nên một số giá trị không thể biểu diễn chính xác trong JavaScript
	// Vì vậy, giá trị được tuần tự hóa thành chuỗi để xử lý
	@JsonSerialize(using = LongToStringSerializer.class)
			private Long updateUserId;

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
	 * Đặt: ID loại
	 */
	public void setCatId(Integer catId) {
		this.catId = catId;
	}
	/**
	 * Lấy: ID loại
	 */
	public Integer getCatId() {
		return catId;
	}
	/**
	 * Đặt: tên danh mục
	 */
	public void setCatName(String catName) {
		this.catName = catName;
	}
	/**
	 * Lấy: tên danh mục
	 */
	public String getCatName() {
		return catName;
	}
	/**
	 * Đặt: nguồn
	 */
	public void setSourceName(String sourceName) {
		this.sourceName = sourceName;
	}
	/**
	 * Lấy: nguồn
	 */
	public String getSourceName() {
		return sourceName;
	}
	/**
	 * Đặt: tiêu đề
	 */
	public void setTitle(String title) {
		this.title = title;
	}
	/**
	 * Lấy: tiêu đề
	 */
	public String getTitle() {
		return title;
	}
	/**
	 * Đặt: nội dung
	 */
	public void setContent(String content) {
		this.content = content;
	}
	/**
	 * Lấy: nội dung
	 */
	public String getContent() {
		return content;
	}
	/**
	 * Đặt: thời gian đăng
	 */
	public void setCreateTime(Date createTime) {
		this.createTime = createTime;
	}
	/**
	 * Lấy: thời gian đăng
	 */
	public Date getCreateTime() {
		return createTime;
	}
	/**
	 * Đặt: ID người đăng
	 */
	public void setCreateUserId(Long createUserId) {
		this.createUserId = createUserId;
	}
	/**
	 * Lấy: ID người đăng
	 */
	public Long getCreateUserId() {
		return createUserId;
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
	/**
	 * Đặt: ID người cập nhật
	 */
	public void setUpdateUserId(Long updateUserId) {
		this.updateUserId = updateUserId;
	}
	/**
	 * Lấy: ID người cập nhật
	 */
	public Long getUpdateUserId() {
		return updateUserId;
	}
}
