package com.java2nb.system.domain;

import java.io.Serializable;
import java.util.Date;

public class MenuDO implements Serializable {
	private static final long serialVersionUID = 1L;
	//
	private Long menuId;
	// ID menu cha; menu cấp một là 0
	private Long parentId;
	// Tên menu
	private String name;
	// URL menu
	private String url;
	// Quyền hạn (nhiều giá trị cách nhau bằng dấu phẩy, ví dụ: user:list,user:create)
	private String perms;
	// Loại: 0 thư mục, 1 menu, 2 nút
	private Integer type;
	// Biểu tượng menu
	private String icon;
	// Thứ tự
	private Integer orderNum;
	// Thời gian tạo
	private Date gmtCreate;
	// Thời gian sửa
	private Date gmtModified;

	/**
	 * Đặt:
	 */
	public void setMenuId(Long menuId) {
		this.menuId = menuId;
	}

	/**
	 * Lấy:
	 */
	public Long getMenuId() {
		return menuId;
	}

	/**
	 * Đặt: ID menu cha; menu cấp một là 0
	 */
	public void setParentId(Long parentId) {
		this.parentId = parentId;
	}

	/**
	 * Lấy: ID menu cha; menu cấp một là 0
	 */
	public Long getParentId() {
		return parentId;
	}

	/**
	 * Đặt: tên menu
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * Lấy: tên menu
	 */
	public String getName() {
		return name;
	}

	/**
	 * Đặt: URL menu
	 */
	public void setUrl(String url) {
		this.url = url;
	}

	/**
	 * Lấy: URL menu
	 */
	public String getUrl() {
		return url;
	}

	/**
	 * Đặt: quyền hạn (nhiều giá trị cách nhau bằng dấu phẩy, ví dụ: user:list,user:create)
	 */
	public void setPerms(String perms) {
		this.perms = perms;
	}

	/**
	 * Lấy: quyền hạn (nhiều giá trị cách nhau bằng dấu phẩy, ví dụ: user:list,user:create)
	 */
	public String getPerms() {
		return perms;
	}

	/**
	 * Đặt: loại: 0 thư mục, 1 menu, 2 nút
	 */
	public void setType(Integer type) {
		this.type = type;
	}

	/**
	 * Lấy: loại: 0 thư mục, 1 menu, 2 nút
	 */
	public Integer getType() {
		return type;
	}

	/**
	 * Đặt: biểu tượng menu
	 */
	public void setIcon(String icon) {
		this.icon = icon;
	}

	/**
	 * Lấy: biểu tượng menu
	 */
	public String getIcon() {
		return icon;
	}

	/**
	 * Đặt: thứ tự
	 */
	public void setOrderNum(Integer orderNum) {
		this.orderNum = orderNum;
	}

	/**
	 * Lấy: thứ tự
	 */
	public Integer getOrderNum() {
		return orderNum;
	}

	/**
	 * Đặt: thời gian tạo
	 */
	public void setGmtCreate(Date gmtCreate) {
		this.gmtCreate = gmtCreate;
	}

	/**
	 * Lấy: thời gian tạo
	 */
	public Date getGmtCreate() {
		return gmtCreate;
	}

	/**
	 * Đặt: thời gian sửa
	 */
	public void setGmtModified(Date gmtModified) {
		this.gmtModified = gmtModified;
	}

	/**
	 * Lấy: thời gian sửa
	 */
	public Date getGmtModified() {
		return gmtModified;
	}

	@Override
	public String toString() {
		return "MenuDO{" +
				"menuId=" + menuId +
				", parentId=" + parentId +
				", name='" + name + '\'' +
				", url='" + url + '\'' +
				", perms='" + perms + '\'' +
				", type=" + type +
				", icon='" + icon + '\'' +
				", orderNum=" + orderNum +
				", gmtCreate=" + gmtCreate +
				", gmtModified=" + gmtModified +
				'}';
	}
}
