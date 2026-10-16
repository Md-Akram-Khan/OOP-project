package electricity.billing.system.model;

import java.time.LocalDateTime;

/** One recorded change: who did what to which record, and when. */
public record AuditEntry(long id, LocalDateTime at, String actor, String action, String target, String details) {
}
