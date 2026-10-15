package electricity.billing.system.db;

import electricity.billing.system.model.Customer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Reads and writes the customer table. */
public class CustomerDao {

    private static final String COLUMNS = "meter_no, name, address, city, state, email, phone";

    private final Connection c;

    public CustomerDao(Connection c) {
        this.c = c;
    }

    public void insert(Customer customer) throws SQLException {
        String sql = "insert into customer (" + COLUMNS + ") values (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, customer.meterNo());
            ps.setString(2, customer.name());
            ps.setString(3, customer.address());
            ps.setString(4, customer.city());
            ps.setString(5, customer.state());
            ps.setString(6, customer.email());
            ps.setString(7, customer.phone());
            ps.executeUpdate();
        }
    }

    public int updateContact(Customer customer) throws SQLException {
        String sql = "update customer set address = ?, city = ?, state = ?, email = ?, phone = ? where meter_no = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, customer.address());
            ps.setString(2, customer.city());
            ps.setString(3, customer.state());
            ps.setString(4, customer.email());
            ps.setString(5, customer.phone());
            ps.setString(6, customer.meterNo());
            return ps.executeUpdate();
        }
    }

    public Optional<Customer> find(String meterNo) throws SQLException {
        String sql = "select " + COLUMNS + " from customer where meter_no = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, meterNo);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public boolean exists(String meterNo) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("select 1 from customer where meter_no = ?")) {
            ps.setString(1, meterNo);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public List<Customer> findAll() throws SQLException {
        List<Customer> customers = new ArrayList<>();
        String sql = "select " + COLUMNS + " from customer order by name, meter_no";
        try (PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                customers.add(map(rs));
            }
        }
        return customers;
    }

    public List<String> meterNumbers() throws SQLException {
        List<String> meters = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("select meter_no from customer order by meter_no");
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                meters.add(rs.getString(1));
            }
        }
        return meters;
    }

    private static Customer map(ResultSet rs) throws SQLException {
        return new Customer(
                rs.getString("meter_no"),
                rs.getString("name"),
                rs.getString("address"),
                rs.getString("city"),
                rs.getString("state"),
                rs.getString("email"),
                rs.getString("phone"));
    }
}
