package electricity.billing.system.model;

/**
 * What the server read from a photo of a meter.
 *
 * @param meterNo     the customer meter identified from the number printed on the meter
 * @param displayText the figure on the display, e.g. "528.53"
 * @param reading     the whole kWh billed from it (528); bills use whole units
 * @param numberSeen  the characters read where the meter number was found, for information
 * @param note        something the admin should check about the reading (e.g. a number wheel caught between
 *                    two digits), or null
 */
public record PhotoReading(String meterNo, String displayText, int reading, String numberSeen, String note) {
}
