package com.java2nb.novel.core.schedule;

import com.java2nb.novel.service.gamification.config.GamificationConfigProvider;
import com.java2nb.novel.service.gamification.config.GamificationConfigSnapshot;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;

import java.time.Duration;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GamificationDynamicSchedulerTest {

    @Test
    void revisionChangeCancelsOldFutureRunsAndRegistersFiveNewJobs() {
        AtomicReference<GamificationConfigSnapshot> current = new AtomicReference<>(
            GamificationConfigSnapshot.bootstrapDisabled());
        GamificationConfigProvider provider = mock(GamificationConfigProvider.class);
        when(provider.current()).thenAnswer(ignored -> current.get());
        TaskScheduler scheduler = mock(TaskScheduler.class);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        doReturn(future).when(scheduler).schedule(any(Runnable.class), any(Trigger.class));
        doReturn(future).when(scheduler)
            .scheduleWithFixedDelay(any(Runnable.class), any(Duration.class));
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        GamificationDynamicScheduler dynamic = new GamificationDynamicScheduler(scheduler, provider,
            mock(GamificationEventDrainSchedule.class), mock(MonthlyTicketExpirySchedule.class),
            mock(MonthlyTicketSeasonSchedule.class), mock(AuthorRewardReleaseSchedule.class),
            registry);

        dynamic.rescheduleIfChanged();
        current.set(current.get().toBuilder().runtimeRevision(2L).build());
        dynamic.rescheduleIfChanged();

        verify(scheduler, times(6)).schedule(any(Runnable.class), any(Trigger.class));
        verify(scheduler, times(4)).scheduleWithFixedDelay(any(Runnable.class), any(Duration.class));
        verify(future, atLeastOnce()).cancel(false);
        assertThat(registry.get("gamification.scheduler.revision").gauge().value()).isEqualTo(2);
    }

    @Test
    void failedRescheduleIsObservableWithoutStoppingTheWatcher() {
        GamificationConfigProvider provider = mock(GamificationConfigProvider.class);
        when(provider.current()).thenThrow(new IllegalStateException("database unavailable"));
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        GamificationDynamicScheduler dynamic = new GamificationDynamicScheduler(
            mock(TaskScheduler.class), provider, mock(GamificationEventDrainSchedule.class),
            mock(MonthlyTicketExpirySchedule.class), mock(MonthlyTicketSeasonSchedule.class),
            mock(AuthorRewardReleaseSchedule.class), registry);

        dynamic.watchRevision();

        assertThat(registry.get("gamification.scheduler.reschedule.failures")
            .counter().count()).isEqualTo(1);
    }
}
