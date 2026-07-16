package com.java2nb.common.domain;

import lombok.Data;

import java.util.List;

/**
 * Dữ liệu bảng
 *
 * @author chenshun
 * @email 1179705413@qq.com
 * @date 2019-09-25 15:09:21
 */
public class TableDO {
    // Tên bảng
    private String tableName;
    // Ghi chú của bảng
    private String comments;
    // Khóa chính của bảng
    private GenColumnsDO pk;
    // Tên cột của bảng (không gồm khóa chính)
    private List<GenColumnsDO> columns;

    // Tên lớp (chữ cái đầu viết hoa), ví dụ: sys_user => SysUser
    private String className;
    // Tên lớp (chữ cái đầu viết thường), ví dụ: sys_user => sysUser
    private String classname;

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getComments() {
        return comments;
    }

    public void setComments(String comments) {
        this.comments = comments;
    }

    public GenColumnsDO getPk() {
        return pk;
    }

    public void setPk(GenColumnsDO pk) {
        this.pk = pk;
    }

    public List<GenColumnsDO> getColumns() {
        return columns;
    }

    public void setColumns(List<GenColumnsDO> columns) {
        this.columns = columns;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getClassname() {
        return classname;
    }

    public void setClassname(String classname) {
        this.classname = classname;
    }

    @Override
    public String toString() {
        return "TableDO{" +
                "tableName='" + tableName + '\'' +
                ", comments='" + comments + '\'' +
                ", pk=" + pk +
                ", columns=" + columns +
                ", className='" + className + '\'' +
                ", classname='" + classname + '\'' +
                '}';
    }
}
