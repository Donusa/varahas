package varahas.main.entities;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MapKeyColumn;
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
public class TnProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonIgnore
    private Long id;

    @ElementCollection
    @CollectionTable(name = "product_name")
    @MapKeyColumn(name = "language")
    @Column(name = "value")
    private Map<String, String> name;

    @ElementCollection
    @CollectionTable(name = "product_description")
    @MapKeyColumn(name = "language")
    @Column(name = "value")
    private Map<String, String> description;

    @ElementCollection
    @CollectionTable(name = "product_handle")
    @MapKeyColumn(name = "language")
    @Column(name = "value")
    private Map<String, String> handle;

    @ElementCollection
    @CollectionTable(name = "product_seo_title")
    @MapKeyColumn(name = "language")
    @Column(name = "value")
    private Map<String, String> seo_title;

    @ElementCollection
    @CollectionTable(name = "product_seo_description")
    @MapKeyColumn(name = "language")
    @Column(name = "value")
    private Map<String, String> seo_description;

    @ElementCollection
    private List<String> attributes;

    private Boolean published;
    private Boolean free_shipping;
    private Boolean requires_shipping;

    private String canonical_url;
    private String video_url;
    private String brand;
    private String tags;

    @ElementCollection
    private List<String> categories;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Variant> variants;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Image> images;

    @Override
    public String toString() {
        return "";
    }
}
