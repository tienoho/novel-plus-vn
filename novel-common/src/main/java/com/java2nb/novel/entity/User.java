package com.java2nb.novel.entity;

import io.github.xxyopen.web.valid.AddGroup;
import io.github.xxyopen.web.valid.UpdateGroup;
import jakarta.validation.constraints.*;

import javax.annotation.Generated;
import java.util.Date;

public class User {

    @Null(groups = {AddGroup.class, UpdateGroup.class})
    @Generated("org.mybatis.generator.api.MyBatisGenerator")
    private Long id;

    @NotBlank(groups = {AddGroup.class}, message = "{validation.user.phone.required}")
    @Pattern(groups = {AddGroup.class}, regexp = "^1[3|4|5|6|7|8|9][0-9]{9}$", message = "{validation.user.phone.invalid}")
    @Null(groups = {UpdateGroup.class})
    @Generated("org.mybatis.generator.api.MyBatisGenerator")
    private String username;

    @NotBlank(groups = {AddGroup.class}, message = "{validation.user.password.required}")
    @Null(groups = {UpdateGroup.class})
    @Generated("org.mybatis.generator.api.MyBatisGenerator")
    private String password;

    @Null(groups = {AddGroup.class})
    @Pattern(groups = {
        UpdateGroup.class}, regexp = "[\u4E00-\u9FA5A-Za-z0-9_]{1,11}", message = "{validation.user.nickname.invalid}")
    @Generated("org.mybatis.generator.api.MyBatisGenerator")
    private String nickName;

    @Null(groups = {AddGroup.class})
    @Pattern(groups = {
        UpdateGroup.class}, regexp = "^/localPic/\\d{4}/\\d{2}/\\d{2}/[A-Za-z0-9]+\\.(jpg|jpeg|swf|gif|png|JPG|JPEG|SWF|GIF|PNG)$", message = "{validation.user.photo.invalid}")
    @Generated("org.mybatis.generator.api.MyBatisGenerator")
    private String userPhoto;

    @Null(groups = {AddGroup.class})
    @Min(value = 0, groups = {UpdateGroup.class})
    @Max(value = 1, groups = {UpdateGroup.class})
    @Generated("org.mybatis.generator.api.MyBatisGenerator")
    private Byte userSex;

    @Null(groups = {AddGroup.class, UpdateGroup.class})
    @Generated("org.mybatis.generator.api.MyBatisGenerator")
    private Long accountBalance;

    @Null(groups = {AddGroup.class, UpdateGroup.class})
    @Generated("org.mybatis.generator.api.MyBatisGenerator")
    private Byte status;

    @Null(groups = {AddGroup.class, UpdateGroup.class})
    @Generated("org.mybatis.generator.api.MyBatisGenerator")
    private Date createTime;

    @Null(groups = {AddGroup.class, UpdateGroup.class})
    @Generated("org.mybatis.generator.api.MyBatisGenerator")
    private Date updateTime;

    private Date dateOfBirth;

    @Null(groups = {AddGroup.class, UpdateGroup.class})
    private Byte isAgeVerified;

    @Generated("org.mybatis.generator.api.MyBatisGenerator")
    public Long getId() {
        return id;
    }

    @Generated("org.mybatis.generator.api.MyBatisGenerator")
    public void setId(Long id) {
        this.id = id;
    }

    @Generated("org.mybatis.generator.api.MyBatisGenerator")
    public String getUsername() {
        return username;
    }

    @Generated("org.mybatis.generator.api.MyBatisGenerator")
    public void setUsername(String username) {
        this.username = username == null ? null : username.trim();
    }

    @Generated("org.mybatis.generator.api.MyBatisGenerator")
    public String getPassword() {
        return password;
    }

    @Generated("org.mybatis.generator.api.MyBatisGenerator")
    public void setPassword(String password) {
        this.password = password == null ? null : password.trim();
    }

    @Generated("org.mybatis.generator.api.MyBatisGenerator")
    public String getNickName() {
        return nickName;
    }

    @Generated("org.mybatis.generator.api.MyBatisGenerator")
    public void setNickName(String nickName) {
        this.nickName = nickName == null ? null : nickName.trim();
    }

    @Generated("org.mybatis.generator.api.MyBatisGenerator")
    public String getUserPhoto() {
        return userPhoto;
    }

    @Generated("org.mybatis.generator.api.MyBatisGenerator")
    public void setUserPhoto(String userPhoto) {
        this.userPhoto = userPhoto == null ? null : userPhoto.trim();
    }

    @Generated("org.mybatis.generator.api.MyBatisGenerator")
    public Byte getUserSex() {
        return userSex;
    }

    @Generated("org.mybatis.generator.api.MyBatisGenerator")
    public void setUserSex(Byte userSex) {
        this.userSex = userSex;
    }

    @Generated("org.mybatis.generator.api.MyBatisGenerator")
    public Long getAccountBalance() {
        return accountBalance;
    }

    @Generated("org.mybatis.generator.api.MyBatisGenerator")
    public void setAccountBalance(Long accountBalance) {
        this.accountBalance = accountBalance;
    }

    @Generated("org.mybatis.generator.api.MyBatisGenerator")
    public Byte getStatus() {
        return status;
    }

    @Generated("org.mybatis.generator.api.MyBatisGenerator")
    public void setStatus(Byte status) {
        this.status = status;
    }

    @Generated("org.mybatis.generator.api.MyBatisGenerator")
    public Date getCreateTime() {
        return createTime;
    }

    @Generated("org.mybatis.generator.api.MyBatisGenerator")
    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    @Generated("org.mybatis.generator.api.MyBatisGenerator")
    public Date getUpdateTime() {
        return updateTime;
    }

    @Generated("org.mybatis.generator.api.MyBatisGenerator")
    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public Date getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(Date dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public Byte getIsAgeVerified() {
        return isAgeVerified;
    }

    public void setIsAgeVerified(Byte isAgeVerified) {
        this.isAgeVerified = isAgeVerified;
    }
}
