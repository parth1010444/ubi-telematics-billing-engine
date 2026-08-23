package com.example.ubi.domain.model;

public enum BillingUsageStatus {
    PENDING,
    REPORTED,
    FAILED,
    /** Permanent failure (e.g. unknown Stripe customer) — do not retry. */
    ABANDONED
}
