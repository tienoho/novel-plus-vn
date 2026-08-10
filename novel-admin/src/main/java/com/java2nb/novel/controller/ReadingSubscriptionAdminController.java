package com.java2nb.novel.controller;

import com.java2nb.common.annotation.Log;
import com.java2nb.common.controller.BaseController;
import com.java2nb.common.utils.R;
import com.java2nb.novel.config.ReadingSubscriptionAdminProperties;
import com.java2nb.novel.service.subscription.ReadingSubscriptionActivationCommand;
import com.java2nb.novel.service.subscription.ReadingSubscriptionPlanCommand;
import com.java2nb.novel.service.subscription.ReadingSubscriptionService;
import com.java2nb.novel.service.subscription.ReadingSubscriptionRenewalService;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Date;

@Controller
@RequestMapping("/novel/readingSubscription")
public class ReadingSubscriptionAdminController extends BaseController {
    private final ReadingSubscriptionService service;
    private final ReadingSubscriptionAdminProperties properties;
    private final ReadingSubscriptionRenewalService renewalService;

    public ReadingSubscriptionAdminController(ReadingSubscriptionService service,
                                              ReadingSubscriptionAdminProperties properties,
                                              ReadingSubscriptionRenewalService renewalService) {
        this.service = service;
        this.properties = properties;
        this.renewalService = renewalService;
    }

    @GetMapping
    @RequiresPermissions("novel:readingSubscription:view")
    public String index() {
        return "novel/readingSubscription/readingSubscription";
    }

    @ResponseBody
    @GetMapping("/plans")
    @RequiresPermissions("novel:readingSubscription:view")
    public R listPlans() {
        return R.ok().put("data", service.listPlansForAdmin());
    }

    @ResponseBody
    @GetMapping("/current")
    @RequiresPermissions("novel:readingSubscription:view")
    public R getCurrent(@RequestParam long userId) {
        return R.ok().put("data", service.getCurrentSubscription(userId));
    }

    @ResponseBody
    @GetMapping("/purchases")
    @RequiresPermissions("novel:readingSubscription:review")
    public R listPurchaseReviews(
        @RequestParam(defaultValue = "PAID_REVIEW") String status,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok().put("data", service.listPurchaseReviews(status, page, pageSize));
    }

    @ResponseBody
    @PostMapping("/purchases/retry")
    @RequiresPermissions("novel:readingSubscription:review")
    @Log("Thử kích hoạt lại đơn thuê bao đã thanh toán")
    public R retryPurchaseActivation(@RequestParam long purchaseId,
                                     @RequestParam long expectedVersion,
                                     @RequestParam String reason) {
        return R.ok().put("data", service.retryPurchaseActivation(
            purchaseId, expectedVersion, getUserId(), reason));
    }

    @ResponseBody
    @PostMapping("/purchases/refund")
    @RequiresPermissions("novel:readingSubscription:review")
    @Log("Chuyển đơn thuê bao sang quy trình hoàn tiền")
    public R sendPurchaseToRefund(@RequestParam long purchaseId,
                                  @RequestParam long expectedVersion,
                                  @RequestParam String reason) {
        return R.ok().put("data", service.sendPurchaseToRefund(
            purchaseId, expectedVersion, getUserId(), reason));
    }

    @ResponseBody
    @GetMapping("/renewals")
    @RequiresPermissions("novel:readingSubscription:review")
    public R listRenewalQueue(@RequestParam(defaultValue = "PROVIDER_PENDING") String status,
                              @RequestParam(defaultValue = "50") int limit) {
        return R.ok().put("data", renewalService.listRenewalQueue(status, limit));
    }

    @ResponseBody
    @GetMapping("/renewals/{cycleId}/attempts")
    @RequiresPermissions("novel:readingSubscription:review")
    public R listRenewalAttempts(@PathVariable long cycleId,
                                 @RequestParam(defaultValue = "50") int limit) {
        return R.ok().put("data", renewalService.listRenewalAttempts(cycleId, limit));
    }

    @ResponseBody
    @GetMapping("/renewals/{cycleId}/audits")
    @RequiresPermissions("novel:readingSubscription:review")
    public R listRenewalAudits(@PathVariable long cycleId,
                               @RequestParam(defaultValue = "50") int limit) {
        return R.ok().put("data", renewalService.listRenewalAdminAudits(cycleId, limit));
    }

    @ResponseBody
    @PostMapping("/renewals/retry")
    @RequiresPermissions("novel:readingSubscription:review")
    @Log("Lên lịch thử lại cycle gia hạn thuê bao")
    public R retryRenewal(@RequestParam long cycleId,
                          @RequestParam long expectedVersion,
                          @RequestParam String reason) {
        return R.ok().put("data", renewalService.adminScheduleRetry(
            cycleId, expectedVersion, getUserId(), reason, new Date()));
    }

    @ResponseBody
    @GetMapping("/mandates")
    @RequiresPermissions("novel:readingSubscription:review")
    public R listMandateQueue(@RequestParam(defaultValue = "REVOKE_PENDING") String status,
                              @RequestParam(defaultValue = "50") int limit) {
        return R.ok().put("data", service.listMandateQueue(status, limit));
    }

    @ResponseBody
    @GetMapping("/mandates/{mandateId}/audits")
    @RequiresPermissions("novel:readingSubscription:review")
    public R listMandateAudits(@PathVariable long mandateId,
                               @RequestParam(defaultValue = "50") int limit) {
        return R.ok().put("data", service.listMandateAdminAudits(mandateId, limit));
    }

    @ResponseBody
    @PostMapping("/mandates/retry")
    @RequiresPermissions("novel:readingSubscription:review")
    @Log("Lên lịch thử lại thu hồi ủy quyền VNPAY Recurring")
    public R retryMandateRevocation(@RequestParam long mandateId,
                                    @RequestParam long expectedVersion,
                                    @RequestParam String reason) {
        return R.ok().put("data", service.adminScheduleMandateRevocationRetry(
            mandateId, expectedVersion, getUserId(), reason, new Date()));
    }

    @ResponseBody
    @PostMapping("/plans/create")
    @RequiresPermissions("novel:readingSubscription:config")
    @Log("Tạo gói thuê bao Vé đọc")
    public R createPlan(@RequestParam String planCode, @RequestParam String planName,
                        @RequestParam long priceVnd,
                        @RequestParam(required = false) Long priceXu,
                        @RequestParam long ticketsPerPeriod, @RequestParam int periodMonths,
                        @RequestParam int ticketValidityDays) {
        return R.ok().put("data", service.createPlan(new ReadingSubscriptionPlanCommand(
            planCode, planName, priceVnd, priceXu, ticketsPerPeriod, periodMonths,
            ticketValidityDays)));
    }

    @ResponseBody
    @PostMapping("/plans/update")
    @RequiresPermissions("novel:readingSubscription:config")
    @Log("Cập nhật gói thuê bao Vé đọc")
    public R updatePlan(@RequestParam long planId, @RequestParam long expectedVersion,
                        @RequestParam String planCode, @RequestParam String planName,
                        @RequestParam long priceVnd,
                        @RequestParam(required = false) Long priceXu,
                        @RequestParam long ticketsPerPeriod, @RequestParam int periodMonths,
                        @RequestParam int ticketValidityDays) {
        return R.ok().put("data", service.updatePlan(planId, expectedVersion,
            new ReadingSubscriptionPlanCommand(planCode, planName, priceVnd, priceXu, ticketsPerPeriod,
                periodMonths, ticketValidityDays)));
    }

    @ResponseBody
    @PostMapping("/plans/status")
    @RequiresPermissions("novel:readingSubscription:config")
    @Log("Đổi trạng thái gói thuê bao Vé đọc")
    public R changePlanStatus(@RequestParam long planId, @RequestParam long expectedVersion,
                              @RequestParam String status) {
        return R.ok().put("data", service.changePlanStatus(planId, expectedVersion, status));
    }

    @ResponseBody
    @PostMapping("/activate")
    @RequiresPermissions("novel:readingSubscription:activate")
    @Log("Kích hoạt thủ công thuê bao Vé đọc")
    public R activate(@RequestParam long userId, @RequestParam String planCode,
                      @RequestParam long startAtMillis,
                      @RequestParam(required = false) Long endAtMillis,
                      @RequestParam String clientRequestId) {
        requireActivationEnabled();
        String requestId = normalizeRequestId(clientRequestId);
        Date startAt = new Date(startAtMillis);
        Date endAt = endAtMillis == null ? null : new Date(endAtMillis);
        return R.ok().put("data", service.activate(new ReadingSubscriptionActivationCommand(
            userId, planCode, startAt, endAt, "ADMIN",
            "ADMIN:" + getUserId() + ':' + requestId, properties.getPolicyVersion())));
    }

    private void requireActivationEnabled() {
        if (!properties.isActivationEnabled() || !properties.isConfigured()) {
            throw new IllegalStateException("Kích hoạt thuê bao thủ công đang tắt hoặc cấu hình chưa hợp lệ");
        }
    }

    private String normalizeRequestId(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!normalized.matches("[A-Za-z0-9_-]{8,64}")) {
            throw new IllegalArgumentException("Mã yêu cầu kích hoạt không hợp lệ");
        }
        return normalized;
    }
}
