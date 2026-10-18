package electricity.billing.system.client;

import com.google.gson.reflect.TypeToken;
import electricity.billing.system.common.Api;
import electricity.billing.system.model.MeterApplication;
import java.lang.reflect.Type;
import java.util.List;

/** Applications for a new meter: applicants apply, admins review and connect. */
public class ApplicationClient {

    private static final Type APPLICATION_LIST = new TypeToken<List<MeterApplication>>() { }.getType();

    private final ApiClient api = ApiClient.get();

    /** Applicant: their verified name, NID and phone. */
    public Api.ApplicantProfile profile() {
        return api.get("/api/applicant/me", Api.ApplicantProfile.class);
    }

    /** An applicant's own applications, or everyone's for an admin. Newest first. */
    public List<MeterApplication> list() {
        return api.get("/api/applications", APPLICATION_LIST);
    }

    public MeterApplication submit(Api.NewApplication form) {
        return api.post("/api/applications", form, MeterApplication.class);
    }

    /** Admin: change the status and leave a note for the applicant. */
    public MeterApplication update(long id, MeterApplication.Status status, String note) {
        return api.post("/api/applications/update", new Api.ApplicationUpdate(id, status, note), MeterApplication.class);
    }

    /** Admin: the meter is installed; creates the customer and links the applicant's account. */
    public MeterApplication connect(long id, String meterNo, String meterType) {
        return api.post("/api/applications/connect", new Api.ConnectMeter(id, meterNo, meterType), MeterApplication.class);
    }
}
