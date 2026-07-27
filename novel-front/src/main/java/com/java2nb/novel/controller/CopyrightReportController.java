package com.java2nb.novel.controller;

import com.java2nb.novel.core.bean.UserDetails;
import com.java2nb.novel.core.enums.CopyrightReportStatusEnum;
import com.java2nb.novel.core.enums.ResponseStatus;
import com.java2nb.novel.entity.CopyrightReport;
import com.java2nb.novel.mapper.CopyrightReportDynamicSqlSupport;
import com.java2nb.novel.mapper.CopyrightReportMapper;
import io.github.xxyopen.model.resp.RestResult;
import io.github.xxyopen.util.IdWorker;
import com.java2nb.novel.core.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.dynamic.sql.render.RenderingStrategies;
import org.mybatis.dynamic.sql.select.render.SelectStatementProvider;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;

import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;
import static org.mybatis.dynamic.sql.select.SelectDSL.select;

import com.java2nb.novel.entity.Author;
import com.java2nb.novel.entity.BookOwnershipProof;
import com.java2nb.novel.entity.CopyrightAppeal;
import com.java2nb.novel.mapper.BookOwnershipProofMapper;
import com.java2nb.novel.mapper.CopyrightAppealMapper;
import com.java2nb.novel.service.AuthorService;
import com.java2nb.novel.core.utils.ContentHashUtil;

/**
 * Controller tiếp nhận báo cáo vi phạm bản quyền (DMCA Intake)
 */
@RequestMapping("copyright")
@RestController
@Slf4j
@RequiredArgsConstructor
public class CopyrightReportController extends BaseController {

    private final CopyrightReportMapper copyrightReportMapper;
    private final CopyrightAppealMapper copyrightAppealMapper;
    private final BookOwnershipProofMapper bookOwnershipProofMapper;
    private final AuthorService authorService;

    /**
     * Gửi báo cáo vi phạm bản quyền
     */
    @PostMapping("report")
    public RestResult<String> submitReport(@RequestBody CopyrightReport report, HttpServletRequest request) {
        if (report.getTargetId() == null || report.getReporterName() == null || report.getReporterEmail() == null) {
            throw new com.java2nb.novel.core.exception.BusinessException("Thông tin báo cáo không hợp lệ");
        }

        UserDetails userDetails = getUserDetails(request);
        Long userId = userDetails != null ? userDetails.getId() : null;

        String reportNo = "CR" + System.currentTimeMillis() + (int)(Math.random() * 1000);
        report.setReportNo(reportNo);
        report.setReporterId(userId);
        report.setStatus((byte) CopyrightReportStatusEnum.PENDING.getCode());
        report.setCreateTime(new Date());
        report.setUpdateTime(new Date());

        copyrightReportMapper.insertSelective(report);
        return RestResult.ok(reportNo);
    }

    /**
     * Tra cứu trạng thái xử lý báo cáo theo mã báo cáo
     */
    @GetMapping("report/status")
    public RestResult<CopyrightReport> getReportStatus(@RequestParam("reportNo") String reportNo) {
        SelectStatementProvider selectStatement = select(CopyrightReportDynamicSqlSupport.copyrightReport.allColumns())
            .from(CopyrightReportDynamicSqlSupport.copyrightReport)
            .where(CopyrightReportDynamicSqlSupport.reportNo, isEqualTo(reportNo))
            .build().render(RenderingStrategies.MYBATIS3);
        List<CopyrightReport> list = copyrightReportMapper.selectMany(selectStatement);
        if (list.isEmpty()) {
            return RestResult.ok(null);
        }
        return RestResult.ok(list.get(0));
    }

    /**
     * Gửi đơn kháng nghị gỡ bỏ tác phẩm
     */
    @PostMapping("appeal")
    public RestResult<Void> submitAppeal(@RequestBody CopyrightAppeal appeal, HttpServletRequest request) {
        UserDetails userDetails = getUserDetails(request);
        if (userDetails == null) {
            throw new BusinessException(ResponseStatus.NO_LOGIN);
        }
        Author author = authorService.queryAuthor(userDetails.getId());
        if (author != null) {
            appeal.setAuthorId(author.getId());
        }
        appeal.setStatus((byte) 0);
        appeal.setCreateTime(new Date());
        copyrightAppealMapper.insertSelective(appeal);

        if (appeal.getReportId() != null) {
            CopyrightReport report = copyrightReportMapper.selectByPrimaryKey(appeal.getReportId()).orElse(null);
            if (report != null) {
                report.setStatus((byte) CopyrightReportStatusEnum.COUNTER_NOTICE_RECEIVED.getCode());
                report.setUpdateTime(new Date());
                copyrightReportMapper.updateByPrimaryKeySelective(report);
            }
        }
        return RestResult.ok();
    }

    /**
     * Tải lên bằng chứng sở hữu bản quyền tác phẩm
     */
    @PostMapping("proof/upload")
    public RestResult<Void> uploadProof(@RequestBody BookOwnershipProof proof, HttpServletRequest request) {
        UserDetails userDetails = getUserDetails(request);
        if (userDetails == null) {
            throw new BusinessException(ResponseStatus.NO_LOGIN);
        }
        Author author = authorService.queryAuthor(userDetails.getId());
        if (author != null) {
            proof.setAuthorId(author.getId());
        }
        String hashContent = proof.getNote() != null ? proof.getNote() : proof.getFileUrl();
        if (hashContent != null) {
            proof.setFileHash(ContentHashUtil.sha256Hex(hashContent));
        }
        proof.setVerificationStatus((byte) 0);
        proof.setCreateTime(new Date());
        bookOwnershipProofMapper.insertSelective(proof);
        return RestResult.ok();
    }
}
