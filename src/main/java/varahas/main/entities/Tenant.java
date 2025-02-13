package varahas.main.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Tenant {

	@Id
	private String tenantId;
    private String dbHost;
    private int dbPort;
    private String dbName;
    private String dbUsername;
    private String dbPassword;
	
}
