package com.java2nb.novel.copyright;

import com.java2nb.novel.core.enums.CopyrightReportStatusEnum;
import com.java2nb.novel.entity.Book;
import com.java2nb.novel.entity.CopyrightAppeal;
import com.java2nb.novel.entity.CopyrightReport;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

public class CopyrightReportWorkflowTest {

    @Test
    public void testCopyrightReportLifecycle() {
        // 1. Submit DMCA Report
        CopyrightReport report = new CopyrightReport();
        report.setReportNo("CR" + System.currentTimeMillis());
        report.setTargetId(1001L);
        report.setReporterName("Bên Sở Hữu Bản Quyền ABC");
        report.setReporterEmail("legal@abc.com");
        report.setStatus((byte) CopyrightReportStatusEnum.PENDING.getCode());

        assertEquals((byte) 0, report.getStatus());

        // 2. Admin Approves Takedown
        report.setStatus((byte) CopyrightReportStatusEnum.APPROVED_TAKEDOWN.getCode());
        Book book = new Book();
        book.setId(1001L);
        book.setAuditStatus((byte) 3); // 3: Takedown

        assertEquals((byte) 3, book.getAuditStatus());
        assertEquals((byte) 2, report.getStatus());

        // 3. Author Submits Counter-Appeal
        CopyrightAppeal appeal = new CopyrightAppeal();
        appeal.setReportId(report.getId());
        appeal.setBookId(1001L);
        appeal.setAuthorId(501L);
        appeal.setAppealReason("Tôi là tác giả gốc có chứng nhận đăng ký bản quyền số 12345.");
        appeal.setStatus((byte) 0); // 0: Pending Review

        report.setStatus((byte) CopyrightReportStatusEnum.COUNTER_NOTICE_RECEIVED.getCode());
        assertEquals((byte) 4, report.getStatus());

        // 4. Admin Accepts Counter-Appeal & Restores Book
        appeal.setStatus((byte) 1); // 1: Accepted
        book.setAuditStatus((byte) 1); // 1: Approved / Restored
        report.setStatus((byte) CopyrightReportStatusEnum.RESOLVED.getCode());

        assertEquals((byte) 1, book.getAuditStatus());
        assertEquals((byte) 5, report.getStatus());
    }
}
