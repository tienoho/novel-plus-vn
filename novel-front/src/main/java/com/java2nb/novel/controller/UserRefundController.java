package com.java2nb.novel.controller;

import com.java2nb.novel.core.bean.UserDetails;
import com.java2nb.novel.entity.OrderRefund;
import com.java2nb.novel.service.RefundService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

@RestController
@RequestMapping("user/refund")
@RequiredArgsConstructor
@Slf4j
public class UserRefundController extends BaseController {

    private final RefundService refundService;

    @PostMapping("request")
    public Map<String, Object> requestRefund(@RequestParam("outTradeNo") Long outTradeNo,
                                              @RequestParam(value = "reason", required = false) String reason,
                                              HttpServletRequest request) {
        UserDetails userDetails = getUserDetails(request);
        if (userDetails == null) {
            return Map.of("code", 401, "msg", "Chưa đăng nhập");
        }
        if (outTradeNo == null) {
            return Map.of("code", 400, "msg", "Thiếu mã đơn thanh toán");
        }
        try {
            OrderRefund refund = refundService.requestRefund(userDetails.getId(), outTradeNo, reason);
            return Map.of("code", 200, "msg", "Gửi yêu cầu hoàn tiền thành công", "data", refund);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Map.of("code", 400, "msg", e.getMessage());
        } catch (Exception e) {
            log.error("Lỗi gửi yêu cầu hoàn tiền", e);
            return Map.of("code", 500, "msg", "Lỗi xử lý hệ thống");
        }
    }
}
