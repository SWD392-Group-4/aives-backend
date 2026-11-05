package com.aives.common.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.logging.Log;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Tu tao database PostgreSQL (vd "aives") va cac bang khi backend khoi dong, neu chua co.
 *
 * Chay TRUOC khi tao DataSource / Hibernate:
 *   1. Ket noi database "postgres" (database mac dinh), tao database trong DB_URL neu chua ton tai.
 *   2. Ket noi database do, neu chua co bang "roles" thi chay script db/postgres/00_init_aives.sql
 *      (ban sao cua database/postgres/00_init_aives.sql) de co du rang buoc CHECK, index.
 * Database / bang da co thi bo qua, khong xoa du lieu. Loi (sai mat khau, PostgreSQL chua chay...)
 * chi ghi canh bao; loi ket noi that se hien ro khi Hibernate khoi dong.
 *
 * Chi bat khi app.db.auto-create = true (dat trong application-postgres.yml).
 * Dang ky trong src/main/resources/META-INF/spring.factories.
 */
public class PostgresDatabaseCreator implements EnvironmentPostProcessor, Ordered {

    static final String INIT_SCRIPT = "db/postgres/00_init_aives.sql";
    /** Cap nhat cau truc Mon hoc / Chuong / Ngan hang cau hoi. Viet dang IF NOT EXISTS nen chay lai moi lan khoi dong. */
    static final String MIGRATION_SCRIPT = "db/postgres/01_course_rubric_question_bank.sql";

    private static final Pattern POSTGRES_URL =
            Pattern.compile("^jdbc:postgresql://([^/?]+)/([^?/]+)(\\?.*)?$");
    private static final Pattern SAFE_DB_NAME = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]{0,62}$");

    private final Log log;

    public PostgresDatabaseCreator(DeferredLogFactory logFactory) {
        this.log = logFactory.getLog(PostgresDatabaseCreator.class);
    }

    /** Chay sau khi Spring da doc application*.yml va bien moi truong. */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!environment.getProperty("app.db.auto-create", Boolean.class, false)) {
            return;
        }
        String url = environment.getProperty("spring.datasource.url");
        if (url == null) {
            return;
        }
        Matcher m = POSTGRES_URL.matcher(url.trim());
        if (!m.matches()) {
            return;
        }
        String hosts = m.group(1);
        String database = m.group(2);
        String params = m.group(3) == null ? "" : m.group(3);
        if (!SAFE_DB_NAME.matcher(database).matches()) {
            log.warn("Bo qua tu tao database: ten database '" + database + "' chua ky tu dac biet.");
            return;
        }
        String username = environment.getProperty("spring.datasource.username");
        String password = environment.getProperty("spring.datasource.password");

        // Driver PostgreSQL tu choi ten mui gio cu (Asia/Saigon): doi truoc khi mo ket noi
        new TimeZoneNormalizer().postProcessEnvironment(environment, application);

        try {
            createDatabaseIfMissing("jdbc:postgresql://" + hosts + "/postgres" + params, database, username, password);
            createTablesIfMissing(url, username, password);
        } catch (SQLException | IOException ex) {
            log.warn("Khong tu tao duoc database '" + database + "': " + ex.getMessage()
                    + " (kiem tra PostgreSQL da chay va DB_USERNAME / DB_PASSWORD trong file .env)");
        }
    }

    private void createDatabaseIfMissing(String adminUrl, String database, String username, String password)
            throws SQLException {
        try (Connection conn = DriverManager.getConnection(adminUrl, username, password)) {
            try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM pg_database WHERE datname = ?")) {
                ps.setString(1, database);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return;
                    }
                }
            }
            try (Statement st = conn.createStatement()) {
                // template0 + UTF8: luu duoc tieng Viet, khong phu thuoc encoding cua template1 tren Windows
                st.execute("CREATE DATABASE \"" + database + "\" TEMPLATE template0 ENCODING 'UTF8'");
            }
            log.info("Da tao database PostgreSQL '" + database + "'.");
        }
    }

    private void createTablesIfMissing(String url, String username, String password)
            throws SQLException, IOException {
        try (Connection conn = DriverManager.getConnection(url, username, password)) {
            boolean hasTables;
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT to_regclass('public.roles') IS NOT NULL")) {
                rs.next();
                hasTables = rs.getBoolean(1);
            }
            if (!hasTables) {
                runScript(conn, INIT_SCRIPT);
                log.info("Da tao bang tu script " + INIT_SCRIPT + ".");
            }
            try {
                runScript(conn, MIGRATION_SCRIPT);
                log.info("Da cap nhat cau truc bang tu script " + MIGRATION_SCRIPT + ".");
            } catch (SQLException ex) {
                log.warn("Khong chay duoc script " + MIGRATION_SCRIPT + ": " + ex.getMessage());
            }
        }
    }

    private void runScript(Connection conn, String resource) throws SQLException, IOException {
        String script = readScript(resource);
        boolean autoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try (Statement st = conn.createStatement()) {
            // Driver PostgreSQL tu tach nhieu cau lenh trong cung mot chuoi (ke ca khoi DO $$ ... $$)
            st.execute(script);
            conn.commit();
        } catch (SQLException ex) {
            conn.rollback();
            throw ex;
        } finally {
            conn.setAutoCommit(autoCommit);
        }
    }

    private String readScript(String resource) throws IOException {
        try (InputStream in = PostgresDatabaseCreator.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("khong tim thay " + resource + " trong classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
