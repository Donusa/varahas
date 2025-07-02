package varahas.main.dto;

import java.time.ZonedDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class MeliItemDto {
	
	private String id;
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
    private ZonedDateTime startTime;
    @JsonProperty("stop_time")
    private ZonedDateTime stopTime;
    private String condition;
    private String permalink;
    private String thumbnail;
    private Shipping shipping;
    private String status;
    @JsonProperty("sub_status")
    private List<String> subStatus;
    private List<String> tags;
    private String warranty;
    @JsonProperty("date_created")
    private ZonedDateTime dateCreated;
    @JsonProperty("last_updated")
    private ZonedDateTime lastUpdated;


    private List<MeliVariationDto> variations;
}
