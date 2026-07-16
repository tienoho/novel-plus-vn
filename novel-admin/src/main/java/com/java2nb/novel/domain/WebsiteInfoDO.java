package com.java2nb.novel.domain;

import java.io.Serializable;


import java.math.BigDecimal;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.java2nb.common.jsonserializer.LongToStringSerializer;


import org.springframework.format.annotation.DateTimeFormat;
import java.util.Date;



/**
 * Bảng thông tin website
 * 
 * @author xiongxy
 * @email 1179705413@qq.com
 * @date 2023-04-14 11:05:43
 */
public class WebsiteInfoDO implements Serializable {
	private static final long serialVersionUID = 1L;

	
	// Khóa chính
		// Kiểu long của Java có phạm vi lớn hơn number của JavaScript, nên một số giá trị không thể biểu diễn chính xác trong JavaScript
	// Vì vậy, giá trị được tuần tự hóa thành chuỗi để xử lý
	@JsonSerialize(using = LongToStringSerializer.class)
			private Long id;
	// Tên website
			private String name;
	// Tên miền website
			private String domain;
	// Từ khóa SEO
			private String keyword;
	// Mô tả website
			private String description;
	// QQ của quản trị website
			private String qq;
	// Ảnh logo website (mặc định)
			private String logo;
	// Ảnh logo website (tối)
			private String logoDark;
	// Thời gian tạo
			@DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
		private Date createTime;
	// ID người tạo
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
	 * Đặt: tên website
	 */
	public void setName(String name) {
		this.name = name;
	}
	/**
	 * Lấy: tên website
	 */
	public String getName() {
		return name;
	}
	/**
	 * Đặt: tên miền website
	 */
	public void setDomain(String domain) {
		this.domain = domain;
	}
	/**
	 * Lấy: tên miền website
	 */
	public String getDomain() {
		return domain;
	}
	/**
	 * Đặt: từ khóa SEO
	 */
	public void setKeyword(String keyword) {
		this.keyword = keyword;
	}
	/**
	 * Lấy: từ khóa SEO
	 */
	public String getKeyword() {
		return keyword;
	}
	/**
	 * Đặt: mô tả website
	 */
	public void setDescription(String description) {
		this.description = description;
	}
	/**
	 * Lấy: mô tả website
	 */
	public String getDescription() {
		return description;
	}
	/**
	 * Đặt: QQ của quản trị website
	 */
	public void setQq(String qq) {
		this.qq = qq;
	}
	/**
	 * Lấy: QQ của quản trị website
	 */
	public String getQq() {
		return qq;
	}
	/**
	 * Đặt: ảnh logo website (mặc định)
	 */
	public void setLogo(String logo) {
		this.logo = logo;
	}
	/**
	 * Lấy: ảnh logo website (mặc định)
	 */
	public String getLogo() {
		return logo;
	}
	/**
	 * Đặt: ảnh logo website (tối)
	 */
	public void setLogoDark(String logoDark) {
		this.logoDark = logoDark;
	}
	/**
	 * Lấy: ảnh logo website (tối)
	 */
	public String getLogoDark() {
		return logoDark;
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
