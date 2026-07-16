package com.java2nb.system.domain;

import java.io.Serializable;



/**
 * Quản lý phòng ban
 * 
 * @author xiongxy
 * @email 1179705413@qq.com
 * @date 2019-09-27 14:28:36
 */
public class DeptDO implements Serializable {
	private static final long serialVersionUID = 1L;
	
	//
	private Long deptId;
	//ID phòng ban cha; phòng ban cấp một là 0
	private Long parentId;
	// Tên phòng ban
	private String name;
	// Thứ tự
	private Integer orderNum;
	// Trạng thái xóa: -1 đã xóa, 0 bình thường
	private Integer delFlag;

	/**
	 * Đặt:
	 */
	public void setDeptId(Long deptId) {
		this.deptId = deptId;
	}
	/**
	 * Lấy:
	 */
	public Long getDeptId() {
		return deptId;
	}
	/**
	 * Đặt: ID phòng ban cha; phòng ban cấp một là 0
	 */
	public void setParentId(Long parentId) {
		this.parentId = parentId;
	}
	/**
	 * Lấy: ID phòng ban cha; phòng ban cấp một là 0
	 */
	public Long getParentId() {
		return parentId;
	}
	/**
	 * Đặt: tên phòng ban
	 */
	public void setName(String name) {
		this.name = name;
	}
	/**
	 * Lấy: tên phòng ban
	 */
	public String getName() {
		return name;
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
	 * Đặt: trạng thái xóa: -1 đã xóa, 0 bình thường
	 */
	public void setDelFlag(Integer delFlag) {
		this.delFlag = delFlag;
	}
	/**
	 * Lấy: trạng thái xóa: -1 đã xóa, 0 bình thường
	 */
	public Integer getDelFlag() {
		return delFlag;
	}

	@Override
	public String toString() {
		return "DeptDO{" +
				"deptId=" + deptId +
				", parentId=" + parentId +
				", name='" + name + '\'' +
				", orderNum=" + orderNum +
				", delFlag=" + delFlag +
				'}';
	}
}
