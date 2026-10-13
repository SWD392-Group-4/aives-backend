package com.aives.common.config;

import java.util.Map;
import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Doi ten mui gio cu (alias) cua JVM sang ten chuan, chay TRUOC khi tao bat ky bean nao (ca khi chay test).
 *
 * Driver PostgreSQL gui ten mui gio cua JVM len server luc ket noi. Mot so ban PostgreSQL khong nhan ten cu
 * (vd "FATAL: invalid value for parameter TimeZone: Asia/Saigon") nen backend khong khoi dong duoc.
 * Ten moi cung do lech gio nen khong lam doi gio da luu.
 *
 * Dang ky trong src/main/resources/META-INF/spring.factories.
 */
public class TimeZoneNormalizer implements EnvironmentPostProcessor {

    static final Map<String, String> LEGACY_TIME_ZONES = Map.of(
            "Asia/Saigon", "Asia/Ho_Chi_Minh",
            "Asia/Calcutta", "Asia/Kolkata",
            "Asia/Rangoon", "Asia/Yangon",
            "Asia/Katmandu", "Asia/Kathmandu");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String canonical = LEGACY_TIME_ZONES.get(TimeZone.getDefault().getID());
        if (canonical != null) {
            TimeZone.setDefault(TimeZone.getTimeZone(canonical));
        }
    }
}
