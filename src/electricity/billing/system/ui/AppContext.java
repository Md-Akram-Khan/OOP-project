package electricity.billing.system.ui;

import electricity.billing.system.client.AuthClient;
import electricity.billing.system.client.BillingClient;
import electricity.billing.system.client.CustomerClient;
import electricity.billing.system.model.UserAccount;

/** What every page needs: the signed-in user, the API clients and navigation. */
public final class AppContext {

    public final AuthClient auth = new AuthClient();
    public final CustomerClient customers = new CustomerClient();
    public final BillingClient billing = new BillingClient();

    private final UserAccount account;
    private final AppWindow window;

    AppContext(UserAccount account, AppWindow window) {
        this.account = account;
        this.window = window;
    }

    public UserAccount account() {
        return account;
    }

    public AppWindow window() {
        return window;
    }

    public void go(String page) {
        window.navigate(page, null);
    }

    /** Opens a page and hands it a value, e.g. which bill to show. */
    public void go(String page, Object param) {
        window.navigate(page, param);
    }
}
