package electricity.billing.system.model;

/** A customer and the meter they are registered to. */
public record Customer(
        String meterNo,
        String name,
        String address,
        String city,
        String state,
        String email,
        String phone) {

    public Customer withMeterNo(String newMeterNo) {
        return new Customer(newMeterNo, name, address, city, state, email, phone);
    }
}
