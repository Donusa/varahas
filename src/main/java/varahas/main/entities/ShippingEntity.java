package varahas.main.entities;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import lombok.Data;

@Entity
@Data
public class ShippingEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private String id;
	private String mode;
	@OneToMany(mappedBy = "shipping", cascade = CascadeType.ALL,orphanRemoval = true)
	private List<Tag> tags;
	@JsonProperty("free_shipping")
	private Boolean freeShipping;
	@JsonProperty("logistic_type")
	private String logisticType;
	@JsonProperty("store_pick_up")
	private Boolean storePickUp;
}
