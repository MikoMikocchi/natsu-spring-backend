package io.mikoshift.natsu.service.bookimport;

import io.mikoshift.natsu.entity.Document.SourceFormat;
import org.springframework.stereotype.Component;

@Component
public class RtfImporter extends PandocBridgeImporter {

    public RtfImporter(EpubImporter epubImporter) {
        super(epubImporter);
    }

    @Override
    public SourceFormat supportedFormat() {
        return SourceFormat.RTF;
    }

    @Override
    protected String sourceExtension() {
        return "rtf";
    }
}
