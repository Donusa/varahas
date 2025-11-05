package varahas.main.services;

import java.time.Duration;
import java.util.Date;

import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import varahas.main.entities.Tenant;
import varahas.main.jobs.RenewTokenJob;

@Service
public class TokenSchedulerService {

    @Autowired
    private Scheduler scheduler;

    public void programarRenovacionTokenMl(Tenant tenant) {
        if (tenant.getMlAccessTokenExpirationDate() == null) return;

        JobDetail jobDetail = JobBuilder.newJob(RenewTokenJob.class)
            .withIdentity("job-ml-token-" + tenant.getId())
            .usingJobData("tenantId", tenant.getId())
            .storeDurably()
            .build();

        Date fechaEjecucion = Date.from(
            tenant.getMlAccessTokenExpirationDate().toInstant().minus(Duration.ofMinutes(5))
        );

        Trigger trigger = TriggerBuilder.newTrigger()
            .forJob(jobDetail)
            .withIdentity("trigger-ml-token-" + tenant.getId())
            .startAt(fechaEjecucion)
            .build();

        try {
            scheduler.scheduleJob(jobDetail, trigger);
        } catch (SchedulerException e) {
            throw new RuntimeException("Error programando token ML", e);
        }
    }
}