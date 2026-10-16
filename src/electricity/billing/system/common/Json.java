package electricity.billing.system.common;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.function.Function;

/** The single JSON configuration used by both the server and the app. */
public final class Json {

    /** Dates travel as ISO text ("2026-09-23"); Gson can't read java.time classes on its own. */
    public static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .registerTypeAdapter(LocalDate.class, iso(LocalDate::parse).nullSafe())
            .registerTypeAdapter(LocalDateTime.class, iso(LocalDateTime::parse).nullSafe())
            .create();

    private Json() {
    }

    private static <T> TypeAdapter<T> iso(Function<String, T> parse) {
        return new TypeAdapter<>() {
            @Override
            public void write(JsonWriter out, T value) throws IOException {
                out.value(value.toString());
            }

            @Override
            public T read(JsonReader in) throws IOException {
                if (in.peek() == JsonToken.NULL) {
                    in.nextNull();
                    return null;
                }
                return parse.apply(in.nextString());
            }
        };
    }
}
