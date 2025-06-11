package varahas.main.entities;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import lombok.Data;

@Embeddable
@Data
public class ShippingEntity {

	private String mode;
	@ElementCollection
	private List<String> tags;
	@JsonProperty("free_shipping")
	private Boolean freeShipping;
	@JsonProperty("logistic_type")
	private String logisticType;
	@JsonProperty("store_pick_up")
	private Boolean storePickUp;
}
