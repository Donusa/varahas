package varahas.main.entities;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Data;

@Entity
@Data
public class AttributeEntity {

    @Id
    private String id;

    @JsonProperty("value_name")
    private String valueName;
    @ManyToOne
    @JoinColumn(name = "meli_item_id",nullable = false)
    private MeliItem meliItem;
    
}