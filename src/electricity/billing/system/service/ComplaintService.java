package electricity.billing.system.service;

import electricity.billing.system.common.Api;
import electricity.billing.system.common.AppException;
import electricity.billing.system.db.ComplaintDao;
import electricity.billing.system.db.Database;
import electricity.billing.system.db.NotificationDao;
import electricity.billing.system.model.Complaint;
import electricity.billing.system.model.Notification;
import java.sql.SQLException;
import java.util.List;

/** Complaints and service requests: customers open them, admins answer and close them. */
public class ComplaintService {

    private static final int SUBJECT_MIN = 5;
    private static final int SUBJECT_MAX = 120;
    private static final int DETAILS_MIN = 10;
    private static final int DETAILS_MAX = 2000;

    public Complaint open(String meterNo, Api.NewComplaint request) throws SQLException {
        if (request.category() == null) {
            throw new AppException("Choose what the problem is about.");
        }
        String subject = trim(request.subject());
        String details = trim(request.details());
        if (subject.length() < SUBJECT_MIN || subject.length() > SUBJECT_MAX) {
            throw new AppException("The subject must be " + SUBJECT_MIN + " to " + SUBJECT_MAX + " characters.");
        }
        if (details.length() < DETAILS_MIN || details.length() > DETAILS_MAX) {
            throw new AppException("Describe the problem in " + DETAILS_MIN + " to " + DETAILS_MAX + " characters.");
        }
        return Database.inTransaction(c -> {
            ComplaintDao complaints = new ComplaintDao(c);
            long id = complaints.insert(meterNo, request.category(), subject, details);
            new NotificationDao(c).insert(meterNo, Notification.Kind.COMPLAINT, "We got your complaint " + Complaint.ticket(id),
                    "\"" + subject + "\". We'll update you here when an admin replies.");
            return complaints.find(id).orElseThrow();
        });
    }

    /** One customer's complaints, or everyone's when {@code meterNo} is null. */
    public List<Complaint> list(String meterNo) throws SQLException {
        return Database.withConnection(c -> new ComplaintDao(c).list(meterNo));
    }

    /** An admin changes the status and/or replies. The customer is notified. */
    public Complaint update(Api.ComplaintUpdate request) throws SQLException {
        if (request.status() == null) {
            throw new AppException("Choose a status.");
        }
        String reply = trim(request.reply());
        if (reply.length() > DETAILS_MAX) {
            throw new AppException("Keep the reply under " + DETAILS_MAX + " characters.");
        }
        if (request.status() == Complaint.Status.RESOLVED && reply.isEmpty()) {
            throw new AppException("Tell the customer how it was resolved before closing the complaint.");
        }
        return Database.inTransaction(c -> {
            ComplaintDao complaints = new ComplaintDao(c);
            Complaint before = complaints.find(request.id())
                    .orElseThrow(() -> new AppException("That complaint doesn't exist."));
            String saved = reply.isEmpty() ? before.adminReply() : reply;
            complaints.update(before.id(), request.status(), saved);
            new NotificationDao(c).insert(before.meterNo(), Notification.Kind.COMPLAINT,
                    before.ticket() + " is now " + request.status().label().toLowerCase(),
                    reply.isEmpty() ? "\"" + before.subject() + "\"" : reply);
            return complaints.find(before.id()).orElseThrow();
        });
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
