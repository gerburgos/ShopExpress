package com.shopexpress.users.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.shopexpress.users.model.User;

public interface UserRepository extends MongoRepository<User, String> {
}
