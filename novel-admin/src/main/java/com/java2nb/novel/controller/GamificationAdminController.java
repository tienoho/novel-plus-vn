package com.java2nb.novel.controller;

import com.java2nb.common.controller.BaseController;
import com.java2nb.common.annotation.Log;
import com.java2nb.common.utils.PageBean;
import com.java2nb.common.utils.Query;
import com.java2nb.common.utils.R;
import com.java2nb.novel.service.GamificationAdminService;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

@Controller
@RequestMapping("/novel/gamification")
public class GamificationAdminController extends BaseController {

    private final GamificationAdminService service;

    public GamificationAdminController(GamificationAdminService service) {
        this.service = service;
    }

    @GetMapping()
    @RequiresPermissions("novel:gamification:view")
    public String index() {
        return "novel/gamification/gamification";
    }

    @ResponseBody
    @GetMapping("/accounts/list")
    @RequiresPermissions("novel:gamification:view")
    public R listAccounts(@RequestParam Map<String, Object> params) {
        Query query = new Query(params);
        return R.ok().put("data", new PageBean(service.listAccounts(query), service.countAccounts(query)));
    }

    @ResponseBody
    @GetMapping("/ledger/list")
    @RequiresPermissions("novel:gamification:view")
    public R listLedger(@RequestParam Map<String, Object> params) {
        Query query = new Query(params);
        return R.ok().put("data", new PageBean(service.listLedger(query), service.countLedger(query)));
    }

    @ResponseBody
    @GetMapping("/jobs/list")
    @RequiresPermissions("novel:gamification:view")
    public R listJobs(@RequestParam Map<String, Object> params) {
        Query query = new Query(params);
        return R.ok().put("data", new PageBean(service.listJobs(query), service.countJobs(query)));
    }

    @ResponseBody
    @GetMapping("/seasons/list")
    @RequiresPermissions("novel:gamification:view")
    public R listSeasons(@RequestParam Map<String, Object> params) {
        Query query = new Query(params);
        return R.ok().put("data", new PageBean(service.listSeasons(query), service.countSeasons(query)));
    }

    @ResponseBody
    @GetMapping("/rewards/campaigns/list")
    @RequiresPermissions("novel:gamification:view")
    public R listRewardCampaigns(@RequestParam Map<String, Object> params) {
        Query query = new Query(params);
        return R.ok().put("data", new PageBean(service.listRewardCampaigns(query),
            service.countRewardCampaigns(query)));
    }

    @ResponseBody
    @GetMapping("/rewards/allocations/list")
    @RequiresPermissions("novel:gamification:view")
    public R listRewardAllocations(@RequestParam Map<String, Object> params) {
        Query query = new Query(params);
        return R.ok().put("data", new PageBean(service.listRewardAllocations(query),
            service.countRewardAllocations(query)));
    }

    @ResponseBody
    @GetMapping("/quests/campaigns/list")
    @RequiresPermissions("novel:gamification:view")
    public R listQuestCampaigns(@RequestParam Map<String, Object> params) {
        Query query = new Query(params);
        return R.ok().put("data", new PageBean(service.listQuestCampaigns(query),
            service.countQuestCampaigns(query)));
    }

    @ResponseBody
    @GetMapping("/quests/rewards/list")
    @RequiresPermissions("novel:gamification:view")
    public R listQuestRewards(@RequestParam Map<String, Object> params) {
        Query query = new Query(params);
        return R.ok().put("data", new PageBean(service.listQuestRewards(query),
            service.countQuestRewards(query)));
    }

    @ResponseBody
    @PostMapping("/quests/campaigns/create")
    @RequiresPermissions("novel:gamification:config")
    @Log("Tạo campaign nhiệm vụ")
    public R createQuestCampaign(@RequestParam String campaignCode,
                                 @RequestParam long startAtMillis,
                                 @RequestParam long endAtMillis) {
        return R.ok().put("data", service.createQuestCampaign(campaignCode,
            startAtMillis, endAtMillis, getUserId()));
    }

    @ResponseBody
    @PostMapping("/quests/rewards/save")
    @RequiresPermissions("novel:gamification:config")
    @Log("Cấu hình thưởng nhiệm vụ")
    public R saveQuestReward(@RequestParam long campaignId, @RequestParam String questCode,
                             @RequestParam long expAmount, @RequestParam long ticketAmount) {
        service.saveQuestReward(campaignId, questCode, expAmount, ticketAmount, getUserId());
        return R.ok();
    }

    @ResponseBody
    @PostMapping("/quests/campaigns/activate")
    @RequiresPermissions("novel:gamification:config")
    @Log("Kích hoạt campaign nhiệm vụ")
    public R activateQuestCampaign(@RequestParam long campaignId) {
        return R.ok().put("data", service.activateQuestCampaign(campaignId, getUserId()));
    }

    @ResponseBody
    @PostMapping("/quests/campaigns/close")
    @RequiresPermissions("novel:gamification:config")
    @Log("Đóng campaign nhiệm vụ")
    public R closeQuestCampaign(@RequestParam long campaignId) {
        return R.ok().put("data", service.closeQuestCampaign(campaignId, getUserId()));
    }

    @ResponseBody
    @PostMapping("/grant")
    @RequiresPermissions("novel:gamification:grant")
    @Log("Cấp Ngọn Đuốc")
    public R grant(@RequestParam long userId, @RequestParam long amount,
                   @RequestParam String clientRequestId, @RequestParam long effectiveAtMillis,
                   @RequestParam String reason) {
        boolean canAdjust = SecurityUtils.getSubject().isPermitted("novel:gamification:adjust");
        return R.ok().put("data", service.grant(userId, amount, clientRequestId,
            effectiveAtMillis, reason, getUserId(), canAdjust).name());
    }

    @ResponseBody
    @PostMapping("/seasons/close")
    @RequiresPermissions("novel:gamification:finalize")
    @Log("Đóng kỳ xếp hạng Ngọn Đuốc")
    public R closeSeason(@RequestParam long seasonId) {
        return R.ok().put("data", service.closeSeason(seasonId, getUserId()));
    }

    @ResponseBody
    @PostMapping("/seasons/pause")
    @RequiresPermissions("novel:gamification:finalize")
    @Log("Tạm dừng snapshot Ngọn Đuốc")
    public R pauseSeason(@RequestParam long seasonId, @RequestParam String reason) {
        return R.ok().put("data", service.pauseSeason(seasonId, reason, getUserId()));
    }

    @ResponseBody
    @PostMapping("/seasons/retry")
    @RequiresPermissions("novel:gamification:finalize")
    @Log("Chạy lại snapshot Ngọn Đuốc")
    public R retrySeason(@RequestParam long seasonId) {
        return R.ok().put("data", service.retrySeason(seasonId, getUserId()));
    }

    @ResponseBody
    @GetMapping("/seasons/reconcile")
    @RequiresPermissions("novel:gamification:view")
    public R reconcileSeason(@RequestParam long seasonId) {
        return R.ok().put("data", service.reconcileSeason(seasonId));
    }

    @ResponseBody
    @PostMapping("/seasons/finalize")
    @RequiresPermissions("novel:gamification:finalize")
    @Log("Chốt kỳ xếp hạng Ngọn Đuốc")
    public R finalizeSeason(@RequestParam long seasonId) {
        return R.ok().put("data", service.finalizeSeason(seasonId, getUserId()));
    }

    @ResponseBody
    @PostMapping("/seasons/create-special")
    @RequiresPermissions("novel:gamification:finalize")
    @Log("Tạo kỳ đặc biệt Ngọn Đuốc")
    public R createSpecialSeason(@RequestParam String periodCode, @RequestParam String seasonType,
                                 @RequestParam long startAtMillis, @RequestParam long endAtMillis,
                                 @RequestParam long voteCutoffAtMillis) {
        return R.ok().put("data", service.createSpecialSeason(periodCode, seasonType,
            startAtMillis, endAtMillis, voteCutoffAtMillis, getUserId()));
    }

    @ResponseBody
    @GetMapping("/ticker/nicknames/list")
    @RequiresPermissions("novel:gamification:view")
    public R listTickerNicknames(@RequestParam Map<String, Object> params) {
        Query query = new Query(params);
        return R.ok().put("data", new PageBean(service.listTickerNicknames(query),
            service.countTickerNicknames(query)));
    }

    @ResponseBody
    @PostMapping("/ticker/moderate")
    @RequiresPermissions("novel:gamification:review")
    @Log("Kiểm duyệt hiển thị bảng chạy Ngọn Đuốc")
    public R moderateTicker(@RequestParam long userId, @RequestParam boolean hide,
                            @RequestParam String reason) {
        return R.ok().put("data", service.moderateTickerVisibility(userId, hide, reason, getUserId()));
    }

    @ResponseBody
    @GetMapping("/risk-reviews/list")
    @RequiresPermissions("novel:gamification:review")
    public R listRiskReviews(@RequestParam Map<String, Object> params) {
        Query query = new Query(params);
        return R.ok().put("data", new PageBean(service.listRiskReviews(query),
            service.countRiskReviews(query)));
    }

    @ResponseBody
    @PostMapping("/risk-reviews/review")
    @RequiresPermissions("novel:gamification:review")
    @Log("Duyệt cảnh báo gian lận gamification")
    public R reviewRisk(@RequestParam long assessmentId, @RequestParam long expectedVersion,
                        @RequestParam String decision, @RequestParam String reason) {
        return R.ok().put("data", service.reviewRisk(assessmentId, expectedVersion,
            decision, reason, getUserId()));
    }

    @ResponseBody
    @GetMapping("/public-policies/list")
    @RequiresPermissions("novel:gamification:config")
    public R listPublicPolicies(@RequestParam Map<String, Object> params) {
        Query query = new Query(params);
        return R.ok().put("data", new PageBean(service.listPublicPolicies(query),
            service.countPublicPolicies(query)));
    }

    @ResponseBody
    @PostMapping("/public-policies/create")
    @RequiresPermissions("novel:gamification:config")
    @Log("Tạo bản nháp luật chơi gamification")
    public R createPublicPolicy(@RequestParam String policyVersion, @RequestParam String title,
                                @RequestParam String contentText) {
        return R.ok().put("data", service.createPublicPolicy(policyVersion, title,
            contentText, getUserId()));
    }

    @ResponseBody
    @PostMapping("/public-policies/publish")
    @RequiresPermissions("novel:gamification:config")
    @Log("Phát hành luật chơi gamification")
    public R publishPublicPolicy(@RequestParam long policyId,
                                 @RequestParam long expectedVersion) {
        return R.ok().put("data", service.publishPublicPolicy(policyId,
            expectedVersion, getUserId()));
    }

    @ResponseBody
    @PostMapping("/rewards/calculate")
    @RequiresPermissions("novel:gamification:reward")
    @Log("Tính phân bổ quỹ thưởng Ngọn Đuốc")
    public R calculateReward(@RequestParam long seasonId, @RequestParam long budgetXu,
                             @RequestParam String sharesBps) {
        return R.ok().put("data", service.calculateRewardCampaign(seasonId, budgetXu, sharesBps));
    }

    @ResponseBody
    @PostMapping("/rewards/approve")
    @RequiresPermissions("novel:gamification:reward")
    @Log("Duyệt quỹ thưởng Ngọn Đuốc")
    public R approveReward(@RequestParam long campaignId) {
        return R.ok().put("data", service.approveRewardCampaign(campaignId, getUserId()));
    }

    @ResponseBody
    @PostMapping("/rewards/post")
    @RequiresPermissions("novel:gamification:reward")
    @Log("Ghi thưởng Ngọn Đuốc vào clearing")
    public R postReward(@RequestParam long campaignId) {
        return R.ok().put("data", service.postRewardCampaign(campaignId));
    }

    @ResponseBody
    @PostMapping("/rewards/clawback")
    @RequiresPermissions("novel:gamification:adjust")
    @Log("Thu hồi thưởng Ngọn Đuốc")
    public R clawbackReward(@RequestParam long allocationId, @RequestParam String reason) {
        return R.ok().put("data", service.clawbackReward(allocationId, reason, getUserId()));
    }
}
