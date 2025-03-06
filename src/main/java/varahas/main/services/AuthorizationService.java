package varahas.main.services;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import varahas.main.entities.User;

@Service
public class AuthorizationService {

	public boolean isUserAuthorized(String tennantName) {
		Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
		if(principal instanceof User) {
			String userTennantName = ((User) principal).getTenant().getName();
			return userTennantName.equals(tennantName);
		}
		return false;
	}
}
