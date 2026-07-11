package io.mikoshift.natsu.service.bookimport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

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
public abstract class PandocBridgeImporter implements BookImporter {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private final EpubImporter epubImporter;
    private final PandocRunner pandocRunner;
    private final Duration timeout;

    protected PandocBridgeImporter(EpubImporter epubImporter) {
        this(epubImporter, PandocRunner.DEFAULT, DEFAULT_TIMEOUT);
    }

    PandocBridgeImporter(EpubImporter epubImporter, PandocRunner pandocRunner, Duration timeout) {
        this.epubImporter = epubImporter;
        this.pandocRunner = pandocRunner;
        this.timeout = timeout;
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
        List<String> command = List.of(
                "pandoc", "--sandbox", "--standalone", input.toString(), "-o", output.toString());
        pandocRunner.run(command, timeout, labelUpper());
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
