package io.mikoshift.natsu.service.bookimport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

/**
 * Bridges a format Pandoc understands (DOCX, RTF, ...) through the {@code pandoc} CLI into EPUB,
 * then delegates to {@link EpubImporter} for the actual block extraction. Pandoc must be installed
 * on the host/image (see Dockerfile); if it's missing, every import of that format fails with a
 * clear message rather than silently degrading.
 *
 * <p>Runs with Pandoc's {@code --sandbox} flag (restricts filesystem access from within Lua
 * filters/includes -- the input file is untrusted user content) and a hard wall-clock timeout.
 * There's no OS-level process sandbox (seccomp/Job Object) here; the container already runs as a
 * non-root user with nothing else on the filesystem worth reaching, which is the realistic
 * blast-radius limit for this deployment.
 */
@Slf4j
public abstract class PandocBridgeImporter implements BookImporter {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final EpubImporter epubImporter;

    protected PandocBridgeImporter(EpubImporter epubImporter) {
        this.epubImporter = epubImporter;
    }

    protected abstract String sourceExtension();

    @Override
    public ImportedBook importFrom(byte[] sourceBytes) {
        Path workDir;
        try {
            workDir = Files.createTempDirectory("natsu-pandoc-");
        } catch (IOException e) {
            throw new TransientImportException("Failed to create temp directory for conversion", e);
        }
        try {
            Path input = workDir.resolve("input." + sourceExtension());
            Path output = workDir.resolve("output.epub");
            try {
                Files.write(input, sourceBytes);
            } catch (IOException e) {
                throw new TransientImportException("Failed to write temp input file", e);
            }

            runPandoc(input, output);

            byte[] epubBytes;
            try {
                epubBytes = Files.readAllBytes(output);
            } catch (IOException e) {
                throw new ImportException(labelUpper() + " conversion produced no output", e);
            }
            return epubImporter.importFrom(epubBytes);
        } finally {
            deleteRecursively(workDir);
        }
    }

    private void runPandoc(Path input, Path output) {
        ProcessBuilder builder = new ProcessBuilder(
                        "pandoc", "--sandbox", "--standalone", input.toString(), "-o", output.toString())
                .redirectErrorStream(true);
        Process process;
        try {
            process = builder.start();
        } catch (IOException e) {
            throw new ImportException(
                    "Pandoc is not available on this server; " + labelUpper() + " import is currently unsupported", e);
        }

        String processOutput;
        try {
            processOutput = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            process.destroyForcibly();
            throw new ImportException("Failed to read pandoc output", e);
        }

        boolean finished;
        try {
            finished = process.waitFor(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new TransientImportException("Interrupted while waiting for pandoc", e);
        }
        if (!finished) {
            process.destroyForcibly();
            throw new ImportException("Pandoc conversion timed out");
        }
        if (process.exitValue() != 0) {
            log.warn("Pandoc conversion failed (exit {}): {}", process.exitValue(), processOutput);
            throw new ImportException("Could not convert " + labelUpper() + " file: malformed or unsupported document");
        }
    }

    private String labelUpper() {
        return sourceExtension().toUpperCase(Locale.ROOT);
    }

    private static void deleteRecursively(Path dir) {
        try (var stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best-effort cleanup
                }
            });
        } catch (IOException ignored) {
            // best-effort cleanup
        }
    }
}
