package com.example.ubi.dto;

public class TriggerBillingRequest {

    /**
     * Dashboard origin Stripe should return to after Checkout, e.g.
     * {@code https://abc.ngrok-free.app} or {@code http://192.168.1.12:5173}.
     * Path and query are ignored; the API appends {@code /?billing=...}.
     */
    private String returnOrigin;

    public String getReturnOrigin() {
        return returnOrigin;
    }

    public void setReturnOrigin(String returnOrigin) {
        this.returnOrigin = returnOrigin;
    }
}
