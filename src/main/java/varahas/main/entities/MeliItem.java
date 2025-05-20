package varahas.main.entities;

import java.time.OffsetDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MeliItem {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
    @JsonProperty("site_id")
    private String siteId;
    private String title;
    @JsonProperty("seller_id")
    private Long sellerId;
    @JsonProperty("category_id")
    private String categoryId;
    private Double price;
    @JsonProperty("base_price")
    private Double basePrice;
    @JsonProperty("currency_id")
    private String currencyId;
    @JsonProperty("initial_quantity")
    private Integer initialQuantity;
    @JsonProperty("available_quantity")
    private Integer availableQuantity;
    @JsonProperty("sold_quantity")
    private Integer soldQuantity;
    @JsonProperty("buying_mode")
    private String buyingMode;
    @JsonProperty("listing_type_id")
    private String listingTypeId;
    @JsonProperty("start_time")
    private OffsetDateTime startTime;
    @JsonProperty("stop_time")
    private OffsetDateTime stopTime;
    private String condition;
    private String permalink;
    private String thumbnail;
    @Embedded
    private ShippingEntity shipping;
    private String status;
    @ElementCollection
    @JsonProperty("sub_status")
    private List<String> subStatus;
    @ElementCollection
    private List<String> tags;
    private String warranty;
    @JsonProperty("date_created")
    private OffsetDateTime dateCreated;
    @JsonProperty("last_updated")
    private OffsetDateTime lastUpdated;

}
