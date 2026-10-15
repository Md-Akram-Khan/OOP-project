package electricity.billing.system.ui;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/** Opens the operating system's own notepad and calculator apps. */
public final class SystemTools {

    public enum Tool {
        NOTEPAD("notepad.exe", "TextEdit", "gedit"),
        CALCULATOR("calc.exe", "Calculator", "gnome-calculator");

        private final String windows;
        private final String mac;
        private final String linux;

        Tool(String windows, String mac, String linux) {
            this.windows = windows;
            this.mac = mac;
            this.linux = linux;
        }
    }

    private SystemTools() {
    }

    public static void launch(Tool tool) throws IOException {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        List<String> command;
        if (os.contains("win")) {
            command = List.of(tool.windows);
        } else if (os.contains("mac")) {
            command = List.of("open", "-a", tool.mac);
        } else {
            command = List.of(tool.linux);
        }
        new ProcessBuilder(command).start();
    }
}
