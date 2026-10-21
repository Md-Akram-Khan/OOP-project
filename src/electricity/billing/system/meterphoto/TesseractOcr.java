package electricity.billing.system.meterphoto;

import electricity.billing.system.common.Config;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;

/**
 * Runs the Tesseract OCR engine (free, open source, offline) on an image and
 * returns the text it read, line by line.
 *
 * Tesseract is a separate program installed on the server machine (on Windows:
 * {@code winget install UB-Mannheim.TesseractOCR}). It is found through the
 * TESSERACT_PATH setting, the PATH, or its usual install folder; without it,
 * callers fall back to the built-in reader. The image is passed as a PNG the
 * server encodes itself, over standard input, so the uploaded file is never
 * handed to Tesseract as it came.
 */
final class TesseractOcr {

    private static final long TIMEOUT_SECONDS = 10;
    private static final String[] USUAL_PLACES = {
        "C:\\Program Files\\Tesseract-OCR\\tesseract.exe",
        "C:\\Program Files (x86)\\Tesseract-OCR\\tesseract.exe",
        "/usr/bin/tesseract",
        "/usr/local/bin/tesseract",
        "/opt/homebrew/bin/tesseract",
    };
    private static volatile String executable;
    private static volatile boolean looked;

    private TesseractOcr() {
    }

    /** The Tesseract program to run, or null if it isn't installed. */
    static String executable() {
        if (!looked) {
            synchronized (TesseractOcr.class) {
                if (!looked) {
                    executable = find();
                    looked = true;
                }
            }
        }
        return executable;
    }

    static boolean available() {
        return executable() != null;
    }

    /** "Tesseract 5.4.0 at …" or why it isn't used, for the server's start-up log. */
    static String describe() {
        String exe = executable();
        if (exe == null) {
            return "Tesseract OCR not found; using the built-in reader (install Tesseract, or set TESSERACT_PATH, for better results)";
        }
        List<String> version = run(List.of(exe, "--version"), null);
        return (version.isEmpty() ? "Tesseract" : version.get(0)) + " at " + exe;
    }

    private static String find() {
        String configured = Config.get("TESSERACT_PATH", null);
        if (configured != null) {
            return new File(configured).canExecute() ? configured : null;
        }
        String path = System.getenv("PATH");
        if (path != null) {
            for (String dir : path.split(File.pathSeparator)) {
                for (String name : new String[] {"tesseract.exe", "tesseract"}) {
                    File f = new File(dir, name);
                    if (f.isFile() && f.canExecute()) {
                        return f.getAbsolutePath();
                    }
                }
            }
        }
        for (String place : USUAL_PLACES) {
            if (new File(place).canExecute()) {
                return place;
            }
        }
        return null;
    }

    /**
     * The text Tesseract reads in {@code image} with page segmentation mode
     * {@code psm} (3 automatic layout, 4 one column, 6 one block, 11 scattered
     * text), as non-blank lines; empty if it isn't installed or fails.
     */
    static List<String> read(BufferedImage image, int psm) {
        String exe = executable();
        if (exe == null) {
            return List.of();
        }
        byte[] png;
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            png = out.toByteArray();
        } catch (IOException e) {
            return List.of();
        }
        return run(List.of(exe, "stdin", "stdout", "-l", "eng", "--psm", String.valueOf(psm)), png);
    }

    private static List<String> run(List<String> command, byte[] input) {
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD);
            // Several passes run side by side; one thread each keeps them from slowing one another down.
            builder.environment().put("OMP_THREAD_LIMIT", "1");
            process = builder.start();
            Process p = process;
            Thread feeder = new Thread(() -> {
                try (OutputStream in = p.getOutputStream()) {
                    if (input != null) {
                        in.write(input);
                    }
                } catch (IOException e) {
                    // Tesseract stopped reading; its exit status tells the rest.
                }
            });
            feeder.setDaemon(true);
            feeder.start();
            // Read the output alongside, so a stuck Tesseract still hits the time limit.
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            Thread reader = new Thread(() -> {
                try (InputStream out = p.getInputStream()) {
                    out.transferTo(output);
                } catch (IOException e) {
                    // The process was stopped.
                }
            });
            reader.setDaemon(true);
            reader.start();
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                return List.of();
            }
            reader.join(TimeUnit.SECONDS.toMillis(2));
            if (process.exitValue() != 0) {
                return List.of();
            }
            List<String> lines = new ArrayList<>();
            for (String line : output.toString(StandardCharsets.UTF_8).split("\\R")) {
                if (!line.isBlank()) {
                    lines.add(line.trim());
                }
            }
            return lines;
        } catch (IOException e) {
            return List.of();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return List.of();
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }
}
