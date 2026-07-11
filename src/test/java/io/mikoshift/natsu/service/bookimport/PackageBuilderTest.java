package io.mikoshift.natsu.service.bookimport;

import static org.assertj.core.api.Assertions.assertThat;

import io.mikoshift.natsu.entity.Document.SourceFormat;
import io.mikoshift.natsu.util.ZipUtils;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class PackageBuilderTest {

    private final PackageBuilder builder =
            new PackageBuilder(JsonMapper.builder().build());

    @Test
    void buildsAZipWithSchemaV2ManifestSectionsAndAssets() {
        ParagraphBlock paragraph = new ParagraphBlock("section-0-b0", "Hello world.", List.of(), List.of());
        ImageBlock image = new ImageBlock("section-0-b1", "abc123", "a picture");
        ImportedSection section = new ImportedSection("section-0", "Chapter One", List.of(paragraph, image));
        ImportedAsset asset = new ImportedAsset("abc123", "image/png", new byte[] {1, 2, 3});
        TocNode toc = new TocNode("Chapter One", "section-0", List.of());
        ImportedBook book = new ImportedBook(
                "My Book", List.of("Jane Author"), "en", "abc123", List.of(toc), List.of(section), List.of(asset));

        byte[] zip = builder.buildZip("My Book", SourceFormat.EPUB, book);
        Map<String, byte[]> entries = ZipUtils.readEntries(zip);

        assertThat(entries).containsKeys("manifest.json", "sections/section-0.json", "assets/abc123.png");

        PackageManifest manifest =
                JsonMapper.builder().build().readValue(entries.get("manifest.json"), PackageManifest.class);
        assertThat(manifest.schemaVersion()).isEqualTo(2);
        assertThat(manifest.title()).isEqualTo("My Book");
        assertThat(manifest.authors()).containsExactly("Jane Author");
        assertThat(manifest.coverAssetId()).isEqualTo("abc123");
        assertThat(manifest.sections()).hasSize(1);
        // "Hello world." (2) + the image block's alt text "a picture" (2) = 4.
        assertThat(manifest.sections().get(0).wordCount()).isEqualTo(4);
        assertThat(manifest.toc()).hasSize(1);
        assertThat(manifest.toc().get(0).sectionId()).isEqualTo("section-0");
    }

    @Test
    void extractsPlainTextFromBlocksInOrder() {
        ImportedSection section = new ImportedSection(
                "section-0",
                null,
                List.of(
                        new HeadingBlock("b0", 1, "Title", List.of()),
                        new ParagraphBlock("b1", "Body text.", List.of(), List.of())));

        String text = builder.extractPlainText(List.of(section));

        assertThat(text).isEqualTo("Title\nBody text.");
    }

    @Test
    void countsJapaneseTextPerCharacterInManifestWordCount() {
        ParagraphBlock paragraph = new ParagraphBlock("section-0-b0", "私は学生です", List.of(), List.of());
        ImportedSection section = new ImportedSection("section-0", "第一章", List.of(paragraph));
        ImportedBook book = new ImportedBook("本", List.of(), "ja", null, List.of(), List.of(section), List.of());

        byte[] zip = builder.buildZip("本", SourceFormat.EPUB, book);
        PackageManifest manifest = JsonMapper.builder()
                .build()
                .readValue(ZipUtils.readEntries(zip).get("manifest.json"), PackageManifest.class);

        assertThat(manifest.sections().get(0).wordCount()).isEqualTo(6);
    }
}
