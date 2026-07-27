package com.java2nb.novel.core.enums;

import io.github.xxyopen.model.resp.IResultCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import com.java2nb.novel.core.i18n.Messages;

/**
 * @author 11797
 */

@Getter
@AllArgsConstructor
@NoArgsConstructor
public enum ResponseStatus implements IResultCode {


    /**
     * Lỗi liên quan đến người dùng
     * */
   NO_LOGIN(1001, "auth.login.required"),
    VEL_CODE_ERROR(1002, "auth.captcha.invalid"),
    USERNAME_EXIST(1003,"auth.phone.registered"),
    USERNAME_PASS_ERROR(1004,"auth.phonePassword.invalid"),
    TWO_PASSWORD_DIFF(1005, "user.password.mismatch"),
    OLD_PASSWORD_ERROR(1006, "user.password.oldInvalid"),
    USER_NO_BALANCE(1007, "payment.balance.insufficient"),

    /**
     * Lỗi liên quan đến bình luận
     * */
    HAS_COMMENTS(3001, "book.comment.duplicate"),

    /**
     * Lỗi liên quan đến tác giả
     * */
    INVITE_CODE_INVALID(4001, "author.invite.invalid"),
    AUTHOR_STATUS_FORBIDDEN(4002, "author.status.forbidden")
    , BOOKNAME_EXISTS(4003,"author.book.nameExists"),
    CHAPTER_DUPLICATE_EXISTS(4004, "Chương truyện này đã tồn tại (nội dung trùng lặp)"),
    CONTENT_AUDIT_PENDING(4005, "Nội dung đang chờ kiểm duyệt"),
    AUTHOR_STORY_ITEM_NOT_FOUND(4006, "author.story.item.notFound"),
    AUTHOR_STORY_VERSION_CONFLICT(4007, "author.story.versionConflict"),
    AUTHOR_STORY_INVALID_TYPE(4008, "author.story.invalidType"),
    AUTHOR_BOOK_ACCESS_FORBIDDEN(4009, "author.collaboration.access.forbidden"),
    AUTHOR_COLLABORATOR_NOT_FOUND(4010, "author.collaboration.notFound"),
    AUTHOR_COLLABORATOR_INVALID(4011, "author.collaboration.invalid"),
    AUTHOR_COLLABORATION_VERSION_CONFLICT(4012, "author.collaboration.versionConflict"),

    /**
     * Lỗi liên quan đến tác phẩm
     */
    BOOK_EXISTS(5001,"book.exists"),
    AGE_RESTRICTED(5002, "Giới hạn độ tuổi: Bạn chưa đủ tuổi để truy cập tác phẩm này"),
    COPYRIGHT_TAKEDOWN(5003, "Tác phẩm/chương truyện đã bị gỡ do vi phạm bản quyền"),
    BOOK_NOT_AVAILABLE(5004, "book.notAvailable"),

    /**
     * Lỗi liên quan đến công cụ tìm kiếm
     * */
    ES_SEARCH_FAIL(9001,"error.search"),

    /**
     * Lỗi liên quan đến tệp
     * */
    FILE_DIR_MAKE_FAIL(10001,"error.file.directory"),
    FILE_NOT_IMAGE(10002,"error.file.notImage"),
    FILE_SIZE_LIMIT(10003,"error.file.size"),

    /**
     * Lỗi dùng chung khác
     * */
    PASSWORD_ERROR(88001,"auth.password.invalid");

    private int code;
    private String msg;

    @Override
    public String getMsg() {
        return Messages.getDefault(msg);
    }


}
