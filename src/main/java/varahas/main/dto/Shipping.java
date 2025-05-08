package varahas.main.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class Shipping {

	private String mode;
    private List<String> tags;
    @JsonProperty("free_shipping")
    private Boolean freeShipping;
    @JsonProperty("logistic_type")
    private String logisticType;
    @JsonProperty("store_pick_up")
    private Boolean storePickUp;
}