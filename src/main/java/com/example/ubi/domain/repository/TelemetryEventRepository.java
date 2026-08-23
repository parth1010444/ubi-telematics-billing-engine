package com.example.ubi.domain.repository;

import com.example.ubi.domain.model.TelemetryEvent;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TelemetryEventRepository extends MongoRepository<TelemetryEvent, String> {

    long deleteByPolicyId(String policyId);

    List<TelemetryEvent> findByPolicyIdOrderByTimestampDesc(String policyId);
}
