package com.example.ubi.domain.repository;

import com.example.ubi.domain.model.BillingUsageRecord;
import com.example.ubi.domain.model.BillingUsageStatus;
import java.time.Instant;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface BillingUsageRecordRepository extends MongoRepository<BillingUsageRecord, String> {

    List<BillingUsageRecord> findTop50ByStatusInAndNextRetryAtLessThanEqualOrderByCreatedAtAsc(
            List<BillingUsageStatus> statuses,
            Instant nextRetryAt
    );
}
