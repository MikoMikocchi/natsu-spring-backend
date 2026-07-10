package io.mikoshift.natsu.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "reader_settings")
@Getter
@Setter
@NoArgsConstructor
public class ReaderSetting {

  @Id
  @Column(name = "user_id")
  private Long userId;

  @OneToOne(fetch = FetchType.EAGER)
  @MapsId
  @JoinColumn(name = "user_id")
  private User user;

  @Column(name = "font_size_sp", nullable = false)
  private double fontSizeSp = 16.0;

  @Column(name = "line_spacing_multiplier", nullable = false)
  private double lineSpacingMultiplier = 1.8;

  @Enumerated(EnumType.STRING)
  @Column(name = "theme", nullable = false)
  private Theme theme = Theme.LIGHT;

  @Enumerated(EnumType.STRING)
  @Column(name = "furigana_mode", nullable = false)
  private FuriganaMode furiganaMode = FuriganaMode.OFF;

  /**
   * Client-authoritative logical clock used to resolve update conflicts; not a JPA-managed
   * timestamp.
   */
  @Column(name = "updated_at_ms", nullable = false)
  private long updatedAtMs = 0L;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public enum Theme {
    LIGHT,
    DARK,
    SEPIA
  }

  public enum FuriganaMode {
    OFF,
    ALWAYS
  }
}
