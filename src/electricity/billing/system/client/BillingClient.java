package electricity.billing.system.client;

import com.google.gson.reflect.TypeToken;
import electricity.billing.system.common.Api;
import electricity.billing.system.model.Bill;
import electricity.billing.system.model.BillStatement;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Optional;

/**
 * Bill requests. For a signed-in customer the server always uses their own
 * meter, so the customer methods take no meter number.
 */
public class BillingClient {

    private static final Type BILL_LIST = new TypeToken<List<Bill>>() { }.getType();

    private final ApiClient api = ApiClient.get();

    /** Admin: prices and saves a bill. */
    public Bill createBill(String meterNo, String month, int year, int units) {
        return api.post("/api/bills", new Api.NewBillRequest(meterNo, month, year, units), Bill.class);
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

    /** Customer reports a payment; it waits for an admin to confirm it. */
    public void submitPayment(String month, int year) {
        api.post("/api/bills/pay", new Api.BillKey(null, month, year), null);
    }
}
