package electricity.billing.system.db;

import electricity.billing.system.model.NidRecord;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Reads the dummy NID authority database (nid_registry). */
public class NidDao {

    private static final String SELECT = "select nid_no, name_en, name_bn, father_bn, mother_bn, date_of_birth, address_bn, "
            + "birth_place_bn, blood_group, issue_date from nid_registry";

    private final Connection c;

    public NidDao(Connection c) {
        this.c = c;
    }

    public List<NidRecord> all() throws SQLException {
        List<NidRecord> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(SELECT + " order by nid_no"); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(map(rs));
            }
        }
        return list;
    }

    public Optional<NidRecord> find(String nidNo) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(SELECT + " where nid_no = ?")) {
            ps.setString(1, nidNo);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    private static NidRecord map(ResultSet rs) throws SQLException {
        return new NidRecord(rs.getString("nid_no"), rs.getString("name_en"), rs.getString("name_bn"), rs.getString("father_bn"),
                rs.getString("mother_bn"), rs.getObject("date_of_birth", LocalDate.class), rs.getString("address_bn"),
                rs.getString("birth_place_bn"), rs.getString("blood_group"), rs.getObject("issue_date", LocalDate.class));
    }
}
