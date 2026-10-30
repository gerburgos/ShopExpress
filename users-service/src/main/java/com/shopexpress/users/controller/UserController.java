package com.shopexpress.users.controller;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.shopexpress.users.dto.AddressDto;
import com.shopexpress.users.dto.CreateUserRequest;
import com.shopexpress.users.dto.UserResponse;
import com.shopexpress.users.model.User;
import com.shopexpress.users.service.UserService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/users")
public class UserController {

	private final UserService service;

	public UserController(UserService service) {
		this.service = service;
	}

	@PostMapping
	public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
		User user = service.create(request);
		URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{userId}").buildAndExpand(user.id())
				.toUri();
		return ResponseEntity.created(location).body(UserResponse.from(user));
	}

	@GetMapping("/{userId}")
	public UserResponse get(@PathVariable String userId) {
		return UserResponse.from(service.get(userId));
	}

	@PostMapping("/{userId}/addresses")
	public ResponseEntity<UserResponse> addAddress(@PathVariable String userId,
			@Valid @RequestBody AddressDto address) {
		return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(service.addAddress(userId, address)));
	}

}
