package varahas.main.entities;

import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Tenant {

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private Long id;
	@Column(unique = true, nullable = false)
	private String name;
	@Column(updatable = false, nullable = false)
	@Temporal(TemporalType.TIMESTAMP)
	private Date createdAt;
	private String cuil;
	@Lob
	@Column(columnDefinition = "TEXT")
	private String arcaToken;
	@Lob
	@Column(columnDefinition = "TEXT")
	private String arcaSign;
	@OneToMany(mappedBy = "tenant", cascade = CascadeType.ALL, orphanRemoval = true)
	@JsonIgnore
    private List<User> users;
	@Column(unique = true, nullable = true)
	private String mlUserId;
	@Column(unique = true, nullable = true)
	private String TiendaNubeAccessToken;
	@Column(nullable = true)
	private String mlAccessToken;
	@Column(nullable = true)
	private Date mlAccessTokenExpirationDate;
	@Column(nullable = true)
	private String mlRefreshToken;
	@Column(nullable = true)
	private String tnUserId;
	@Override
	public String toString() {
		return "";
	}
}
