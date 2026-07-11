package io.mikoshift.natsu.service;

import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class ServerTimeService {

    public long nowMs() {
        return Instant.now().toEpochMilli();
    }
}
