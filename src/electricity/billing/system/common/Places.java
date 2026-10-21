package electricity.billing.system.common;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bangladesh's divisions and their districts, for addresses, and suggested
 * thanas where they're known (DPDC's lists for Dhaka and Narayanganj; other
 * thanas and upazilas are typed in).
 */
public final class Places {

    /** Each division's districts, in alphabetical order. */
    public static final Map<String, List<String>> DISTRICTS;

    static {
        Map<String, List<String>> m = new LinkedHashMap<>();
        m.put("Barishal", List.of("Barguna", "Barishal", "Bhola", "Jhalokati", "Patuakhali", "Pirojpur"));
        m.put("Chattogram", List.of("Bandarban", "Brahmanbaria", "Chandpur", "Chattogram", "Cox's Bazar", "Cumilla", "Feni",
                "Khagrachhari", "Lakshmipur", "Noakhali", "Rangamati"));
        m.put("Dhaka", List.of("Dhaka", "Faridpur", "Gazipur", "Gopalganj", "Kishoreganj", "Madaripur", "Manikganj", "Munshiganj",
                "Narayanganj", "Narsingdi", "Rajbari", "Shariatpur", "Tangail"));
        m.put("Khulna", List.of("Bagerhat", "Chuadanga", "Jashore", "Jhenaidah", "Khulna", "Kushtia", "Magura", "Meherpur",
                "Narail", "Satkhira"));
        m.put("Mymensingh", List.of("Jamalpur", "Mymensingh", "Netrokona", "Sherpur"));
        m.put("Rajshahi", List.of("Bogura", "Chapai Nawabganj", "Joypurhat", "Naogaon", "Natore", "Pabna", "Rajshahi",
                "Sirajganj"));
        m.put("Rangpur", List.of("Dinajpur", "Gaibandha", "Kurigram", "Lalmonirhat", "Nilphamari", "Panchagarh", "Rangpur",
                "Thakurgaon"));
        m.put("Sylhet", List.of("Habiganj", "Moulvibazar", "Sunamganj", "Sylhet"));
        DISTRICTS = java.util.Collections.unmodifiableMap(m);
    }

    /** Bangladesh's eight divisions. */
    public static final List<String> DIVISIONS = List.copyOf(DISTRICTS.keySet());

    private static final Map<String, List<String>> THANAS = Map.of(
            "Dhaka", List.of("Adabor", "Banani", "Bangshal", "Chalkbazar Model", "Demra", "Dhanmondi", "Gandaria", "Hatirjheel",
                    "Hazaribag", "Jatrabari", "Kadamtoli", "Kalabagan", "Kamrangir Char", "Khilgaon", "Kotowali", "Lalbag",
                    "Mohammadpur", "Motijheel", "Mugdapara", "New Market", "Paltan", "Ramna", "Rampura", "Sabujbagh",
                    "Shahabag", "Shahjahanpur", "Sher-e-Bangla Nagar", "Shyampur", "Sutrapur", "Tejgaon", "Tejgaon I/A",
                    "Trimohoni", "Wari"),
            "Narayanganj", List.of("Fatullah", "Narayanganj Sadar", "Rupganj", "Siddhirganj"));

    private Places() {
    }

    /** True when {@code district} is one of {@code division}'s districts. */
    public static boolean inDivision(String district, String division) {
        return division != null && DISTRICTS.getOrDefault(division, List.of()).contains(district);
    }

    /** Thanas to suggest for a district; empty where none are listed (the thana is typed in). */
    public static List<String> thanas(String district) {
        return THANAS.getOrDefault(district, List.of());
    }
}
