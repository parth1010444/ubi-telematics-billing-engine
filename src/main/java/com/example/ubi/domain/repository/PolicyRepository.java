package com.example.ubi.domain.repository;

import com.example.ubi.domain.model.Policy;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PolicyRepository extends MongoRepository<Policy, String> {
}
