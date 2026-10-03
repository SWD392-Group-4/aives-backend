package com.aives.common.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Dong ho dung chung cho nghiep vu phu thuoc thoi gian (phien thi, dem nguoc).
 * Service lay "bay gio" tu bean nay thay vi goi LocalDateTime.now() truc tiep,
 * nho do test co the thay bang dong ho gia de tua thoi gian.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
