package varahas.main.services;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import varahas.main.entities.User;
import varahas.main.enums.Status;
import varahas.main.repositories.UserRepository;

@Service
public class UserService {

	@Autowired
	private UserRepository userRepository;
	
	public List<User> getAllUsers() {
		return userRepository.findAll();
	}	
	
	public User getUserById(Long id) {
		return userRepository.findById(id).orElse(null);
	}
	
	public User getUserByEmail(String email) {
		return userRepository.findByEmail(email).orElseThrow(
				()->new RuntimeException("User not found"));
	}
	
	public User getUserByUsername(String username) {
		return userRepository.findByUsername(username);
	}
	
	public User getUserByPhone(String phone) {
		return userRepository.findByPhone(phone);
	}
	
	public User saveUser(User user) {
		return userRepository.save(user);
	}
	
	public User disableEnableUser(Long id) {
		User user = userRepository.findById(id).orElse(null);
		if(user==null) {
			throw new RuntimeException("User not found");
		}
		user.setStatus(user.getStatus().equals(Status.ACTIVE) ? Status.INACTIVE : Status.INACTIVE);
		return userRepository.save(user);
	}
}
