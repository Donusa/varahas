package varahas.main.jobs;

import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;

import varahas.main.output.MercadoLibreApiOutput;

public class RenewTokenJob implements Job{
	
	@Autowired
    private MercadoLibreApiOutput mercadoLibreApiOutput;

    @Override
    public void execute(JobExecutionContext context) {
        JobDataMap data = context.getMergedJobDataMap();
        Long tenantId = data.getLong("tenantId");
        mercadoLibreApiOutput.renewMlToken(tenantId);
    }
}
