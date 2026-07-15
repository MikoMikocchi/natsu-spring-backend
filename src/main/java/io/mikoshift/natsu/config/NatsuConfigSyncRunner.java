package io.mikoshift.natsu.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Mirrors selected {@link NatsuProperties} values into {@code natsu_config} for DB-level enforcement. */
@Component
@RequiredArgsConstructor
class NatsuConfigSyncRunner implements CommandLineRunner {

    static final String MAX_STORAGE_BYTES_PER_USER_KEY = "max_storage_bytes_per_user";

    private final JdbcTemplate jdbcTemplate;
    private final NatsuProperties properties;

    @Override
    public void run(String... args) {
        jdbcTemplate.update("""
                insert into natsu_config (key, value)
                values (?, ?)
                on conflict (key) do update set value = excluded.value
                """, MAX_STORAGE_BYTES_PER_USER_KEY, properties.maxStorageBytesPerUser());
    }
}
