package electricity.billing.system.client;

import com.google.gson.reflect.TypeToken;
import electricity.billing.system.common.Api;
import electricity.billing.system.model.BillType;
import electricity.billing.system.model.Customer;
import electricity.billing.system.model.Meter;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.List;

/** Customer and meter requests. "my" methods act on the signed-in customer; the rest are admin-only. */
public class CustomerClient {

    private static final Type CUSTOMER_LIST = new TypeToken<List<Customer>>() { }.getType();
    private static final Type STRING_LIST = new TypeToken<List<String>>() { }.getType();

    private final ApiClient api = ApiClient.get();

    /** Admin: saves a new customer and their meter together (all or nothing). */
    public Customer create(Customer draft, String meterType, BillType billType, BigDecimal sanctionedLoadKw) {
        return api.post("/api/customers", new Api.NewCustomerRequest(draft.meterNo(), draft.name(), draft.address(), draft.city(),
                draft.state(), draft.email(), draft.phone(), meterType, billType, sanctionedLoadKw), Customer.class);
    }

    public Customer create(Customer draft) {
        return api.post("/api/customers", draft, Customer.class);
    }

    public List<Customer> findAll() {
        return api.get("/api/customers", CUSTOMER_LIST);
    }

    public Customer find(String meterNo) {
        return api.get(ApiClient.withQuery("/api/customers/lookup", ApiClient.params("meter", meterNo)), Customer.class);
    }

    public List<String> meterNumbers() {
        return api.get("/api/meters/numbers", STRING_LIST);
    }

    public void saveMeter(Meter meter) {
        api.put("/api/meters", meter, null);
    }

    public Customer myProfile() {
        return api.get("/api/customers/me", Customer.class);
    }

    public Customer updateMyContact(Customer changed) {
        return api.put("/api/customers/me", new Api.ContactUpdate(changed.address(), changed.city(),
                changed.state(), changed.email(), changed.phone()), Customer.class);
    }
}
