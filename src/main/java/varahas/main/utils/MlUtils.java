package varahas.main.utils;

import java.util.ArrayList;
import java.util.List;

import varahas.main.dto.VariationsDTO;
import varahas.main.entities.Product;
import varahas.main.entities.Variations;

public class MlUtils {

	public static List<VariationsDTO> getVariations(Product product){
		List<VariationsDTO> variations = new ArrayList<VariationsDTO>();
		
		for(Variations variation:product.getVariations()){
			if(variation.getMeliId()!= null){
			VariationsDTO variationDto = VariationsDTO.builder()
					.id(variation.getMeliId())
					.available_quantity(variation.getStock())
					.build();
			variations.add(variationDto);
			}
		}
		
		return variations;
	}  
}
