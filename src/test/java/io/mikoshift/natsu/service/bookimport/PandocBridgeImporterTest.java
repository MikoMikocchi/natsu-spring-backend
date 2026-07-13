package io.mikoshift.natsu.service.bookimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.mikoshift.natsu.config.NatsuProperties;
import io.mikoshift.natsu.entity.Document.SourceFormat;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PandocBridgeImporterTest {

    private EpubImporter epubImporter;

    @BeforeEach
    void setUp() {
        NatsuProperties.RateLimit.Bucket bucket = new NatsuProperties.RateLimit.Bucket(5, 60);
        NatsuProperties.RateLimit rateLimit =
                new NatsuProperties.RateLimit(bucket, bucket, bucket, bucket, bucket, bucket);
        epubImporter = new EpubImporter(new NatsuProperties(
                "/tmp/natsu-test",
                52_428_800L,
                524_288_000L,
                List.of("*"),
                List.of(),
                rateLimit,
                "http://localhost:3000/reset-password?token={token}",
                "noreply@example.com",
                new NatsuProperties.Auth(Duration.ofHours(1), Duration.ofDays(365), Duration.ofSeconds(30), Duration.ofHours(2)),
                new NatsuProperties.BookImportRecovery(true, 15, 5, 3)));
    }

    @Nested
    class PandocCliRunnerTests {

        @Test
        void rejectsUnavailableExecutable() {
            List<String> command = List.of("natsu-nonexistent-pandoc-stub");

            assertThatThrownBy(() -> PandocCliRunner.run(command, Duration.ofSeconds(5), "DOCX"))
                    .isInstanceOf(ImportException.class)
                    .hasMessageContaining("Pandoc is not available on this server")
                    .hasMessageContaining("DOCX import is currently unsupported");
        }

        @Test
        void rejectsNonZeroExitCode() {
            List<String> command = ShellCommands.failingCommand();

            assertThatThrownBy(() -> PandocCliRunner.run(command, Duration.ofSeconds(5), "RTF"))
                    .isInstanceOf(ImportException.class)
                    .hasMessage("Could not convert RTF file: malformed or unsupported document");
        }

        @Test
        void rejectsTimedOutProcess() {
            List<String> command = ShellCommands.longRunningCommand();

            assertThatThrownBy(() -> PandocCliRunner.run(command, Duration.ofSeconds(1), "DOCX"))
                    .isInstanceOf(ImportException.class)
                    .hasMessage("Pandoc conversion timed out");
        }

        @Test
        void rejectsInterruptedWait() throws Exception {
            List<String> command = ShellCommands.longRunningCommand();
            AtomicReference<Throwable> failure = new AtomicReference<>();
            Thread runner = new Thread(() -> {
                try {
                    PandocCliRunner.run(command, Duration.ofMinutes(2), "DOCX");
                } catch (Throwable t) {
                    failure.set(t);
                }
            });

            runner.start();
            runner.join(1_000);
            runner.interrupt();
            runner.join(10_000);

            assertThat(failure.get()).isInstanceOf(TransientImportException.class);
            assertThat(failure.get()).hasMessage("Interrupted while waiting for pandoc");
        }

        @Test
        void acceptsZeroExitCode() {
            List<String> command = ShellCommands.successfulNoOpCommand();

            PandocCliRunner.run(command, Duration.ofSeconds(5), "DOCX");
        }
    }

    @Nested
    class BridgeOrchestrationTests {

        @Test
        void passesSandboxFlagAndPathsToRunner() {
            List<List<String>> capturedCommands = new ArrayList<>();
            TestPandocImporter importer = new TestPandocImporter(
                    epubImporter,
                    "docx",
                    (command, timeout, formatLabel) -> {
                        capturedCommands.add(command);
                        writeEpubOutput(command, minimalEpub("Ch", "Body."));
                    },
                    Duration.ofSeconds(30));

            importer.importFrom("fake docx bytes".getBytes(StandardCharsets.UTF_8));

            assertThat(capturedCommands).hasSize(1);
            List<String> command = capturedCommands.getFirst();
            assertThat(command)
                    .containsExactly("pandoc", "--sandbox", "--standalone", command.get(3), "-o", command.get(5));
            assertThat(command.get(3)).endsWith("input.docx");
            assertThat(command.get(5)).endsWith("output.epub");
        }

        @Test
        void usesFormatLabelFromSourceExtension() {
            AtomicReference<String> capturedLabel = new AtomicReference<>();
            TestPandocImporter importer = new TestPandocImporter(
                    epubImporter,
                    "rtf",
                    (command, timeout, formatLabel) -> {
                        capturedLabel.set(formatLabel);
                        throw new ImportException("stop after capture");
                    },
                    Duration.ofSeconds(30));

            assertThatThrownBy(() -> importer.importFrom(new byte[] {1, 2, 3})).isInstanceOf(ImportException.class);

            assertThat(capturedLabel.get()).isEqualTo("RTF");
        }

        @Test
        void delegatesConvertedEpubToEpubImporter() {
            byte[] epub = minimalEpub("Converted Chapter", "Converted body.");
            TestPandocImporter importer = new TestPandocImporter(
                    epubImporter,
                    "docx",
                    (command, timeout, formatLabel) -> writeEpubOutput(command, epub),
                    Duration.ofSeconds(30));

            ImportedBook book = importer.importFrom("source".getBytes(StandardCharsets.UTF_8));

            assertThat(book.title()).isEqualTo("Converted Book");
            assertThat(book.sections()).hasSize(1);
            assertThat(book.sections().getFirst().title()).isEqualTo("Converted Chapter");
        }

        @Test
        void rejectsWhenRunnerSucceedsButProducesNoOutputFile() {
            TestPandocImporter importer = new TestPandocImporter(
                    epubImporter, "docx", (command, timeout, formatLabel) -> {}, Duration.ofSeconds(30));

            assertThatThrownBy(() -> importer.importFrom(new byte[] {1}))
                    .isInstanceOf(ImportException.class)
                    .hasMessage("DOCX conversion produced no output");
        }

        @Test
        void deletesTempWorkDirectoryAfterImport() {
            List<Path> inputPaths = new ArrayList<>();
            TestPandocImporter importer = new TestPandocImporter(
                    epubImporter,
                    "docx",
                    (command, timeout, formatLabel) -> {
                        inputPaths.add(Path.of(command.get(3)));
                        writeEpubOutput(command, minimalEpub("Ch", "Body."));
                    },
                    Duration.ofSeconds(30));

            importer.importFrom(new byte[] {1});

            assertThat(inputPaths).hasSize(1);
            Path workDir = inputPaths.getFirst().getParent();
            assertThat(Files.exists(workDir)).isFalse();
        }

        @Test
        void docxImporterUsesDocxExtension() {
            DocxImporter importer = new DocxImporter(epubImporter);
            assertThat(importer.supportedFormat()).isEqualTo(SourceFormat.DOCX);
        }

        @Test
        void rtfImporterUsesRtfExtension() {
            RtfImporter importer = new RtfImporter(epubImporter);
            assertThat(importer.supportedFormat()).isEqualTo(SourceFormat.RTF);
        }
    }

    private static void writeEpubOutput(List<String> command, byte[] epubBytes) {
        Path output = Path.of(command.get(5));
        try {
            Files.write(output, epubBytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] minimalEpub(String chapterTitle, String paragraph) {
        return buildEpub(Map.of(
                "META-INF/container.xml", containerXml("OEBPS/content.opf"),
                "OEBPS/content.opf", """
                        <?xml version="1.0"?>
                        <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                          <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                            <dc:title>Converted Book</dc:title>
                          </metadata>
                          <manifest>
                            <item id="ch1" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
                          </manifest>
                          <spine>
                            <itemref idref="ch1"/>
                          </spine>
                        </package>
                        """,
                "OEBPS/chapter1.xhtml", """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <html xmlns="http://www.w3.org/1999/xhtml">
                        <head><title>%s</title></head>
                        <body><h1>%s</h1><p>%s</p></body>
                        </html>
                        """.formatted(chapterTitle, chapterTitle, paragraph)));
    }

    private static String containerXml(String opfPath) {
        return """
                <?xml version="1.0"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles>
                    <rootfile full-path="%s" media-type="application/oebps-package+xml"/>
                  </rootfiles>
                </container>
                """.formatted(opfPath);
    }

    private static byte[] buildEpub(Map<String, String> entries) {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(buffer, StandardCharsets.UTF_8)) {
                for (Map.Entry<String, String> entry : entries.entrySet()) {
                    zip.putNextEntry(new ZipEntry(entry.getKey()));
                    zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                    zip.closeEntry();
                }
            }
            return buffer.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static final class TestPandocImporter extends PandocBridgeImporter {

        private final String extension;

        TestPandocImporter(EpubImporter epubImporter, String extension, PandocRunner pandocRunner, Duration timeout) {
            super(epubImporter, pandocRunner, timeout);
            this.extension = extension;
        }

        @Override
        protected String sourceExtension() {
            return extension;
        }

        @Override
        public SourceFormat supportedFormat() {
            return SourceFormat.DOCX;
        }
    }

    private static final class ShellCommands {

        private static boolean isWindows() {
            return System.getProperty("os.name").toLowerCase().contains("win");
        }

        private static List<String> longRunningCommand() {
            if (isWindows()) {
                return List.of("powershell", "-NoProfile", "-Command", "Start-Sleep -Seconds 30");
            }
            return List.of("sleep", "30");
        }

        private static List<String> failingCommand() {
            if (isWindows()) {
                return List.of("cmd", "/c", "exit", "1");
            }
            return List.of("false");
        }

        private static List<String> successfulNoOpCommand() {
            if (isWindows()) {
                return List.of("cmd", "/c", "exit", "0");
            }
            return List.of("true");
        }
    }
}
