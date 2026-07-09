package io.mikoshift.natsu.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "dictionary_terms")
@Getter
@Setter
@NoArgsConstructor
public class DictionaryTerm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dictionary_id", nullable = false)
    private Dictionary dictionary;

    @Column(nullable = false)
    private String expression;

    @Column(nullable = false)
    private String reading;

    /** JSON array of gloss/definition strings. */
    @Column(name = "glosses_json", nullable = false)
    private String glossesJson;

    /** Space-separated part-of-speech tags (e.g. "v1", "v5", "adj-i"); also the deinflection target tags. */
    @Column(name = "rule_tags", nullable = false)
    private String ruleTags = "";

    @Column(nullable = false)
    private int score = 0;
}
