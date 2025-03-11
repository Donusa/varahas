package varahas.main.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import varahas.main.services.UserService;

@RestController
@RequestMapping("/employees")
public class EmployeesController {

	@Autowired
	private UserService userService;
	
	@GetMapping("/all")
	public ResponseEntity<?> getAllEmployees() {
		return ResponseEntity.ok(userService.getAllUsers().toString());
	}
	
	@DeleteMapping("/delete")
	public ResponseEntity<?> deleteEmployee(Long id) {
		userService.deleteUser(id);
		return ResponseEntity.ok("Employee deleted successfully");
	}
	
}
