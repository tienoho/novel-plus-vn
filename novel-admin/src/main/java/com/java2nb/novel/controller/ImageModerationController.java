package com.java2nb.novel.controller;

import com.java2nb.common.utils.PageBean;
import com.java2nb.common.utils.Query;
import com.java2nb.common.utils.R;
import com.java2nb.novel.entity.Book;
import com.java2nb.novel.core.enums.ModerationStatusEnum;
import com.java2nb.novel.mapper.BookDynamicSqlSupport;
import com.java2nb.novel.mapper.BookMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.mybatis.dynamic.sql.render.RenderingStrategies;
import org.mybatis.dynamic.sql.select.render.SelectStatementProvider;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.mybatis.dynamic.sql.SqlBuilder.isNotNull;
import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;
import static org.mybatis.dynamic.sql.SqlBuilder.update;
import static org.mybatis.dynamic.sql.select.SelectDSL.select;

/**
 * Controller kiểm duyệt hình ảnh tải lên (Bìa truyện, Avatar) - Image Moderation Queue
 */
@Controller
@RequestMapping("/novel/moderation/image")
@Slf4j
@RequiredArgsConstructor
public class ImageModerationController {

    private final BookMapper bookMapper;

    /**
     * Danh sách tác phẩm có hình ảnh cần kiểm duyệt
     */
    @ResponseBody
    @GetMapping("/list")
    @RequiresPermissions("novel:book:book")
    public R list(@RequestParam Map<String, Object> params) {
        Query query = new Query(params);
        Byte auditStatus = Byte.parseByte(params.getOrDefault("auditStatus", "0").toString());

        SelectStatementProvider selectStatement = select(BookDynamicSqlSupport.book.allColumns())
            .from(BookDynamicSqlSupport.book)
            .where(BookDynamicSqlSupport.picUrl, isNotNull())
            .and(BookDynamicSqlSupport.coverAuditStatus, isEqualTo(auditStatus))
            .orderBy(BookDynamicSqlSupport.updateTime.descending())
            .limit(query.getLimit())
            .offset(query.getOffset())
            .build().render(RenderingStrategies.MYBATIS3);
        List<Book> list = bookMapper.selectMany(selectStatement);

        SelectStatementProvider countSelect = select(org.mybatis.dynamic.sql.SqlBuilder.count(BookDynamicSqlSupport.id))
            .from(BookDynamicSqlSupport.book)
            .where(BookDynamicSqlSupport.picUrl, isNotNull())
            .and(BookDynamicSqlSupport.coverAuditStatus, isEqualTo(auditStatus))
            .build().render(RenderingStrategies.MYBATIS3);
        long total = bookMapper.count(countSelect);

        PageBean pageBean = new PageBean(list, (int) total);
        return R.ok().put("data", pageBean);
    }

    /**
     * Phê duyệt hoặc từ chối hình ảnh tác phẩm
     */
    @ResponseBody
    @PostMapping("/audit")
    @RequiresPermissions("novel:book:edit")
    public R audit(@RequestParam("bookId") Long bookId, @RequestParam("approved") Boolean approved,
                   @RequestParam(value = "auditReason", required = false) String auditReason,
                   @RequestParam(value = "defaultPicUrl", defaultValue = "/images/default.gif") String defaultPicUrl) {
        Byte nextStatus = Boolean.TRUE.equals(approved)
            ? ModerationStatusEnum.APPROVED.getCode()
            : ModerationStatusEnum.REJECTED.getCode();
        var updateDsl = update(BookDynamicSqlSupport.book)
            .set(BookDynamicSqlSupport.coverAuditStatus).equalTo(nextStatus)
            .set(BookDynamicSqlSupport.updateTime).equalTo(new Date());
        if (Boolean.TRUE.equals(approved)) {
            updateDsl = updateDsl.set(BookDynamicSqlSupport.coverAuditReason).equalToNull();
        } else {
            // Thay thế hình ảnh bị gỡ bỏ bằng ảnh mặc định
            updateDsl = updateDsl
                .set(BookDynamicSqlSupport.coverAuditReason).equalTo(auditReason)
                .set(BookDynamicSqlSupport.picUrl).equalTo(defaultPicUrl);
        }
        bookMapper.update(updateDsl
            .where(BookDynamicSqlSupport.id, isEqualTo(bookId))
            .build().render(RenderingStrategies.MYBATIS3));
        return R.ok();
    }
}
