package io.mikoshift.natsu.service.bookimport;

import io.mikoshift.natsu.entity.Document.SourceFormat;
import io.mikoshift.natsu.service.bookimport.PackageManifest.ManifestSection;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Builds the on-disk package format: a zip of manifest.json + one HTML file per section. */
@Component
@RequiredArgsConstructor
public class PackageBuilder {

    private final ObjectMapper objectMapper;

    public byte[] buildZip(String title, SourceFormat sourceFormat, List<ImportedSection> sections) {
        List<ManifestSection> manifestSections = new ArrayList<>();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer, StandardCharsets.UTF_8)) {
            for (ImportedSection section : sections) {
                String path = "sections/" + section.id() + ".html";
                zip.putNextEntry(new ZipEntry(path));
                zip.write(section.html().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
                manifestSections.add(new ManifestSection(section.id(), section.title(), path));
            }

            PackageManifest manifest = new PackageManifest(1, title, sourceFormat, manifestSections);
            zip.putNextEntry(new ZipEntry("manifest.json"));
            zip.write(objectMapper.writeValueAsBytes(manifest));
            zip.closeEntry();
        } catch (IOException e) {
            throw new ImportException("Failed to build package archive", e);
        }
        return buffer.toByteArray();
    }

    public String extractPlainText(List<ImportedSection> sections) {
        StringBuilder text = new StringBuilder();
        for (ImportedSection section : sections) {
            text.append(Jsoup.parse(section.html()).text()).append('\n');
        }
        return text.toString().strip();
    }
}
