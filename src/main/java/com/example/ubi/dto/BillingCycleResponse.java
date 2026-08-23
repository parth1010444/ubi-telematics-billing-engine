package com.example.ubi.dto;

public record BillingCycleResponse(
        String invoiceId,
        String status,
        String hostedInvoiceUrl,
        String invoicePdf,
        long amountCents,
        long accruedAmountCents,
        long paidAmountCents,
        long unpaidAmountCents
) {
}
