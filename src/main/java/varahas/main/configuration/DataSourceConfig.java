package varahas.main.configuration;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;

import jakarta.persistence.EntityManagerFactory;
import varahas.main.utils.DynamicDataSource;

@Configuration
public class DataSourceConfig {

	@Autowired
	private DynamicDataSource dynamicDataSource;
	
	@Bean
	@Primary
	DataSource dataSource() {
		return dynamicDataSource;
	}
	
	@Bean
	LocalContainerEntityManagerFactoryBean entityManagerFactory(EntityManagerFactoryBuilder builder) {
		return builder
				.dataSource(dynamicDataSource)
				.packages("varahas.main.entities")
				.persistenceUnit("default")
				.build();
	}
	
	@Bean
	PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
		return new JpaTransactionManager(entityManagerFactory);
	}
}
