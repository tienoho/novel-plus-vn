package com.java2nb.novel.controller;

import com.java2nb.common.utils.PageBean;
import com.java2nb.common.utils.Query;
import com.java2nb.common.utils.R;
import com.java2nb.novel.domain.BookContentDO;
import com.java2nb.novel.entity.BookIndex;
import com.java2nb.novel.mapper.BookIndexDynamicSqlSupport;
import com.java2nb.novel.mapper.BookIndexMapper;
import com.java2nb.novel.service.BookContentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.mybatis.dynamic.sql.render.RenderingStrategies;
import org.mybatis.dynamic.sql.select.render.SelectStatementProvider;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;
import static org.mybatis.dynamic.sql.select.SelectDSL.select;

/**
 * Controller kiểm tra nghi vấn đạo văn và so sánh văn bản (Plagiarism Inspection & Diff)
 */
@Controller
@RequestMapping({"/novel/plagiarism", "/novel/moderation/plagiarism"})
@Slf4j
@RequiredArgsConstructor
public class PlagiarismInspectionController {

    private final BookIndexMapper bookIndexMapper;
    private final BookContentService bookContentService;

    /**
     * Danh sách các chương bị đánh dấu nghi ngờ đạo văn / chờ duyệt
     */
    @ResponseBody
    @GetMapping("/flagged")
    @RequiresPermissions("novel:book:book")
    public R listFlagged(@RequestParam Map<String, Object> params) {
        Query query = new Query(params);

        SelectStatementProvider selectStatement = select(BookIndexDynamicSqlSupport.bookIndex.allColumns())
            .from(BookIndexDynamicSqlSupport.bookIndex)
            .where(BookIndexDynamicSqlSupport.auditStatus, isEqualTo((byte) 0))
            .limit(query.getLimit())
            .offset(query.getOffset())
            .build().render(RenderingStrategies.MYBATIS3);
        List<BookIndex> list = bookIndexMapper.selectMany(selectStatement);

        SelectStatementProvider countSelect = select(org.mybatis.dynamic.sql.SqlBuilder.count(BookIndexDynamicSqlSupport.id))
            .from(BookIndexDynamicSqlSupport.bookIndex)
            .where(BookIndexDynamicSqlSupport.auditStatus, isEqualTo((byte) 0))
            .build().render(RenderingStrategies.MYBATIS3);
        long total = bookIndexMapper.count(countSelect);

        PageBean pageBean = new PageBean(list, (int) total);
        return R.ok().put("data", pageBean);
    }

    /**
     * So sánh văn bản chương gốc vs chương nghi ngờ đạo văn
     */
    @ResponseBody
    @GetMapping("/diff")
    @RequiresPermissions("novel:book:book")
    public R compareDiff(@RequestParam("indexId1") Long indexId1, @RequestParam("indexId2") Long indexId2) {
        BookContentDO content1 = bookContentService.get(indexId1);
        BookContentDO content2 = bookContentService.get(indexId2);

        Map<String, Object> result = new HashMap<>();
        result.put("chapter1", content1);
        result.put("chapter2", content2);
        return R.ok().put("data", result);
    }
}
