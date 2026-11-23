package com.apps.mindlog.global.config;

import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

class TimeConfigTest {
    @Test void providesOneSeoulClock() {
        try (var context = new AnnotationConfigApplicationContext(TimeConfig.class)) {
            assertThat(context.getBeansOfType(Clock.class)).hasSize(1);
            assertThat(context.getBean(Clock.class).getZone()).isEqualTo(ZoneId.of("Asia/Seoul"));
        }
    }
    @Test void seoulDateChangesAtUtcFifteen() {
        Clock before = Clock.fixed(Instant.parse("2026-09-29T14:59:59Z"), TimeConfig.SEOUL);
        Clock after = Clock.offset(before, Duration.ofSeconds(1));
        assertThat(LocalDate.now(before)).isEqualTo(LocalDate.of(2026, 9, 29));
        assertThat(LocalDate.now(after)).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(OffsetDateTime.now(after).getOffset()).isEqualTo(ZoneOffset.ofHours(9));
    }
}
