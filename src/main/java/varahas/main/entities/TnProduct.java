package varahas.main.entities;

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
	private Integer price;
	private Integer quantity;
	@OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
	private Variant[] variants;
	@OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
	private Image[] images;

	@Override
	public String toString() {
		return "";
	}
}
