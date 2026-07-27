package com.java2nb.novel.controller;

import com.java2nb.common.utils.PageBean;
import com.java2nb.common.utils.Query;
import com.java2nb.common.utils.R;
import com.java2nb.novel.entity.BookComment;
import com.java2nb.novel.mapper.BookCommentDynamicSqlSupport;
import com.java2nb.novel.mapper.BookCommentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.mybatis.dynamic.sql.render.RenderingStrategies;
import org.mybatis.dynamic.sql.select.render.SelectStatementProvider;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;
import static org.mybatis.dynamic.sql.SqlBuilder.isIn;
import static org.mybatis.dynamic.sql.SqlBuilder.update;
import static org.mybatis.dynamic.sql.select.SelectDSL.select;

/**
 * Controller kiểm duyệt bình luận (Comment Moderation Queue)
 */
@Controller
@RequestMapping("/novel/moderation/comment")
@Slf4j
@RequiredArgsConstructor
public class CommentModerationController {

    private final BookCommentMapper bookCommentMapper;

    /**
     * Danh sách bình luận theo trạng thái kiểm duyệt
     */
    @ResponseBody
    @GetMapping("/list")
    @RequiresPermissions("novel:bookComment:bookComment")
    public R list(@RequestParam Map<String, Object> params) {
        Query query = new Query(params);
        Byte auditStatus = Byte.parseByte(params.getOrDefault("auditStatus", "0").toString());

        SelectStatementProvider selectStatement = select(BookCommentDynamicSqlSupport.bookComment.allColumns())
            .from(BookCommentDynamicSqlSupport.bookComment)
            .where(BookCommentDynamicSqlSupport.auditStatus, isEqualTo(auditStatus))
            .orderBy(BookCommentDynamicSqlSupport.createTime.descending())
            .limit(query.getLimit())
            .offset(query.getOffset())
            .build().render(RenderingStrategies.MYBATIS3);
        List<BookComment> list = bookCommentMapper.selectMany(selectStatement);

        SelectStatementProvider countSelect = select(org.mybatis.dynamic.sql.SqlBuilder.count(BookCommentDynamicSqlSupport.id))
            .from(BookCommentDynamicSqlSupport.bookComment)
            .where(BookCommentDynamicSqlSupport.auditStatus, isEqualTo(auditStatus))
            .build().render(RenderingStrategies.MYBATIS3);
        long total = bookCommentMapper.count(countSelect);

        PageBean pageBean = new PageBean(list, (int) total);
        return R.ok().put("data", pageBean);
    }

    /**
     * Duyệt / từ chối bình luận hàng loạt
     */
    @ResponseBody
    @PostMapping("/batchAudit")
    @RequiresPermissions("novel:bookComment:edit")
    public R batchAudit(@RequestParam("ids[]") Long[] ids, @RequestParam("auditStatus") Byte auditStatus) {
        if (ids != null && ids.length > 0) {
            bookCommentMapper.update(update(BookCommentDynamicSqlSupport.bookComment)
                .set(BookCommentDynamicSqlSupport.auditStatus).equalTo(auditStatus)
                .where(BookCommentDynamicSqlSupport.id, isIn(ids))
                .build().render(RenderingStrategies.MYBATIS3));
        }
        return R.ok();
    }
}
