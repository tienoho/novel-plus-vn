package com.java2nb.novel.controller;

import com.java2nb.common.annotation.Log;
import com.java2nb.common.controller.BaseController;
import com.java2nb.common.utils.R;
import com.java2nb.novel.service.gamification.config.GamificationBootstrapProperties;
import com.java2nb.novel.service.gamification.config.GamificationConfigHasher;
import com.java2nb.novel.service.gamification.config.GamificationConfigSnapshot;
import com.java2nb.novel.service.gamification.config.GamificationConfigValidator;
import com.java2nb.novel.service.gamification.config.GamificationEnvironmentProperties;
import com.java2nb.novel.service.gamification.config.GamificationRuntimeConfigLifecycleService;
import com.java2nb.novel.service.gamification.config.GamificationRuntimeConfigRow;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.scheduling.support.CronExpression;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/novel/gamification/settings")
public class GamificationRuntimeSettingsController extends BaseController {

    private final GamificationRuntimeConfigLifecycleService lifecycle;
    private final GamificationEnvironmentProperties environment;
    private final GamificationBootstrapProperties bootstrap;
    private final GamificationConfigHasher hasher;
    private final GamificationConfigValidator validator;

    public GamificationRuntimeSettingsController(
        GamificationRuntimeConfigLifecycleService lifecycle,
        GamificationEnvironmentProperties environment,
        GamificationBootstrapProperties bootstrap,
        GamificationConfigHasher hasher,
        GamificationConfigValidator validator) {
        this.lifecycle = lifecycle;
        this.environment = environment;
        this.bootstrap = bootstrap;
        this.hasher = hasher;
        this.validator = validator;
    }

    @GetMapping("/active")
    @RequiresPermissions("novel:gamification:settings:view")
    public R active() {
        GamificationRuntimeConfigRow active = lifecycle.getActive();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("config", active);
        metadata.put("hashSecretReady",
            bootstrap.isReady(active.getVoteIpHashKeyId()));
        metadata.put("configuredKeyId", bootstrap.getVoteIpHashKeyId());
        metadata.put("requiredKeyId", active.getVoteIpHashKeyId());
        metadata.put("source", bootstrap.resolveSource().name());
        metadata.put("constraints", validator.constraints());
        return R.ok().put("data", metadata);
    }

    @GetMapping("/history")
    @RequiresPermissions("novel:gamification:settings:view")
    public R history(@RequestParam(defaultValue = "100") int limit) {
        return R.ok().put("data", lifecycle.history(limit));
    }

    @GetMapping("/{id}")
    @RequiresPermissions("novel:gamification:settings:view")
    public R detail(@PathVariable long id) {
        return R.ok().put("data", lifecycle.get(id));
    }

    @GetMapping("/{id}/diff")
    @RequiresPermissions("novel:gamification:settings:view")
    public R diff(@PathVariable long id) {
        return R.ok().put("data", lifecycle.diff(id));
    }

    @GetMapping("/cron-preview")
    @RequiresPermissions("novel:gamification:settings:view")
    public R previewCron(@RequestParam String expression, @RequestParam String zoneId) {
        CronExpression cron = CronExpression.parse(expression);
        ZonedDateTime next = ZonedDateTime.now(ZoneId.of(zoneId));
        ArrayList<String> occurrences = new ArrayList<>(5);
        for (int index = 0; index < 5; index++) {
            next = cron.next(next);
            if (next == null) {
                break;
            }
            occurrences.add(next.toString());
        }
        return R.ok().put("data", occurrences);
    }

    @PostMapping("/drafts/clone-active")
    @RequiresPermissions("novel:gamification:settings:edit")
    @Log("Tạo draft gamification từ revision đang chạy")
    public R cloneActive(@RequestBody ReasonRequest request) {
        return R.ok().put("data", lifecycle.cloneActive(getUserId(), request.reason()));
    }

    @PostMapping("/drafts/import-env")
    @RequiresPermissions("novel:gamification:settings:edit")
    @Log("Import cấu hình gamification từ ENV")
    public R importEnvironment(@RequestBody ReasonRequest request) {
        GamificationConfigSnapshot snapshot = environment.toSnapshot(bootstrap.getVoteIpHashKeyId());
        return R.ok().put("data", lifecycle.importSnapshot(snapshot, hasher.hash(snapshot),
            getUserId(), request.reason()));
    }

    @PostMapping("/{id}/save")
    @RequiresPermissions("novel:gamification:settings:edit")
    @Log("Lưu draft cấu hình gamification")
    public R save(@PathVariable long id, @RequestBody SaveRequest request) {
        return R.ok().put("data", lifecycle.saveDraft(id, request.expectedVersion(),
            request.snapshot(), getUserId(), request.reason()));
    }

    @PostMapping("/{id}/submit")
    @RequiresPermissions("novel:gamification:settings:edit")
    @Log("Gửi duyệt cấu hình gamification")
    public R submit(@PathVariable long id, @RequestBody ActionRequest request) {
        return R.ok().put("data", lifecycle.submit(id, request.expectedVersion(),
            getUserId(), request.reason()));
    }

    @PostMapping("/{id}/approve")
    @RequiresPermissions("novel:gamification:settings:approve")
    @Log("Phê duyệt cấu hình gamification")
    public R approve(@PathVariable long id, @RequestBody ActionRequest request) {
        return R.ok().put("data", lifecycle.approve(id, request.expectedVersion(),
            getUserId(), request.reason()));
    }

    @PostMapping("/{id}/reject")
    @RequiresPermissions("novel:gamification:settings:approve")
    @Log("Từ chối cấu hình gamification")
    public R reject(@PathVariable long id, @RequestBody ActionRequest request) {
        return R.ok().put("data", lifecycle.reject(id, request.expectedVersion(),
            getUserId(), request.reason()));
    }

    @PostMapping("/{id}/schedule")
    @RequiresPermissions("novel:gamification:settings:activate")
    @Log("Hẹn giờ kích hoạt cấu hình gamification")
    public R schedule(@PathVariable long id, @RequestBody ScheduleRequest request) {
        return R.ok().put("data", lifecycle.schedule(id, request.expectedVersion(),
            getUserId(), request.reason(), new Date(request.effectiveAtMillis())));
    }

    @PostMapping("/{id}/cancel")
    @RequiresPermissions("novel:gamification:settings:activate")
    @Log("Hủy lịch kích hoạt cấu hình gamification")
    public R cancel(@PathVariable long id, @RequestBody ActionRequest request) {
        return R.ok().put("data", lifecycle.cancel(id, request.expectedVersion(),
            getUserId(), request.reason()));
    }

    @PostMapping("/{id}/rollback")
    @RequiresPermissions("novel:gamification:settings:activate")
    @Log("Tạo draft rollback cấu hình gamification")
    public R rollback(@PathVariable long id, @RequestBody ReasonRequest request) {
        return R.ok().put("data", lifecycle.rollbackFrom(id, getUserId(), request.reason()));
    }

    public record ReasonRequest(String reason) {
    }

    public record ActionRequest(long expectedVersion, String reason) {
    }

    public record ScheduleRequest(long expectedVersion, String reason, long effectiveAtMillis) {
    }

    public record SaveRequest(long expectedVersion, String reason,
                              GamificationConfigSnapshot snapshot) {
    }
}
