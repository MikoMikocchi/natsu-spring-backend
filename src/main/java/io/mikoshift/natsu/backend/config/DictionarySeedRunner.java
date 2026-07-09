package io.mikoshift.natsu.backend.config;

import io.mikoshift.natsu.backend.entity.Dictionary;
import io.mikoshift.natsu.backend.service.dictionary.TermBankImportService;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Seeds the dictionary catalog from a Yomitan archive on disk. Only active under the {@code seed}
 * profile, so it never runs as part of normal request-serving startup:
 * {@code ./mvnw spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=seed --seed.file=/path/to/dict.zip"}.
 */
@Component
@Profile("seed")
@RequiredArgsConstructor
@Slf4j
public class DictionarySeedRunner implements CommandLineRunner {

    private final TermBankImportService termBankImportService;

    @Value("${seed.catalog-id:}")
    private String catalogId;

    @Value("${seed.file:}")
    private String filePath;

    @Override
    public void run(String... args) throws Exception {
        if (filePath.isBlank()) {
            log.warn("seed profile is active but no --seed.file was provided; skipping dictionary seed");
            return;
        }
        byte[] bytes = Files.readAllBytes(Path.of(filePath));
        String effectiveCatalogId =
                catalogId.isBlank() ? Path.of(filePath).getFileName().toString().replaceFirst("\\.zip$", "") : catalogId;

        Dictionary dictionary = termBankImportService.importZip(effectiveCatalogId, bytes);
        log.info("Seeded dictionary '{}' ({} terms) from {}", dictionary.getTitle(), dictionary.getTermCount(), filePath);
    }
}
