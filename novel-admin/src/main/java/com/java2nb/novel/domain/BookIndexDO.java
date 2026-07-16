package com.java2nb.novel.domain;

import java.io.Serializable;


import java.math.BigDecimal;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.java2nb.common.jsonserializer.LongToStringSerializer;


import org.springframework.format.annotation.DateTimeFormat;
import java.util.Date;



/**
 * Bảng mục lục tác phẩm
 * 
 * @author xiongxy
 * @email 1179705413@qq.com
 * @date 2023-04-14 19:51:54
 */
public class BookIndexDO implements Serializable {
	private static final long serialVersionUID = 1L;

	
	// Khóa chính
		// Kiểu long của Java có phạm vi lớn hơn number của JavaScript, nên một số giá trị không thể biểu diễn chính xác trong JavaScript
	// Vì vậy, giá trị được tuần tự hóa thành chuỗi để xử lý
	@JsonSerialize(using = LongToStringSerializer.class)
			private Long id;
	// ID tác phẩm
		// Kiểu long của Java có phạm vi lớn hơn number của JavaScript, nên một số giá trị không thể biểu diễn chính xác trong JavaScript
	// Vì vậy, giá trị được tuần tự hóa thành chuỗi để xử lý
	@JsonSerialize(using = LongToStringSerializer.class)
			private Long bookId;
	// Số mục lục
			private Integer indexNum;
	// Tên mục lục
			private String indexName;
	// Số chữ
			private Integer wordCount;
	// Thu phí: 1 có, 0 miễn phí
			private Integer isVip;
	// Phí chương (Xu)
			private Integer bookPrice;
	// Phương thức lưu trữ
			private String storageType;
	//
			@DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
		private Date createTime;
	//
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
	 * Đặt: ID tác phẩm
	 */
	public void setBookId(Long bookId) {
		this.bookId = bookId;
	}
	/**
	 * Lấy: ID tác phẩm
	 */
	public Long getBookId() {
		return bookId;
	}
	/**
	 * Đặt: số mục lục
	 */
	public void setIndexNum(Integer indexNum) {
		this.indexNum = indexNum;
	}
	/**
	 * Lấy: số mục lục
	 */
	public Integer getIndexNum() {
		return indexNum;
	}
	/**
	 * Đặt: tên mục lục
	 */
	public void setIndexName(String indexName) {
		this.indexName = indexName;
	}
	/**
	 * Lấy: tên mục lục
	 */
	public String getIndexName() {
		return indexName;
	}
	/**
	 * Đặt: số chữ
	 */
	public void setWordCount(Integer wordCount) {
		this.wordCount = wordCount;
	}
	/**
	 * Lấy: số chữ
	 */
	public Integer getWordCount() {
		return wordCount;
	}
	/**
	 * Đặt: thu phí: 1 có, 0 miễn phí
	 */
	public void setIsVip(Integer isVip) {
		this.isVip = isVip;
	}
	/**
	 * Lấy: thu phí: 1 có, 0 miễn phí
	 */
	public Integer getIsVip() {
		return isVip;
	}
	/**
	 * Đặt: phí chương (Xu)
	 */
	public void setBookPrice(Integer bookPrice) {
		this.bookPrice = bookPrice;
	}
	/**
	 * Lấy: phí chương (Xu)
	 */
	public Integer getBookPrice() {
		return bookPrice;
	}
	/**
	 * Đặt: phương thức lưu trữ
	 */
	public void setStorageType(String storageType) {
		this.storageType = storageType;
	}
	/**
	 * Lấy: phương thức lưu trữ
	 */
	public String getStorageType() {
		return storageType;
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
