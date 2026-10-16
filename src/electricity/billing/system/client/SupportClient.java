package electricity.billing.system.client;

import com.google.gson.reflect.TypeToken;
import electricity.billing.system.common.Api;
import electricity.billing.system.model.AuditEntry;
import electricity.billing.system.model.Complaint;
import java.lang.reflect.Type;
import java.util.List;

/** Complaints, notifications and the audit log. */
public class SupportClient {

    private static final Type COMPLAINT_LIST = new TypeToken<List<Complaint>>() { }.getType();
    private static final Type AUDIT_LIST = new TypeToken<List<AuditEntry>>() { }.getType();

    private final ApiClient api = ApiClient.get();

    /** A customer gets their own complaints; an admin gets everyone's. */
    public List<Complaint> complaints() {
        return api.get("/api/complaints", COMPLAINT_LIST);
    }

    /** Customer: opens a complaint. */
    public Complaint openComplaint(Complaint.Category category, String subject, String details) {
        return api.post("/api/complaints", new Api.NewComplaint(category, subject, details), Complaint.class);
    }

    /** Admin: changes a complaint's status and replies to the customer. */
    public Complaint updateComplaint(long id, Complaint.Status status, String reply) {
        return api.post("/api/complaints/update", new Api.ComplaintUpdate(id, status, reply), Complaint.class);
    }

    /** Customer: notifications and due-date reminders, newest first. */
    public Api.NotificationFeed notifications() {
        return api.get("/api/notifications", Api.NotificationFeed.class);
    }

    public void markNotificationsRead() {
        api.post("/api/notifications/read", null, null);
    }

    /** Admin: the most recent changes, newest first. */
    public List<AuditEntry> audit(int limit) {
        return api.get(ApiClient.withQuery("/api/audit", ApiClient.params("limit", limit)), AUDIT_LIST);
    }
}
