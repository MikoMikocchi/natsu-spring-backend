package io.mikoshift.natsu.service.bookimport;

import java.time.Duration;
import java.util.List;

@FunctionalInterface
interface PandocRunner {

    PandocRunner DEFAULT = PandocCliRunner::run;

    void run(List<String> command, Duration timeout, String formatLabel);
}
