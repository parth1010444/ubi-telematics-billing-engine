package com.example.ubi.domain.repository;

import com.example.ubi.domain.model.TelemetryEvent;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TelemetryEventRepository extends MongoRepository<TelemetryEvent, String> {
}
