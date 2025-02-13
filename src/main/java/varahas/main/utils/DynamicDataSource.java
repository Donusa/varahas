package varahas.main.utils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.stereotype.Component;

import com.zaxxer.hikari.HikariDataSource;

import jakarta.annotation.PostConstruct;
import varahas.main.entities.Tenant;
import varahas.main.repositories.TenantRespository;

@Component
public class DynamicDataSource extends AbstractRoutingDataSource{

	@Autowired
	private TenantRespository tenantRespository;
	
	@Value("${DB_POOL_SIZE}")
	private int MaxPoolSize;
	
	private final Map<Object,Object> datasource = new ConcurrentHashMap<>();
	
	@PostConstruct
	public void loadInitialDataSources() {
		List<Tenant> tenant = tenantRespository.findAll();
		for (Tenant t : tenant) {
			addDataSource(t);
		}
	}
	
	private void addDataSource(Tenant tenant) {
		HikariDataSource dataSource = new HikariDataSource();
		String url = "jdbc:mysql://"+tenant.getDbHost()+":"+tenant.getDbPort()+"/"+tenant.getDbName();
		dataSource.setJdbcUrl(url);
		dataSource.setUsername(tenant.getDbUsername());
		dataSource.setPassword(tenant.getDbPassword());
		dataSource.setMaximumPoolSize(MaxPoolSize);
		
		datasource.put(tenant.getTenantId(), dataSource);
		this.afterPropertiesSet();
	}
	
	@Override
	protected Object determineCurrentLookupKey() {
		return TenantContext.getTenant();
	}
}
