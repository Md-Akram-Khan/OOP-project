package electricity.billing.system.service;

import electricity.billing.system.common.Api;
import electricity.billing.system.common.AppException;
import electricity.billing.system.common.Config;
import electricity.billing.system.common.Dates;
import electricity.billing.system.common.Money;
import electricity.billing.system.db.AuditDao;
import electricity.billing.system.db.BillDao;
import electricity.billing.system.db.CustomerDao;
import electricity.billing.system.db.Database;
import electricity.billing.system.db.NotificationDao;
import electricity.billing.system.db.PaymentDao;
import electricity.billing.system.model.Bill;
import electricity.billing.system.model.BillStatus;
import electricity.billing.system.model.Customer;
import electricity.billing.system.model.Months;
import electricity.billing.system.model.Notification;
import electricity.billing.system.model.Payment;
import electricity.billing.system.service.SslCommerzGateway.Validation;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Online bill payments through SSLCommerz.
 *
 * The rule throughout: nothing the browser sends back is trusted. A bill is
 * marked paid only after the gateway itself, asked server to server, confirms
 * a valid transaction whose id, amount and currency match what we recorded
 * when the payment started.
 */
public class PaymentService {

    private static final Logger LOG = Logger.getLogger(PaymentService.class.getName());
    private static final BigDecimal MINIMUM = new BigDecimal("10.00");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SslCommerzGateway gateway;

    public PaymentService() {
        this(new SslCommerzGateway());
    }

    public PaymentService(SslCommerzGateway gateway) {
        this.gateway = gateway;
    }

    public static String currency() {
        return Config.get("SSLCOMMERZ_CURRENCY", "BDT");
    }

    public Api.PaymentConfig config() {
        return new Api.PaymentConfig(SslCommerzGateway.isConfigured(), SslCommerzGateway.isSandbox(), currency());
    }

    /** Records a new payment for the customer's own bill and opens a checkout session for it. */
    public Api.PaymentStart start(String meterNo, String month, int year, String publicUrl) throws SQLException {
        if (!SslCommerzGateway.isConfigured()) {
            throw new AppException("Online payment isn't set up on the server yet. Ask the admin to add the SSLCommerz keys.");
        }
        if (!Months.isValid(month)) {
            throw new AppException("Unknown month: " + month);
        }
        String tranId = newTranId(meterNo, month, year);
        Map<String, String> fields = Database.inTransaction(c -> {
            Bill bill = new BillDao(c).find(meterNo, month, year)
                    .orElseThrow(() -> new AppException("There's no bill for " + month + " " + year + "."));
            if (bill.status() == BillStatus.PAID) {
                throw new AppException("This bill is already paid.");
            }
            if (bill.status() == BillStatus.PENDING) {
                throw new AppException("A payment for this bill is already waiting for confirmation.");
            }
            // Past the due date the late fee is included, exactly as the bill says.
            BigDecimal amount = bill.amountDue(Dates.today()).setScale(2, RoundingMode.HALF_UP);
            if (amount.compareTo(MINIMUM) < 0) {
                throw new AppException("Online payments must be at least " + MINIMUM + " " + currency() + ".");
            }
            Customer customer = new CustomerDao(c).find(meterNo)
                    .orElseThrow(() -> new AppException("Your customer details couldn't be found."));
            new PaymentDao(c).insert(new Payment(tranId, meterNo, month, year, amount, currency(), Payment.Status.INITIATED));
            return checkoutFields(tranId, amount, customer, month, year, publicUrl);
        });
        try {
            SslCommerzGateway.Session session = gateway.startSession(fields);
            Database.withConnection(c -> {
                new PaymentDao(c).saveSessionKey(tranId, session.sessionKey());
                return null;
            });
            return new Api.PaymentStart(tranId, session.gatewayUrl(), SslCommerzGateway.isSandbox());
        } catch (IOException | InterruptedException | RuntimeException e) {
            end(tranId, Payment.Status.FAILED);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            if (e instanceof AppException app) {
                throw app;
            }
            throw new AppException("Couldn't reach SSLCommerz. Check your internet connection and try again.");
        }
    }

    /**
     * Checks a payment with the gateway and, if it really went through, marks
     * the bill paid. {@code valIdHint} is what the browser or IPN claimed; it
     * is only used as a lookup key and is verified like everything else.
     */
    public Payment.Status confirm(String tranId, String valIdHint) throws SQLException {
        Payment payment = Database.withConnection(c -> new PaymentDao(c).find(tranId))
                .orElseThrow(() -> new AppException("Unknown payment."));
        if (payment.status() == Payment.Status.PAID || !SslCommerzGateway.isConfigured()) {
            return payment.status();
        }
        Validation proof = findProof(payment, valIdHint);
        if (proof == null) {
            return payment.status();
        }
        boolean risky = "1".equals(proof.riskLevel());
        return Database.inTransaction(c -> {
            PaymentDao payments = new PaymentDao(c);
            Payment locked = payments.findForUpdate(tranId).orElseThrow();
            if (locked.status() == Payment.Status.PAID) {
                return Payment.Status.PAID;
            }
            payments.markPaid(tranId, proof.valId(), proof.bankTranId(), proof.cardType());
            // The gateway flags some transactions as risky; those wait for an admin's eye before counting as paid.
            new BillDao(c).updateStatus(locked.meterNo(), locked.month(), locked.year(),
                    risky ? BillStatus.PENDING : BillStatus.PAID, BillStatus.NOT_PAID, BillStatus.PENDING);
            LOG.info("Payment " + tranId + " confirmed by SSLCommerz" + (risky ? " (flagged risky, sent for review)" : ""));
            String period = locked.month() + " " + locked.year();
            new NotificationDao(c).insert(locked.meterNo(), Notification.Kind.PAYMENT,
                    risky ? "Payment received, being checked" : "Payment received for " + period,
                    Money.tk(locked.amount()) + " paid online" + (proof.cardType() == null ? "" : " with " + proof.cardType())
                            + (risky ? ". SSLCommerz asked for a quick check, so an admin will confirm it shortly." : ". Thank you!"));
            new AuditDao(c).insert("SSLCommerz", "PAYMENT_ONLINE", locked.meterNo() + " " + period,
                    Money.tk(locked.amount()) + ", transaction " + tranId + (risky ? ", flagged risky: sent for review" : ""));
            return Payment.Status.PAID;
        });
    }

    /** Ends an unfinished payment after a cancel or failure, unless the gateway shows it actually succeeded. */
    public Payment.Status endIfUnpaid(String tranId, Payment.Status outcome) throws SQLException {
        Payment.Status now = confirm(tranId, null);
        if (now != Payment.Status.INITIATED) {
            return now;
        }
        end(tranId, outcome);
        return outcome;
    }

    /** Where the customer's payment stands. Customers can only ask about their own payments. */
    public Api.PaymentState status(String tranId, String meterNo) throws SQLException {
        Payment payment = Database.withConnection(c -> new PaymentDao(c).find(tranId))
                .filter(p -> p.meterNo().equals(meterNo))
                .orElseThrow(() -> new AppException("Unknown payment."));
        Payment.Status status = payment.status() == Payment.Status.INITIATED ? confirm(tranId, null) : payment.status();
        BillStatus bill = Database.withConnection(c -> new BillDao(c).find(payment.meterNo(), payment.month(), payment.year()))
                .map(Bill::status).orElse(null);
        return new Api.PaymentState(tranId, status, bill);
    }

    private Validation findProof(Payment payment, String valIdHint) {
        List<Validation> candidates = new ArrayList<>();
        try {
            if (valIdHint != null && !valIdHint.isBlank()) {
                candidates.add(gateway.validate(valIdHint.trim()));
            }
            candidates.addAll(gateway.lookup(payment.tranId()));
        } catch (IOException e) {
            LOG.warning("Couldn't check payment " + payment.tranId() + " with SSLCommerz: " + e.getMessage());
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
        for (Validation v : candidates) {
            if (!v.isValid()) {
                continue;
            }
            boolean sameTransaction = payment.tranId().equals(v.tranId());
            boolean sameAmount = v.amount() != null && v.amount().compareTo(payment.amount()) == 0;
            boolean sameCurrency = payment.currency().equalsIgnoreCase(v.currency());
            if (sameTransaction && sameAmount && sameCurrency) {
                return v;
            }
            LOG.warning("Rejected a gateway record for " + payment.tranId() + ": transaction match=" + sameTransaction
                    + ", amount match=" + sameAmount + ", currency match=" + sameCurrency);
        }
        return null;
    }

    private void end(String tranId, Payment.Status outcome) {
        try {
            Database.withConnection(c -> new PaymentDao(c).markEnded(tranId, outcome));
        } catch (SQLException e) {
            LOG.warning("Couldn't record " + outcome + " for payment " + tranId + ": " + e.getMessage());
        }
    }

    private static Map<String, String> checkoutFields(String tranId, BigDecimal amount, Customer customer,
            String month, int year, String publicUrl) {
        String tran = URLEncoder.encode(tranId, StandardCharsets.UTF_8);
        Map<String, String> f = new LinkedHashMap<>();
        f.put("total_amount", amount.toPlainString());
        f.put("currency", currency());
        f.put("tran_id", tranId);
        f.put("success_url", publicUrl + "/pay/success?tran=" + tran);
        f.put("fail_url", publicUrl + "/pay/fail?tran=" + tran);
        f.put("cancel_url", publicUrl + "/pay/cancel?tran=" + tran);
        f.put("ipn_url", publicUrl + "/pay/ipn");
        f.put("cus_name", customer.name());
        f.put("cus_email", customer.email());
        f.put("cus_add1", customer.address());
        f.put("cus_city", customer.city());
        f.put("cus_state", customer.state());
        f.put("cus_postcode", Config.get("SSLCOMMERZ_DEFAULT_POSTCODE", "1000"));
        f.put("cus_country", Config.get("SSLCOMMERZ_COUNTRY", "Bangladesh"));
        f.put("cus_phone", customer.phone());
        f.put("shipping_method", "NO");
        f.put("num_of_item", "1");
        f.put("product_name", "Electricity bill " + month + " " + year);
        f.put("product_category", "Utility");
        f.put("product_profile", "non-physical-goods");
        f.put("value_a", customer.meterNo());
        return f;
    }

    private static String newTranId(String meterNo, String month, int year) {
        byte[] bytes = new byte[5];
        RANDOM.nextBytes(bytes);
        return "EBS" + meterNo + year + String.format("%02d", Months.NAMES.indexOf(month) + 1) + "-" + HexFormat.of().formatHex(bytes);
    }
}
