package com.java2nb.novel.controller;

import com.java2nb.common.annotation.Log;
import com.java2nb.common.controller.BaseController;
import com.java2nb.common.utils.R;
import com.java2nb.novel.service.gift.GiftCampaignCommand;
import com.java2nb.novel.service.gift.GiftCodeService;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Date;

@Controller
@RequestMapping("/novel/giftCode")
public class GiftCodeAdminController extends BaseController {
    private final GiftCodeService service;

    public GiftCodeAdminController(GiftCodeService service) {
        this.service = service;
    }

    @GetMapping
    @RequiresPermissions("novel:giftCode:view")
    public String index() {
        return "novel/giftCode/giftCode";
    }

    @ResponseBody
    @GetMapping("/campaigns")
    @RequiresPermissions("novel:giftCode:view")
    public R listCampaigns() {
        return R.ok().put("data", service.listCampaigns());
    }

    @ResponseBody
    @GetMapping("/codes")
    @RequiresPermissions("novel:giftCode:view")
    public R listCodes(@RequestParam long campaignId,
                       @RequestParam(defaultValue = "1") int page,
                       @RequestParam(defaultValue = "50") int limit) {
        return R.ok().put("data", service.listCodes(campaignId, page, limit));
    }

    @ResponseBody
    @GetMapping("/redemptions")
    @RequiresPermissions("novel:giftCode:view")
    public R listRedemptions(@RequestParam long campaignId,
                             @RequestParam(defaultValue = "1") int page,
                             @RequestParam(defaultValue = "50") int limit) {
        return R.ok().put("data", service.listCampaignRedemptions(campaignId, page, limit));
    }

    @ResponseBody
    @PostMapping("/campaigns/create")
    @RequiresPermissions("novel:giftCode:config")
    @Log("Tạo chiến dịch mã quà")
    public R createCampaign(@RequestParam String campaignCode,
                            @RequestParam String campaignName,
                            @RequestParam String rewardType,
                            @RequestParam long rewardAmount,
                            @RequestParam(required = false) Integer ticketValidityDays,
                            @RequestParam long startAtMillis,
                            @RequestParam long endAtMillis,
                            @RequestParam long maxRedemptions,
                            @RequestParam int maxPerUser) {
        return R.ok().put("data", service.createCampaign(new GiftCampaignCommand(
            campaignCode, campaignName, rewardType, rewardAmount, ticketValidityDays,
            new Date(startAtMillis), new Date(endAtMillis), maxRedemptions, maxPerUser)));
    }

    @ResponseBody
    @PostMapping("/campaigns/status")
    @RequiresPermissions("novel:giftCode:config")
    @Log("Đổi trạng thái chiến dịch mã quà")
    public R changeStatus(@RequestParam long campaignId,
                          @RequestParam long expectedVersion,
                          @RequestParam String status) {
        return R.ok().put("data", service.changeCampaignStatus(
            campaignId, expectedVersion, status));
    }

    @ResponseBody
    @PostMapping("/codes/issue")
    @RequiresPermissions("novel:giftCode:issue")
    @Log("Phát hành mã quà")
    public R issueCodes(@RequestParam long campaignId,
                        @RequestParam int quantity,
                        @RequestParam long maxRedemptionsPerCode) {
        return R.ok().put("data", service.issueCodes(
            campaignId, quantity, maxRedemptionsPerCode));
    }

    @ResponseBody
    @PostMapping("/codes/revoke")
    @RequiresPermissions("novel:giftCode:revoke")
    @Log("Thu hồi mã quà chưa sử dụng")
    public R revokeCode(@RequestParam long codeId,
                        @RequestParam long expectedVersion) {
        return R.ok().put("data", service.revokeCode(codeId, expectedVersion));
    }
}
