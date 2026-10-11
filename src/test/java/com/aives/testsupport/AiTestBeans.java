package com.aives.testsupport;

import java.time.Instant;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Bean dung chung cho cac test co AI: dong ho gia va "AI" gia. Dung: @Import(AiTestBeans.class). */
@TestConfiguration
public class AiTestBeans {

    @Bean
    @Primary
    public MutableClock testClock() {
        return new MutableClock(Instant.parse("2027-01-01T01:00:00Z"));
    }

    @Bean
    @Primary
    public ScriptedLlmClient scriptedLlmClient() {
        return new ScriptedLlmClient();
    }
}
