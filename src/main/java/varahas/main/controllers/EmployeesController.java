package varahas.main.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import varahas.main.entities.Tenant;
import varahas.main.entities.User;
import varahas.main.services.UserService;

@RestController
@RequestMapping("/employees")
public class EmployeesController {

	@Autowired
	private UserService userService;
	
	@GetMapping("/all")
	public ResponseEntity<?> getAllEmployees() {
		return ResponseEntity.ok(userService.getAllUsers());
	}
	
	@DeleteMapping("/delete")
	public ResponseEntity<?> deleteEmployee(@RequestParam Long id) {
		userService.deleteUser(id);
		return ResponseEntity.ok("Employee deleted successfully");
	}
	
	@PutMapping("/update")
	public ResponseEntity<?> updateEmployee(@RequestBody User user){
		User u = userService.getUserById(user.getId());
		String password = u.getPassword();
		Tenant tenant = u.getTenant();
		user.setPassword(password);
		user.setTenant(tenant);
		u = user;
		userService.saveUser(u);
		return ResponseEntity.ok("Employee updated successfully");
	}
}
