package varahas.main.dto;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class MlUserItemsResponse {
    @JsonProperty("seller_id")
    private String sellerId;
    
    private List<String> results;
    
    private Map<String, Object> paging;
    
    private String query;
    
    private List<Map<String, Object>> orders;
    
    @JsonProperty("available_orders")
    private List<Map<String, Object>> availableOrders;

}