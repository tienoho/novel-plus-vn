package com.java2nb.novel.controller;

import com.java2nb.novel.common.entity.FinancialVoucherDO;
import com.java2nb.novel.common.service.FinancialVoucherService;
import com.java2nb.novel.core.bean.UserDetails;
import com.java2nb.novel.core.exception.BusinessException;
import com.java2nb.novel.entity.Author;
import com.java2nb.novel.service.AuthorService;
import io.github.xxyopen.model.resp.RestResult;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class AuthorFinanceReceiptsControllerTest {

    private FinancialVoucherService voucherService;
    private AuthorService authorService;
    private com.java2nb.novel.core.utils.JwtTokenUtil jwtTokenUtil;
    private AuthorFinanceReceiptsController controller;

    @BeforeEach
    public void setUp() {
        voucherService = mock(FinancialVoucherService.class);
        authorService = mock(AuthorService.class);
        jwtTokenUtil = mock(com.java2nb.novel.core.utils.JwtTokenUtil.class);
        controller = new AuthorFinanceReceiptsController(voucherService, authorService);
        controller.setJwtTokenUtil(jwtTokenUtil);
    }

    private HttpServletRequest createAuthenticatedRequest(UserDetails userDetails) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer test-token");
        when(jwtTokenUtil.getUserDetailsFromToken("Bearer test-token")).thenReturn(userDetails);
        return request;
    }

    @Test
    public void testListReceipts() {
        UserDetails userDetails = new UserDetails();
        userDetails.setId(101L);
        HttpServletRequest request = createAuthenticatedRequest(userDetails);

        Author author = new Author();
        author.setId(501L);
        author.setPenName("TestAuthor");
        author.setStatus((byte) 0);

        when(request.getAttribute("user")).thenReturn(userDetails);
        when(authorService.queryAuthor(101L)).thenReturn(author);

        List<FinancialVoucherDO> list = new ArrayList<>();
        list.add(FinancialVoucherDO.builder().voucherNo("VOUCHER-20260725-11").payeeName("TestAuthor").build());
        when(voucherService.listAuthorVouchers(501L)).thenReturn(list);

        RestResult<List<FinancialVoucherDO>> result = controller.listReceipts(request);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals(1, result.getData().size());
        assertEquals("VOUCHER-20260725-11", result.getData().get(0).getVoucherNo());
        verify(voucherService).listAuthorVouchers(501L);
        verify(voucherService, never()).listVouchers(any());
    }

    @Test
    public void testDownloadPdf() {
        UserDetails userDetails = new UserDetails();
        userDetails.setId(101L);
        HttpServletRequest request = createAuthenticatedRequest(userDetails);

        Author author = new Author();
        author.setId(501L);
        author.setPenName("TestAuthor");
        author.setStatus((byte) 0);

        when(authorService.queryAuthor(101L)).thenReturn(author);

        byte[] mockPdf = "%PDF-1.4 Mock PDF".getBytes(StandardCharsets.ISO_8859_1);
        when(voucherService.exportAuthorVoucherPdf("VOUCHER-123", 501L)).thenReturn(mockPdf);

        ResponseEntity<byte[]> response = controller.downloadPdf("VOUCHER-123", request);

        assertNotNull(response);
        assertEquals(200, response.getStatusCodeValue());
        assertArrayEquals(mockPdf, response.getBody());
        verify(voucherService).exportAuthorVoucherPdf("VOUCHER-123", 501L);
        verify(voucherService, never()).exportVoucherPdf("VOUCHER-123");
    }

    @Test
    public void testGetVoucherJson() {
        UserDetails userDetails = new UserDetails();
        userDetails.setId(101L);
        HttpServletRequest request = createAuthenticatedRequest(userDetails);

        Author author = new Author();
        author.setId(501L);
        author.setPenName("TestAuthor");
        author.setStatus((byte) 0);

        when(authorService.queryAuthor(101L)).thenReturn(author);

        FinancialVoucherDO voucher = FinancialVoucherDO.builder().voucherNo("VOUCHER-123").build();
        when(voucherService.getAuthorVoucher("VOUCHER-123", 501L)).thenReturn(voucher);
        when(voucherService.exportVoucherJson(voucher)).thenReturn("{\"voucherNo\":\"VOUCHER-123\"}".getBytes(StandardCharsets.UTF_8));

        ResponseEntity<byte[]> response = controller.getVoucherJson("VOUCHER-123", request);

        assertNotNull(response);
        assertEquals(200, response.getStatusCodeValue());
        assertTrue(new String(response.getBody(), StandardCharsets.UTF_8).contains("VOUCHER-123"));
        verify(voucherService).getAuthorVoucher("VOUCHER-123", 501L);
        verify(voucherService, never()).getByVoucherNo("VOUCHER-123");
    }

    @Test
    public void receiptOutsideAuthorScopeIsIndistinguishableFromMissing() {
        UserDetails userDetails = new UserDetails();
        userDetails.setId(101L);
        HttpServletRequest request = createAuthenticatedRequest(userDetails);
        Author author = new Author();
        author.setId(501L);
        author.setPenName("TestAuthor");
        when(authorService.queryAuthor(101L)).thenReturn(author);
        when(voucherService.getAuthorVoucher("VOUCHER-OTHER", 501L)).thenReturn(null);

        assertThrows(BusinessException.class,
                () -> controller.getReceipt("VOUCHER-OTHER", request));

        verify(voucherService).getAuthorVoucher("VOUCHER-OTHER", 501L);
        verify(voucherService, never()).getByVoucherNo("VOUCHER-OTHER");
    }
}
