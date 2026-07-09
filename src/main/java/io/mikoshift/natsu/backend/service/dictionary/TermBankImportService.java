package io.mikoshift.natsu.backend.service.dictionary;

import io.mikoshift.natsu.backend.entity.Dictionary;
import io.mikoshift.natsu.backend.entity.DictionaryTerm;
import io.mikoshift.natsu.backend.repository.DictionaryRepository;
import io.mikoshift.natsu.backend.repository.DictionaryTermRepository;
import io.mikoshift.natsu.backend.util.ZipUtils;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Imports a Yomitan-format dictionary archive (index.json + term_bank_N.json files, each entry
 * {@code [expression, reading, definitionTags, rules, score, glosses, sequence, termTags]}) into
 * the server-side catalog. This is a seed-time operation (see {@code DictionarySeedRunner}), not
 * a public upload endpoint -- dictionary catalog content is server-curated.
 */
@Service
@RequiredArgsConstructor
public class TermBankImportService {

    private static final int BATCH_SIZE = 500;

    private final DictionaryRepository dictionaryRepository;
    private final DictionaryTermRepository dictionaryTermRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public Dictionary importZip(String catalogId, byte[] zipBytes) {
        Map<String, byte[]> entries;
        try {
            entries = ZipUtils.readEntries(zipBytes);
        } catch (UncheckedIOException e) {
            throw new TermBankImportException("Dictionary archive is not a valid zip file", e);
        }

        byte[] indexBytes = entries.get("index.json");
        if (indexBytes == null) {
            throw new TermBankImportException("Dictionary archive is missing index.json");
        }
        JsonNode index = objectMapper.readTree(indexBytes);

        Dictionary dictionary = dictionaryRepository.findByCatalogId(catalogId).orElseGet(() -> {
            Dictionary created = new Dictionary();
            created.setId(UUID.randomUUID());
            created.setCatalogId(catalogId);
            return created;
        });
        dictionary.setTitle(index.path("title").asString(catalogId));
        dictionary.setRevision(index.path("revision").asString("1"));
        dictionary = dictionaryRepository.save(dictionary);

        int total = 0;
        List<DictionaryTerm> batch = new ArrayList<>(BATCH_SIZE);
        for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
            if (!entry.getKey().matches("term_bank_\\d+\\.json")) {
                continue;
            }
            for (JsonNode termEntry : objectMapper.readTree(entry.getValue())) {
                batch.add(toTerm(dictionary, termEntry));
                total++;
                if (batch.size() == BATCH_SIZE) {
                    dictionaryTermRepository.saveAll(batch);
                    batch.clear();
                }
            }
        }
        if (!batch.isEmpty()) {
            dictionaryTermRepository.saveAll(batch);
        }
        if (total == 0) {
            throw new TermBankImportException("Dictionary archive has no term_bank_*.json files with entries");
        }

        dictionary.setTermCount(total);
        return dictionaryRepository.save(dictionary);
    }

    private DictionaryTerm toTerm(Dictionary dictionary, JsonNode entry) {
        DictionaryTerm term = new DictionaryTerm();
        term.setDictionary(dictionary);
        term.setExpression(entry.get(0).asString());
        term.setReading(entry.get(1).asString());
        term.setRuleTags(entry.path(3).asString(""));
        term.setScore(entry.path(4).asInt(0));
        term.setGlossesJson(objectMapper.writeValueAsString(extractGlosses(entry.get(5))));
        return term;
    }

    private List<String> extractGlosses(JsonNode glossesNode) {
        List<String> glosses = new ArrayList<>();
        if (glossesNode != null && glossesNode.isArray()) {
            for (JsonNode gloss : glossesNode) {
                glosses.add(gloss.isString() ? gloss.asString() : gloss.toString());
            }
        }
        return glosses;
    }
}
