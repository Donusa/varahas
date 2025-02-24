package varahas.main.utils;

public class SecurityUtils {

	 public static String passwordChallenge(String password) {
	        if (password == null || password.length() < 8) {
	            return "Password must be at least 8 characters long";
	        }
	        if (!password.matches(".*[A-Z].*")) {
	            return "Password must contain at least one uppercase letter";
	        }
	        if (!password.matches(".*[a-z].*")) {
	            return "Password must contain at least one lowercase letter";
	        }
	        if (!password.matches(".*\\d.*")) {
	            return "Password must contain at least one number";
	        }
	        return "Password is safe";
	    }
}
