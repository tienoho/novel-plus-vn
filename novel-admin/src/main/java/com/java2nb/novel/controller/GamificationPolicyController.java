package com.java2nb.novel.controller;

import com.java2nb.common.annotation.Log;
import com.java2nb.common.controller.BaseController;
import com.java2nb.common.utils.R;
import com.java2nb.novel.service.gamification.LevelRewardPolicyRow;
import com.java2nb.novel.service.gamification.LevelRuleRow;
import com.java2nb.novel.service.gamification.QuestDefinitionRow;
import com.java2nb.novel.service.gamification.RealmCatalogRow;
import com.java2nb.novel.service.gamification.TicketRiskRuleRow;
import com.java2nb.novel.service.gamification.config.GamificationPolicyLifecycleService;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/novel/gamification/policies")
public class GamificationPolicyController extends BaseController {
    private final GamificationPolicyLifecycleService lifecycle;

    public GamificationPolicyController(GamificationPolicyLifecycleService lifecycle) {
        this.lifecycle = lifecycle;
    }

    @GetMapping
    @RequiresPermissions("novel:gamification:policy:view")
    public R list(@RequestParam(defaultValue = "100") int limit) {
        return R.ok().put("data", lifecycle.list(limit));
    }

    @GetMapping("/{version}")
    @RequiresPermissions("novel:gamification:policy:view")
    public R detail(@PathVariable String version) {
        return R.ok().put("data", lifecycle.get(version));
    }

    @PostMapping("/drafts")
    @RequiresPermissions("novel:gamification:policy:edit")
    @Log("Tạo draft policy gamification")
    public R createDraft(@RequestBody CreateDraftRequest request) {
        return R.ok().put("data", lifecycle.createDraft(request.policyVersion(),
            request.sourceVersion(), getUserId(), request.reason()));
    }

    @PostMapping("/{version}/levels")
    @RequiresPermissions("novel:gamification:policy:edit")
    @Log("Lưu quy tắc level gamification")
    public R saveLevel(@PathVariable String version, @RequestBody LevelRequest request) {
        return R.ok().put("data", lifecycle.saveLevel(version, request.expectedVersion(),
            request.level(), getUserId(), request.reason()));
    }

    @PostMapping("/{version}/level-rewards")
    @RequiresPermissions("novel:gamification:policy:edit")
    @Log("Lưu thưởng level gamification")
    public R saveLevelReward(@PathVariable String version,
                             @RequestBody LevelRewardRequest request) {
        return R.ok().put("data", lifecycle.saveLevelReward(version, request.expectedVersion(),
            request.reward(), getUserId(), request.reason()));
    }

    @PostMapping("/{version}/quests")
    @RequiresPermissions("novel:gamification:policy:edit")
    @Log("Lưu nhiệm vụ gamification")
    public R saveQuest(@PathVariable String version, @RequestBody QuestRequest request) {
        return R.ok().put("data", lifecycle.saveQuest(version, request.expectedVersion(),
            request.quest(), request.expReward(), request.ticketReward(), getUserId(),
            request.reason()));
    }

    @PostMapping("/{version}/realms")
    @RequiresPermissions("novel:gamification:policy:edit")
    @Log("Lưu cảnh giới gamification")
    public R saveRealm(@PathVariable String version, @RequestBody RealmRequest request) {
        return R.ok().put("data", lifecycle.saveRealm(version, request.expectedVersion(),
            request.realm(), getUserId(), request.reason()));
    }

    @PostMapping("/{version}/abuse-rules")
    @RequiresPermissions("novel:gamification:policy:edit")
    @Log("Lưu rule chống lạm dụng gamification")
    public R saveAbuseRule(@PathVariable String version, @RequestBody AbuseRuleRequest request) {
        return R.ok().put("data", lifecycle.saveAbuseRule(version, request.expectedVersion(),
            request.reviewScoreThreshold(), request.rule(), getUserId(), request.reason()));
    }

    @PostMapping("/{version}/public-policy")
    @RequiresPermissions("novel:gamification:policy:edit")
    @Log("Lưu luật chơi công khai gamification")
    public R savePublicPolicy(@PathVariable String version,
                              @RequestBody PublicPolicyRequest request) {
        return R.ok().put("data", lifecycle.savePublicPolicy(version, request.expectedVersion(),
            request.title(), request.contentText(), getUserId(), request.reason()));
    }

    @PostMapping("/{version}/submit")
    @RequiresPermissions("novel:gamification:policy:edit")
    @Log("Gửi duyệt policy gamification")
    public R submit(@PathVariable String version, @RequestBody ActionRequest request) {
        return R.ok().put("data", lifecycle.submit(version, request.expectedVersion(),
            getUserId(), request.reason()));
    }

    @PostMapping("/{version}/approve")
    @RequiresPermissions("novel:gamification:policy:approve")
    @Log("Phê duyệt policy gamification")
    public R approve(@PathVariable String version, @RequestBody ActionRequest request) {
        return R.ok().put("data", lifecycle.approve(version, request.expectedVersion(),
            getUserId(), request.reason()));
    }

    @PostMapping("/{version}/publish")
    @RequiresPermissions("novel:gamification:policy:publish")
    @Log("Phát hành policy gamification")
    public R publish(@PathVariable String version, @RequestBody ActionRequest request) {
        return R.ok().put("data", lifecycle.publish(version, request.expectedVersion(),
            getUserId(), request.reason()));
    }

    public record CreateDraftRequest(String policyVersion, String sourceVersion, String reason) {
    }
    public record ActionRequest(long expectedVersion, String reason) {
    }
    public record LevelRequest(long expectedVersion, String reason, LevelRuleRow level) {
    }
    public record LevelRewardRequest(long expectedVersion, String reason,
                                     LevelRewardPolicyRow reward) {
    }
    public record QuestRequest(long expectedVersion, String reason, QuestDefinitionRow quest,
                               long expReward, long ticketReward) {
    }
    public record RealmRequest(long expectedVersion, String reason, RealmCatalogRow realm) {
    }
    public record AbuseRuleRequest(long expectedVersion, String reason, int reviewScoreThreshold,
                                   TicketRiskRuleRow rule) {
    }
    public record PublicPolicyRequest(long expectedVersion, String reason, String title,
                                      String contentText) {
    }
}
