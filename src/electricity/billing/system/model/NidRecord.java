package electricity.billing.system.model;

import java.time.LocalDate;

/**
 * One person in the dummy NID authority database used for this project. A
 * real deployment would query the Election Commission's NID service, which
 * needs authorised access; these are manually stored sample records.
 */
public record NidRecord(
        String nidNo,
        String nameEn,
        String nameBn,
        String fatherBn,
        String motherBn,
        LocalDate dateOfBirth,
        String addressBn,
        String birthPlaceBn,
        String bloodGroup,
        LocalDate issueDate) {
}
