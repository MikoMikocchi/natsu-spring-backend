package io.mikoshift.natsu.service.bookimport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;

/** Runs a pandoc CLI command with timeout and exit-code handling. Package-private for unit testing. */
@Slf4j
final class PandocCliRunner {

    private static final ExecutorService OUTPUT_READER = Executors.newVirtualThreadPerTaskExecutor();

    private PandocCliRunner() {}

    static void run(List<String> command, Duration timeout, String formatLabel) {
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        Process process;
        try {
            process = builder.start();
        } catch (IOException e) {
            throw new ImportException(
                    "Pandoc is not available on this server; " + formatLabel + " import is currently unsupported",
                    e);
        }

        Future<String> outputFuture =
                OUTPUT_READER.submit(() -> new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8));

        boolean finished;
        try {
            finished = process.waitFor(timeout.toSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            outputFuture.cancel(true);
            throw new TransientImportException("Interrupted while waiting for pandoc", e);
        }
        if (!finished) {
            process.destroyForcibly();
            outputFuture.cancel(true);
            throw new ImportException("Pandoc conversion timed out");
        }

        String processOutput;
        try {
            processOutput = outputFuture.get(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TransientImportException("Interrupted while reading pandoc output", e);
        } catch (ExecutionException e) {
            throw new ImportException("Failed to read pandoc output", e.getCause());
        } catch (TimeoutException e) {
            throw new ImportException("Failed to read pandoc output", e);
        }

        if (process.exitValue() != 0) {
            log.warn("Pandoc conversion failed (exit {}): {}", process.exitValue(), processOutput);
            throw new ImportException(
                    "Could not convert " + formatLabel + " file: malformed or unsupported document");
        }
    }
}
