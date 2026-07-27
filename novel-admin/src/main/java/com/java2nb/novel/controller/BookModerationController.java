package com.java2nb.novel.controller;

import com.java2nb.common.utils.PageBean;
import com.java2nb.common.utils.Query;
import com.java2nb.common.utils.R;
import com.java2nb.novel.entity.Book;
import com.java2nb.novel.entity.BookIndex;
import com.java2nb.novel.mapper.BookDynamicSqlSupport;
import com.java2nb.novel.mapper.BookIndexDynamicSqlSupport;
import com.java2nb.novel.mapper.BookIndexMapper;
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

import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;
import static org.mybatis.dynamic.sql.SqlBuilder.update;
import static org.mybatis.dynamic.sql.select.SelectDSL.select;

/**
 * Controller kiểm duyệt nội dung tác phẩm và chương (Novel & Chapter Moderation)
 */
@Controller
@RequestMapping("/novel/moderation")
@Slf4j
@RequiredArgsConstructor
public class BookModerationController {

    private final BookMapper bookMapper;
    private final BookIndexMapper bookIndexMapper;

    /**
     * Danh sách tác phẩm cần duyệt / có trạng thái kiểm duyệt
     */
    @ResponseBody
    @GetMapping("/book/list")
    @RequiresPermissions("novel:book:book")
    public R listBooks(@RequestParam Map<String, Object> params) {
        Query query = new Query(params);
        SelectStatementProvider selectStatement = select(BookDynamicSqlSupport.book.allColumns())
            .from(BookDynamicSqlSupport.book)
            .where(BookDynamicSqlSupport.auditStatus, isEqualTo(Byte.parseByte(params.getOrDefault("auditStatus", "0").toString())))
            .limit(query.getLimit())
            .offset(query.getOffset())
            .build().render(RenderingStrategies.MYBATIS3);
        List<Book> bookList = bookMapper.selectMany(selectStatement);

        SelectStatementProvider countSelect = select(org.mybatis.dynamic.sql.SqlBuilder.count(BookDynamicSqlSupport.id))
            .from(BookDynamicSqlSupport.book)
            .where(BookDynamicSqlSupport.auditStatus, isEqualTo(Byte.parseByte(params.getOrDefault("auditStatus", "0").toString())))
            .build().render(RenderingStrategies.MYBATIS3);
        long total = bookMapper.count(countSelect);

        PageBean pageBean = new PageBean(bookList, (int) total);
        return R.ok().put("data", pageBean);
    }

    /**
     * Thực hiện kiểm duyệt tác phẩm (Approve / Reject / Takedown)
     */
    @ResponseBody
    @PostMapping("/book/audit")
    @RequiresPermissions("novel:book:edit")
    public R auditBook(@RequestParam("id") Long id, @RequestParam("auditStatus") Byte auditStatus,
                      @RequestParam(value = "auditReason", required = false) String auditReason) {
        bookMapper.update(update(BookDynamicSqlSupport.book)
            .set(BookDynamicSqlSupport.auditStatus).equalTo(auditStatus)
            .set(BookDynamicSqlSupport.auditReason).equalTo(auditReason)
            .set(BookDynamicSqlSupport.updateTime).equalTo(new Date())
            .where(BookDynamicSqlSupport.id, isEqualTo(id))
            .build().render(RenderingStrategies.MYBATIS3));
        return R.ok();
    }

    /**
     * Danh sách chương cần kiểm duyệt
     */
    @ResponseBody
    @GetMapping("/chapter/list")
    @RequiresPermissions("novel:book:book")
    public R listChapters(@RequestParam Map<String, Object> params) {
        Query query = new Query(params);
        Byte auditStatus = Byte.parseByte(params.getOrDefault("auditStatus", "0").toString());

        SelectStatementProvider selectStatement = select(BookIndexDynamicSqlSupport.bookIndex.allColumns())
            .from(BookIndexDynamicSqlSupport.bookIndex)
            .where(BookIndexDynamicSqlSupport.auditStatus, isEqualTo(auditStatus))
            .limit(query.getLimit())
            .offset(query.getOffset())
            .build().render(RenderingStrategies.MYBATIS3);
        List<BookIndex> indexList = bookIndexMapper.selectMany(selectStatement);

        SelectStatementProvider countSelect = select(org.mybatis.dynamic.sql.SqlBuilder.count(BookIndexDynamicSqlSupport.id))
            .from(BookIndexDynamicSqlSupport.bookIndex)
            .where(BookIndexDynamicSqlSupport.auditStatus, isEqualTo(auditStatus))
            .build().render(RenderingStrategies.MYBATIS3);
        long total = bookIndexMapper.count(countSelect);

        PageBean pageBean = new PageBean(indexList, (int) total);
        return R.ok().put("data", pageBean);
    }

    /**
     * Thực hiện kiểm duyệt chương tác phẩm
     */
    @ResponseBody
    @PostMapping("/chapter/audit")
    @RequiresPermissions("novel:book:edit")
    public R auditChapter(@RequestParam("id") Long id, @RequestParam("auditStatus") Byte auditStatus) {
        bookIndexMapper.update(update(BookIndexDynamicSqlSupport.bookIndex)
            .set(BookIndexDynamicSqlSupport.auditStatus).equalTo(auditStatus)
            .set(BookIndexDynamicSqlSupport.updateTime).equalTo(new Date())
            .where(BookIndexDynamicSqlSupport.id, isEqualTo(id))
            .build().render(RenderingStrategies.MYBATIS3));
        return R.ok();
    }
}
