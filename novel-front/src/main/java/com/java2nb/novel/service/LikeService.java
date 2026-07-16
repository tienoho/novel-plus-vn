package com.java2nb.novel.service;


/**
 * @author 11797
 */
public interface LikeService {

    /**
     * Thích hoặc bỏ thích bình luận
     * @param commentId ID bình luận được thích
     * @param userId ID người dùng
     * @return số lượt thích
     */
    public Long toggleCommentLike(Long commentId, Long userId);

    /**
     * Không thích hoặc bỏ không thích bình luận
     * @param commentId ID bình luận bị không thích
     * @param userId ID người dùng
     * @return số lượt không thích
     */
    public Long toggleCommentUnLike(Long commentId, Long userId);

    /**
     * Lấy số lượt thích bình luận
     * @param commentId ID bình luận
     * @return số lượt thích
     */
    public Long getCommentLikesCount(Long commentId);

    /**
     * Lấy số lượt không thích bình luận
     * @param commentId ID bình luận
     * @return số lượt không thích
     */
    public Long getCommentUnLikesCount(Long commentId);

    /**
     * Thích hoặc bỏ thích phản hồi
     * @param replyId ID phản hồi được thích
     * @param userId ID người dùng
     * @return số lượt thích
     */
    public Long toggleReplyLike(Long replyId, Long userId);

    /**
     * Không thích hoặc bỏ không thích phản hồi
     * @param replyId ID phản hồi bị không thích
     * @param userId ID người dùng
     * @return số lượt không thích
     */
    public Long toggleReplyUnLike(Long replyId, Long userId);

    /**
     * Lấy số lượt thích phản hồi
     * @param replyId ID phản hồi
     * @return số lượt thích
     */
    public Long getReplyLikesCount(Long replyId);

    /**
     * Lấy số lượt không thích phản hồi
     * @param replyId ID phản hồi
     * @return số lượt không thích
     */
    public Long getReplyUnLikesCount(Long replyId);

}
