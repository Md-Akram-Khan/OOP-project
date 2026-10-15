package electricity.billing.system.common;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/** The single JSON configuration used by both the server and the app. */
public final class Json {

    public static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private Json() {
    }
}
