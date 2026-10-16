package electricity.billing.system.client;

import com.google.gson.reflect.TypeToken;
import electricity.billing.system.common.Api;
import electricity.billing.system.model.Bill;
import electricity.billing.system.model.BillStatement;
import electricity.billing.system.model.TariffPlan;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Optional;

/**
 * Bill requests. For a signed-in customer the server always uses their own
 * meter, so the customer methods take no meter number.
 */
public class BillingClient {

    private static final Type BILL_LIST = new TypeToken<List<Bill>>() { }.getType();
    private static final Type PLAN_LIST = new TypeToken<List<TariffPlan>>() { }.getType();

    private final ApiClient api = ApiClient.get();

    /**
     * Admin: prices a meter reading and saves the bill. Leave {@code previousReading}
     * null to use the last bill's reading. If the bill checker has concerns the
     * server refuses unless {@code acknowledgeWarnings} is set.
     */
    public Bill createBill(String meterNo, String month, int year, Integer previousReading, int currentReading,
            boolean acknowledgeWarnings) {
        return api.post("/api/bills", new Api.NewBillRequest(meterNo, month, year, previousReading, currentReading,
                acknowledgeWarnings), Bill.class);
    }

    /** Admin: what a bill would cost and what the bill checker thinks, without saving it. */
    public Api.BillPreview preview(String meterNo, String month, int year, Integer previousReading, int currentReading) {
        return api.post("/api/bills/preview", new Api.PreviewRequest(meterNo, month, year, previousReading, currentReading),
                Api.BillPreview.class);
    }

    /** Admin: the meter's last recorded reading and how it's billed. */
    public Api.LastReading lastReading(String meterNo) {
        return api.get(ApiClient.withQuery("/api/meters/last-reading", ApiClient.params("meter", meterNo)),
                Api.LastReading.class);
    }

    /** Admin: any filter left null matches everything. */
    public List<Bill> search(String meterNo, String month, Integer year) {
        return api.get(ApiClient.withQuery("/api/bills",
                ApiClient.params("meter", meterNo, "month", month, "year", year)), BILL_LIST);
    }

    /** Admin: confirms a payment. */
    public void confirmPayment(String meterNo, String month, int year) {
        api.post("/api/bills/confirm", new Api.BillKey(meterNo, month, year), null);
    }

    /** Every tariff plan, newest first within each category. */
    public List<TariffPlan> tariffs() {
        return api.get("/api/tariffs", PLAN_LIST);
    }

    public List<Bill> myBills() {
        return search(null, null, null);
    }

    public Optional<Bill> myBill(String month, int year) {
        List<Bill> bills = search(null, month, year);
        return bills.isEmpty() ? Optional.empty() : Optional.of(bills.get(0));
    }

    public BillStatement myStatement(String month, int year) {
        return api.get(ApiClient.withQuery("/api/bills/statement",
                ApiClient.params("month", month, "year", year)), BillStatement.class);
    }

    /** Admin: any customer's statement. */
    public BillStatement statement(String meterNo, String month, int year) {
        return api.get(ApiClient.withQuery("/api/bills/statement",
                ApiClient.params("meter", meterNo, "month", month, "year", year)), BillStatement.class);
    }

    /** Customer reports a payment; it waits for an admin to confirm it. */
    public void submitPayment(String month, int year) {
        api.post("/api/bills/pay", new Api.BillKey(null, month, year), null);
    }

    /** Customer: what a month of this many units would cost them. */
    public Api.Estimate estimate(int units) {
        return api.post("/api/usage/estimate", new Api.EstimateRequest(units), Api.Estimate.class);
    }

    /** Customer: where this month is heading, from today's meter reading. */
    public Api.Forecast forecast(int currentReading) {
        return api.post("/api/usage/forecast", new Api.ForecastRequest(currentReading), Api.Forecast.class);
    }
}
