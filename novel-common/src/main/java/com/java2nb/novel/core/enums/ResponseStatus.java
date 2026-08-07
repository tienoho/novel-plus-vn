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
    CHAPTER_DUPLICATE_EXISTS(4004, "author.chapter.duplicate"),
    CONTENT_AUDIT_PENDING(4005, "moderation.content.pending"),
    AUTHOR_STORY_ITEM_NOT_FOUND(4006, "author.story.item.notFound"),
    AUTHOR_STORY_VERSION_CONFLICT(4007, "author.story.versionConflict"),
    AUTHOR_STORY_INVALID_TYPE(4008, "author.story.invalidType"),
    AUTHOR_BOOK_ACCESS_FORBIDDEN(4009, "author.collaboration.access.forbidden"),
    AUTHOR_COLLABORATOR_NOT_FOUND(4010, "author.collaboration.notFound"),
    AUTHOR_COLLABORATOR_INVALID(4011, "author.collaboration.invalid"),
    AUTHOR_COLLABORATION_VERSION_CONFLICT(4012, "author.collaboration.versionConflict"),
    AUTHOR_AI_INVALID_REQUEST(4013, "author.ai.invalidRequest"),
    AUTHOR_AI_UNAVAILABLE(4014, "author.ai.unavailable"),
    AUTHOR_VOUCHER_NOT_FOUND(4015, "author.finance.voucherNotFound"),

    /**
     * Lỗi liên quan đến tác phẩm
     */
    BOOK_EXISTS(5001,"book.exists"),
    AGE_RESTRICTED(5002, "book.ageRestricted"),
    COPYRIGHT_TAKEDOWN(5003, "book.copyrightTakedown"),
    BOOK_NOT_AVAILABLE(5004, "book.notAvailable"),
    BOOK_CATEGORY_INVALID(5005, "book.category.invalid"),

    /**
     * Lỗi liên quan đến trạng thái đọc riêng tư
     */
    READER_CHAPTER_INVALID(6001, "error.reader.chapterInvalid"),
    READER_ANNOTATION_NOT_FOUND(6002, "error.reader.annotationNotFound"),
    READER_ANNOTATION_VERSION_CONFLICT(6003, "error.reader.annotationConflict"),
    READER_ANNOTATION_INVALID(6004, "error.reader.annotationInvalid"),

    /**
     * Lỗi gamification và Ngọn Đuốc.
     */
    GAMIFICATION_DISABLED(7001, "gamification.disabled"),
    GAMIFICATION_SEASON_UNAVAILABLE(7002, "gamification.season.unavailable"),
    GAMIFICATION_TICKET_INSUFFICIENT(7003, "gamification.ticket.insufficient"),
    GAMIFICATION_VOTE_DAILY_LIMIT(7004, "gamification.vote.dailyLimit"),
    GAMIFICATION_VOTE_BOOK_LIMIT(7005, "gamification.vote.bookLimit"),
    GAMIFICATION_BOOK_INELIGIBLE(7006, "gamification.book.ineligible"),
    GAMIFICATION_IDEMPOTENCY_CONFLICT(7007, "gamification.idempotency.conflict"),
    GAMIFICATION_ACCOUNT_UNAVAILABLE(7008, "gamification.account.unavailable"),
    GAMIFICATION_PROFILE_VERSION_CONFLICT(7009, "gamification.profile.versionConflict"),
    GAMIFICATION_REALM_INVALID(7010, "gamification.realm.invalid"),
    GAMIFICATION_REALM_LEVEL_REQUIRED(7011, "gamification.realm.levelRequired"),
    GAMIFICATION_REALM_COOLDOWN(7012, "gamification.realm.cooldown"),
    GAMIFICATION_QUEST_NOT_FOUND(7013, "gamification.quest.notFound"),
    GAMIFICATION_QUEST_NOT_COMPLETED(7014, "gamification.quest.notCompleted"),
    GAMIFICATION_QUEST_REWARD_UNAVAILABLE(7015, "gamification.quest.rewardUnavailable"),
    GAMIFICATION_READING_HEARTBEAT_INVALID(7016, "gamification.reading.heartbeatInvalid"),
    GAMIFICATION_VOTE_RISK_BLOCKED(7017, "gamification.vote.riskBlocked"),

    /** Lỗi Vé đọc và quyền đọc chương. */
    READING_TICKET_DISABLED(7101, "reader.ticket.disabled"),
    READING_TICKET_INSUFFICIENT(7102, "reader.ticket.insufficient"),
    READING_SUBSCRIPTION_CHECKOUT_UNAVAILABLE(7110, "reader.subscription.checkout.unavailable"),
    READING_SUBSCRIPTION_PURCHASE_CONFLICT(7111, "reader.subscription.checkout.conflict"),
    READING_SUBSCRIPTION_MANDATE_UNAVAILABLE(7112, "reader.subscription.mandate.unavailable"),
    READING_SUBSCRIPTION_MANDATE_CONFLICT(7113, "reader.subscription.mandate.conflict"),

    /** Lỗi mã quà. */
    GIFT_CODE_DISABLED(7201, "gift.code.disabled"),
    GIFT_CODE_REJECTED(7202, "gift.code.rejected"),

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
