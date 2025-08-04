package varahas.main.jobs;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import varahas.main.entities.Tenant;
import varahas.main.repositories.TenantRepository;
import varahas.main.services.TokenSchedulerService;

@Component
public class StartupTokenJobLoader {

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private TokenSchedulerService schedulerService;

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        List<Tenant> tenants = tenantRepository.findAll();
        for (Tenant tenant : tenants) {
            if (tenant.getMlAccessTokenExpirationDate() != null) {
                schedulerService.programarRenovacionTokenMl(tenant);
            }
        }
    }
}
