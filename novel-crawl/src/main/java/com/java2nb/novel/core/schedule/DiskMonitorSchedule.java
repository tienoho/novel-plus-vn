package com.java2nb.novel.core.schedule;

import com.java2nb.novel.core.bean.DiskInfo;
import com.java2nb.novel.core.config.DiskMonitorProperties;
import com.java2nb.novel.core.i18n.Messages;
import com.java2nb.novel.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author xiongxiaoyang
 * @date 2025/10/24
 */
@ConditionalOnProperty(prefix = "disk-monitor", name = "enabled", havingValue = "true")
@Service
@RequiredArgsConstructor
@Slf4j
public class DiskMonitorSchedule {

    private final DiskMonitorProperties properties;

    private final EmailService emailService;

    private final Messages messages;

    private final AtomicBoolean criticalAlertSent = new AtomicBoolean(false);

    @Scheduled(fixedDelayString = "#{1000 * 60 * ${disk-monitor.interval-minutes}}")
    public void checkDiskUsage() {
        log.info("🔍 {}", messages.get("crawl.log.diskCheck"));

        File[] roots = File.listRoots();
        List<DiskInfo> diskInfos = new ArrayList<>();
        boolean criticalDetected = false;

        for (File root : roots) {
            String path = root.getAbsolutePath().trim();
            if (path.isEmpty()) continue;
            long total = root.getTotalSpace();
            if (total == 0) continue;
            long free = root.getFreeSpace();
            double usage = (double)(total - free) / total * 100;
            diskInfos.add(new DiskInfo(path, total, free, usage));
            if (usage >= properties.getCriticalThreshold()) {
                criticalDetected = true;
            }
        }

        if (criticalDetected) {
            if (criticalAlertSent.compareAndSet(false, true)) {
                sendAlertEmail("CRITICAL", diskInfos);
                log.error("🚨 {}", messages.get("crawl.log.diskCritical"));
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                System.exit(1);
            }
        } else {
            criticalAlertSent.set(false);
            double maxUsage = diskInfos.stream().mapToDouble(DiskInfo::getUsage).max().orElse(0);
            if (maxUsage >= properties.getSevereThreshold() && maxUsage < properties.getCriticalThreshold()) {
                sendAlertEmail("WARNING", diskInfos);
            } else if (maxUsage >= properties.getWarningThreshold() && maxUsage < properties.getSevereThreshold()) {
                sendAlertEmail("INFO", diskInfos);
            }
        }
    }

    private void sendAlertEmail(String level, List<DiskInfo> diskInfos) {
        Map<String, Object> model = new HashMap<>();
        model.put("diskInfos", diskInfos);
        model.put("alertLevel", getAlertLevelText(level));
        model.put("icon", getIcon(level));
        model.put("actionText", getActionText(level));
        model.put("levelColor", getLevelColor(level));

        String subject = getSubject(level);
        emailService.sendHtmlEmail(subject, "disk_alert", model, properties.getRecipients());
    }

    private String getAlertLevelText(String level) {
        return switch (level) {
            case "CRITICAL" -> messages.get("crawl.disk.level.critical");
            case "WARNING" -> messages.get("crawl.disk.level.warning");
            case "INFO" -> messages.get("crawl.disk.level.info");
            default -> messages.get("crawl.disk.level.notice");
        };
    }

    private String getIcon(String level) {
        return switch (level) {
            case "CRITICAL" -> "🚨";
            case "WARNING" -> "⚠️";
            case "INFO" -> "💡";
            default -> "📢";
        };
    }

    private String getActionText(String level) {
        return switch (level) {
            case "CRITICAL" -> messages.get("crawl.disk.action.critical");
            case "WARNING" -> messages.get("crawl.disk.action.warning");
            case "INFO" -> messages.get("crawl.disk.action.info");
            default -> "";
        };
    }

    private String getLevelColor(String level) {
        return switch (level) {
            case "CRITICAL" -> "#d32f2f";
            case "WARNING" -> "#f57c00";
            case "INFO" -> "#388e3c";
            default -> "#1976d2";
        };
    }

    private String getSubject(String level) {
        return switch (level) {
            case "CRITICAL" -> messages.get("crawl.disk.subject.critical");
            case "WARNING" -> messages.get("crawl.disk.subject.warning");
            case "INFO" -> messages.get("crawl.disk.subject.info");
            default -> messages.get("crawl.disk.subject.default");
        };
    }

}
