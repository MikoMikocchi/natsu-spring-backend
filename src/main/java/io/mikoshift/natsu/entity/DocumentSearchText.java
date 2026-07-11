package io.mikoshift.natsu.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Holds the extracted plain-text body of a document, kept out of {@link Document} itself so that
 * ordinary document reads (list/get) never pull this potentially multi-megabyte column along with
 * them -- only the search path touches this table.
 */
@Entity
@Table(name = "document_search_text")
@Getter
@Setter
@NoArgsConstructor
public class DocumentSearchText {

    @Id
    @Column(name = "document_id")
    private UUID documentId;

    @Column(name = "search_text")
    private String searchText;

    public DocumentSearchText(UUID documentId, String searchText) {
        this.documentId = documentId;
        this.searchText = searchText;
    }
}
