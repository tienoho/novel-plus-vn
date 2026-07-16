package com.java2nb.common.domain;

/**
 * Thuộc tính cột
 * 
 * 
 */
public class ColumnDO {
	// Tên cột
	private String columnName;
	// Kiểu cột
	private String dataType;
	// Ghi chú cột
	private String comments;

	// Tên thuộc tính (chữ cái đầu viết hoa), ví dụ: user_name => UserName
	private String attrName;
	// Tên thuộc tính (chữ cái đầu viết thường), ví dụ: user_name => userName
	private String attrname;
	// Kiểu thuộc tính
	private String attrType;
	// auto_increment
	private String extra;

	public String getColumnName() {
		return columnName;
	}

	public void setColumnName(String columnName) {
		this.columnName = columnName;
	}

	public String getDataType() {
		return dataType;
	}

	public void setDataType(String dataType) {
		this.dataType = dataType;
	}

	public String getComments() {
		return comments;
	}

	public void setComments(String comments) {
		this.comments = comments;
	}

	public String getAttrname() {
		return attrname;
	}

	public void setAttrname(String attrname) {
		this.attrname = attrname;
	}

	public String getAttrName() {
		return attrName;
	}

	public void setAttrName(String attrName) {
		this.attrName = attrName;
	}

	public String getAttrType() {
		return attrType;
	}

	public void setAttrType(String attrType) {
		this.attrType = attrType;
	}

	public String getExtra() {
		return extra;
	}

	public void setExtra(String extra) {
		this.extra = extra;
	}

	@Override
	public String toString() {
		return "ColumnDO{" +
				"columnName='" + columnName + '\'' +
				", dataType='" + dataType + '\'' +
				", comments='" + comments + '\'' +
				", attrName='" + attrName + '\'' +
				", attrname='" + attrname + '\'' +
				", attrType='" + attrType + '\'' +
				", extra='" + extra + '\'' +
				'}';
	}
}
