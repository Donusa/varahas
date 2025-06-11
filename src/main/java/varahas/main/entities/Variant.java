package varahas.main.entities;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class Variant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonIgnore
    private Long id;

    private String price;
    private String compareAtPrice;
    private Boolean stockManagement;
    private Integer stock;
    private Boolean visible;
    private Integer position;

    private String sku;
    private String barcode;
    private String mpn;
    private String ageGroup;
    private String gender;

    private String weight;
    private String width;
    private String height;
    private String depth;

    @ElementCollection
    @CollectionTable(name = "variant_values", joinColumns = @JoinColumn(name = "variant_id"))
    @Column(name = "value")
    @JsonProperty("values")
    private List<String> variantValues;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "variant_id")
    private List<InventoryLevel> inventoryLevels;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "variant_id")
    private List<Attributes> attributes;

    @ManyToOne
    @JoinColumn(name = "product_id")
    @JsonIgnore
    private TnProduct product;

    @Override
    public String toString() {
        return "";
    }
}
