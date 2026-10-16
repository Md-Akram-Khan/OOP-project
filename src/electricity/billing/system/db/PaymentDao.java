package electricity.billing.system.db;

import electricity.billing.system.model.Payment;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/** Reads and writes the payment table. */
public class PaymentDao {

    private final Connection c;

    public PaymentDao(Connection c) {
        this.c = c;
    }

    public void insert(Payment p) throws SQLException {
        String sql = "insert into payment (tran_id, meter_no, month, year, amount, currency, status) values (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, p.tranId());
            ps.setString(2, p.meterNo());
            ps.setString(3, p.month());
            ps.setInt(4, p.year());
            ps.setBigDecimal(5, p.amount());
            ps.setString(6, p.currency());
            ps.setString(7, p.status().name());
            ps.executeUpdate();
        }
    }

    public void saveSessionKey(String tranId, String sessionKey) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "update payment set session_key = ?, updated_at = now() where tran_id = ?")) {
            ps.setString(1, sessionKey);
            ps.setString(2, tranId);
            ps.executeUpdate();
        }
    }

    /** Locks the row for the rest of the transaction, so two confirmations can't race. */
    public Optional<Payment> findForUpdate(String tranId) throws SQLException {
        return find(tranId, " for update");
    }

    public Optional<Payment> find(String tranId) throws SQLException {
        return find(tranId, "");
    }

    private Optional<Payment> find(String tranId, String suffix) throws SQLException {
        String sql = "select tran_id, meter_no, month, year, amount, currency, status from payment where tran_id = ?" + suffix;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, tranId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new Payment(rs.getString("tran_id"), rs.getString("meter_no"), rs.getString("month"),
                        rs.getInt("year"), rs.getBigDecimal("amount"), rs.getString("currency"),
                        Payment.Status.valueOf(rs.getString("status"))));
            }
        }
    }

    public void markPaid(String tranId, String valId, String bankTranId, String cardType) throws SQLException {
        String sql = "update payment set status = 'PAID', val_id = ?, bank_tran_id = ?, card_type = ?, updated_at = now() "
                + "where tran_id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, valId);
            ps.setString(2, bankTranId);
            ps.setString(3, cardType);
            ps.setString(4, tranId);
            ps.executeUpdate();
        }
    }

    /** Ends an unfinished payment; never touches one that's already paid. */
    public int markEnded(String tranId, Payment.Status status) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "update payment set status = ?, updated_at = now() where tran_id = ? and status = 'INITIATED'")) {
            ps.setString(1, status.name());
            ps.setString(2, tranId);
            return ps.executeUpdate();
        }
    }
}
