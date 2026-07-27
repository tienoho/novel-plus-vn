package com.java2nb.novel.controller;

import com.java2nb.common.utils.PageBean;
import com.java2nb.common.utils.Query;
import com.java2nb.common.utils.R;
import com.java2nb.novel.core.enums.CopyrightReportStatusEnum;
import com.java2nb.novel.entity.CopyrightReport;
import com.java2nb.novel.mapper.BookDynamicSqlSupport;
import com.java2nb.novel.mapper.BookIndexDynamicSqlSupport;
import com.java2nb.novel.mapper.BookIndexMapper;
import com.java2nb.novel.mapper.BookMapper;
import com.java2nb.novel.mapper.CopyrightReportDynamicSqlSupport;
import com.java2nb.novel.mapper.CopyrightReportMapper;
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
 * Controller quản lý và xử lý báo cáo vi phạm bản quyền (DMCA Takedown Admin)
 */
@Controller
@RequestMapping({"/novel/copyrightReport", "/novel/copyright/report"})
@Slf4j
@RequiredArgsConstructor
public class CopyrightReportAdminController {

    private final CopyrightReportMapper copyrightReportMapper;
    private final BookMapper bookMapper;
    private final BookIndexMapper bookIndexMapper;

    /**
     * Danh sách báo cáo vi phạm bản quyền
     */
    @ResponseBody
    @GetMapping("/list")
    @RequiresPermissions("novel:book:book")
    public R list(@RequestParam Map<String, Object> params) {
        Query query = new Query(params);

        SelectStatementProvider selectStatement = select(CopyrightReportDynamicSqlSupport.copyrightReport.allColumns())
            .from(CopyrightReportDynamicSqlSupport.copyrightReport)
            .orderBy(CopyrightReportDynamicSqlSupport.createTime.descending())
            .limit(query.getLimit())
            .offset(query.getOffset())
            .build().render(RenderingStrategies.MYBATIS3);
        List<CopyrightReport> list = copyrightReportMapper.selectMany(selectStatement);

        SelectStatementProvider countSelect = select(org.mybatis.dynamic.sql.SqlBuilder.count(CopyrightReportDynamicSqlSupport.id))
            .from(CopyrightReportDynamicSqlSupport.copyrightReport)
            .build().render(RenderingStrategies.MYBATIS3);
        long total = copyrightReportMapper.count(countSelect);

        PageBean pageBean = new PageBean(list, (int) total);
        return R.ok().put("data", pageBean);
    }

    /**
     * Xử lý/duyệt báo cáo vi phạm (Duyệt gỡ bỏ tác phẩm - Takedown / Từ chối)
     */
    @ResponseBody
    @PostMapping("/audit")
    @RequiresPermissions("novel:book:edit")
    public R audit(@RequestParam("id") Long id, @RequestParam("status") Byte status,
                   @RequestParam(value = "auditOpinion", required = false) String auditOpinion) {
        CopyrightReport report = copyrightReportMapper.selectByPrimaryKey(id).orElse(null);
        if (report == null) {
            return R.error("Không tìm thấy đơn báo cáo");
        }

        report.setStatus(status);
        report.setReviewResult(auditOpinion);
        report.setReviewedAt(new Date());
        report.setUpdateTime(new Date());
        copyrightReportMapper.updateByPrimaryKeySelective(report);

        // Nếu duyệt báo cáo bản quyền (Chấp nhận gỡ bỏ - Status 2: TAKEDOWN_EXECUTED)
        if (status == CopyrightReportStatusEnum.TAKEDOWN_EXECUTED.getCode()) {
            Long bookId = report.getBookId();
            if (bookId != null) {
                // Set book audit_status = 3 (Takedown)
                bookMapper.update(update(BookDynamicSqlSupport.book)
                    .set(BookDynamicSqlSupport.auditStatus).equalTo((byte) 3)
                    .set(BookDynamicSqlSupport.auditReason).equalTo("Bị gỡ bỏ do vi phạm bản quyền: " + report.getReportNo())
                    .set(BookDynamicSqlSupport.updateTime).equalTo(new Date())
                    .where(BookDynamicSqlSupport.id, isEqualTo(bookId))
                    .build().render(RenderingStrategies.MYBATIS3));

                // Set chapter audit_status = 3 (Takedown)
                bookIndexMapper.update(update(BookIndexDynamicSqlSupport.bookIndex)
                    .set(BookIndexDynamicSqlSupport.auditStatus).equalTo((byte) 3)
                    .set(BookIndexDynamicSqlSupport.updateTime).equalTo(new Date())
                    .where(BookIndexDynamicSqlSupport.bookId, isEqualTo(bookId))
                    .build().render(RenderingStrategies.MYBATIS3));
            }
        }
        return R.ok();
    }
}
