package electricity.billing.system.client;

import electricity.billing.system.common.Api;

/** Online payments through SSLCommerz, for the signed-in customer. */
public class PaymentClient {

    private final ApiClient api = ApiClient.get();

    public Api.PaymentConfig config() {
        return api.get("/api/payments/config", Api.PaymentConfig.class);
    }

    /** Starts paying one of the customer's own bills; the amount is taken from the bill on the server. */
    public Api.PaymentStart start(String month, int year) {
        return api.post("/api/payments/start", new Api.PaymentStartRequest(month, year), Api.PaymentStart.class);
    }

    public Api.PaymentState status(String tranId) {
        return api.get(ApiClient.withQuery("/api/payments/status", ApiClient.params("tran", tranId)), Api.PaymentState.class);
    }
}
