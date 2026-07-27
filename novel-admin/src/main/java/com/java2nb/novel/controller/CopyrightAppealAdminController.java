package com.java2nb.novel.controller;

import com.java2nb.common.utils.PageBean;
import com.java2nb.common.utils.Query;
import com.java2nb.common.utils.R;
import com.java2nb.novel.core.enums.CopyrightReportStatusEnum;
import com.java2nb.novel.entity.CopyrightAppeal;
import com.java2nb.novel.entity.CopyrightReport;
import com.java2nb.novel.mapper.*;
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
 * Controller xử lý kháng nghị bản quyền của tác giả (Counter-Notice Appeal Admin)
 */
@Controller
@RequestMapping({"/novel/copyrightAppeal", "/novel/copyright/appeal"})
@Slf4j
@RequiredArgsConstructor
public class CopyrightAppealAdminController {

    private final CopyrightAppealMapper copyrightAppealMapper;
    private final CopyrightReportMapper copyrightReportMapper;
    private final BookMapper bookMapper;
    private final BookIndexMapper bookIndexMapper;

    /**
     * Danh sách kháng nghị từ tác giả
     */
    @ResponseBody
    @GetMapping("/list")
    @RequiresPermissions("novel:book:book")
    public R list(@RequestParam Map<String, Object> params) {
        Query query = new Query(params);

        SelectStatementProvider selectStatement = select(CopyrightAppealDynamicSqlSupport.copyrightAppeal.allColumns())
            .from(CopyrightAppealDynamicSqlSupport.copyrightAppeal)
            .orderBy(CopyrightAppealDynamicSqlSupport.createTime.descending())
            .limit(query.getLimit())
            .offset(query.getOffset())
            .build().render(RenderingStrategies.MYBATIS3);
        List<CopyrightAppeal> list = copyrightAppealMapper.selectMany(selectStatement);

        SelectStatementProvider countSelect = select(org.mybatis.dynamic.sql.SqlBuilder.count(CopyrightAppealDynamicSqlSupport.id))
            .from(CopyrightAppealDynamicSqlSupport.copyrightAppeal)
            .build().render(RenderingStrategies.MYBATIS3);
        long total = copyrightAppealMapper.count(countSelect);

        PageBean pageBean = new PageBean(list, (int) total);
        return R.ok().put("data", pageBean);
    }

    /**
     * Duyệt đơn kháng nghị (Chấp nhận kháng nghị -> Phục hồi tác phẩm / Từ chối kháng nghị)
     */
    @ResponseBody
    @PostMapping("/audit")
    @RequiresPermissions("novel:book:edit")
    public R audit(@RequestParam("id") Long id, @RequestParam("status") Byte status,
                   @RequestParam(value = "auditOpinion", required = false) String auditOpinion) {
        CopyrightAppeal appeal = copyrightAppealMapper.selectByPrimaryKey(id).orElse(null);
        if (appeal == null) {
            return R.error("Không tìm thấy đơn kháng nghị");
        }

        appeal.setStatus(status);
        appeal.setReviewRemark(auditOpinion);
        appeal.setReviewedAt(new Date());
        appeal.setUpdateTime(new Date());
        copyrightAppealMapper.updateByPrimaryKeySelective(appeal);

        // Nếu chấp nhận kháng nghị (Status 1: ACCEPTED) -> Phục hồi tác phẩm
        if (status == 1) {
            Long bookId = appeal.getBookId();
            if (bookId != null) {
                // Khôi phục book audit_status = 1 (Approved)
                bookMapper.update(update(BookDynamicSqlSupport.book)
                    .set(BookDynamicSqlSupport.auditStatus).equalTo((byte) 1)
                    .set(BookDynamicSqlSupport.auditReason).equalTo("Đã chấp nhận đơn kháng nghị bản quyền")
                    .set(BookDynamicSqlSupport.updateTime).equalTo(new Date())
                    .where(BookDynamicSqlSupport.id, isEqualTo(bookId))
                    .build().render(RenderingStrategies.MYBATIS3));

                // Khôi phục chapter audit_status = 1 (Approved)
                bookIndexMapper.update(update(BookIndexDynamicSqlSupport.bookIndex)
                    .set(BookIndexDynamicSqlSupport.auditStatus).equalTo((byte) 1)
                    .set(BookIndexDynamicSqlSupport.updateTime).equalTo(new Date())
                    .where(BookIndexDynamicSqlSupport.bookId, isEqualTo(bookId))
                    .build().render(RenderingStrategies.MYBATIS3));
            }

            // Cập nhật trạng thái báo cáo vi phạm liên quan thành Resolved (Status 5)
            if (appeal.getReportId() != null) {
                CopyrightReport report = copyrightReportMapper.selectByPrimaryKey(appeal.getReportId()).orElse(null);
                if (report != null) {
                    report.setStatus((byte) CopyrightReportStatusEnum.RESOLVED.getCode());
                    report.setUpdateTime(new Date());
                    copyrightReportMapper.updateByPrimaryKeySelective(report);
                }
            }
        }
        return R.ok();
    }
}
