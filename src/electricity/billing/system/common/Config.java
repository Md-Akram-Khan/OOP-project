package electricity.billing.system.common;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads settings from environment variables, falling back to a .env file in
 * the working directory or up to three folders above it.
 */
public final class Config {

    private static volatile Map<String, String> dotenv;

    private Config() {
    }

    public static String get(String name, String defaultValue) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            value = dotenv().get(name);
        }
        return value == null || value.isBlank() ? defaultValue : value;
    }

    public static String require(String name) {
        String value = get(name, null);
        if (value == null) {
            throw new IllegalStateException(
                    "Missing " + name + ". Copy .env.example to .env and fill it in, or set it as an environment variable.");
        }
        return value;
    }

    private static Map<String, String> dotenv() {
        Map<String, String> values = dotenv;
        if (values == null) {
            values = loadDotEnv();
            dotenv = values;
        }
        return values;
    }

    private static Map<String, String> loadDotEnv() {
        Map<String, String> values = new HashMap<>();
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (int level = 0; level < 4 && current != null; level++, current = current.getParent()) {
            Path candidate = current.resolve(".env");
            if (!Files.isRegularFile(candidate)) {
                continue;
            }
            try {
                List<String> lines = Files.readAllLines(candidate);
                for (String line : lines) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                        continue;
                    }
                    int separator = trimmed.indexOf('=');
                    if (separator <= 0) {
                        continue;
                    }
                    String key = trimmed.substring(0, separator).trim();
                    String value = trimmed.substring(separator + 1).trim();
                    if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                        value = value.substring(1, value.length() - 1);
                    }
                    values.putIfAbsent(key, value);
                }
                return values;
            } catch (IOException e) {
                throw new IllegalStateException("Unable to read " + candidate, e);
            }
        }
        return values;
    }
}
