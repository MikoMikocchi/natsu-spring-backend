package io.mikoshift.natsu.service.bookimport;

import io.mikoshift.natsu.entity.Document.SourceFormat;
import org.springframework.stereotype.Component;

@Component
public class DocxImporter extends PandocBridgeImporter {

    public DocxImporter(EpubImporter epubImporter) {
        super(epubImporter);
    }

    @Override
    public SourceFormat supportedFormat() {
        return SourceFormat.DOCX;
    }

    @Override
    protected String sourceExtension() {
        return "docx";
    }
}
