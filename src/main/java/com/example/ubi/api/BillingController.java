package com.example.ubi.api;

import com.example.ubi.application.BillingCycleService;
import com.example.ubi.dto.BillingCycleResponse;
import com.example.ubi.dto.ConfirmCheckoutRequest;
import com.example.ubi.dto.PolicyResponse;
import com.example.ubi.dto.TriggerBillingRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/billing")
public class BillingController {

    private final BillingCycleService billingCycleService;

    public BillingController(BillingCycleService billingCycleService) {
        this.billingCycleService = billingCycleService;
    }

    @PostMapping("/trigger-cycle/{policyId}")
    public BillingCycleResponse triggerCycle(
            @PathVariable String policyId,
            @RequestParam(name = "force", defaultValue = "false") boolean force,
            @RequestBody(required = false) TriggerBillingRequest request,
            @RequestHeader(value = HttpHeaders.ORIGIN, required = false) String originHeader
    ) {
        String returnOrigin = request != null && request.getReturnOrigin() != null
                && !request.getReturnOrigin().isBlank()
                ? request.getReturnOrigin().trim()
                : originHeader;
        return billingCycleService.triggerCycle(policyId, force, returnOrigin);
    }

    @PostMapping("/confirm-checkout")
    public PolicyResponse confirmCheckout(@Valid @RequestBody ConfirmCheckoutRequest request) {
        return billingCycleService.confirmCheckout(request.getPolicyId(), request.getSessionId());
    }
}
