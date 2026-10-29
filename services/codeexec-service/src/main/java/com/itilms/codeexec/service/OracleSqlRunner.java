package com.itilms.codeexec.service;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import com.itilms.codeexec.config.CodeExecProperties;
import com.itilms.codeexec.exception.CodeRunnerUnavailableException;
import com.itilms.codeexec.service.CodeRunner.Outcome;
import com.itilms.codeexec.service.CodeRunner.RunResult;

import lombok.extern.slf4j.Slf4j;

/**
 * Runs a SQL script on Oracle. Every run gets a throwaway database user: the script builds its tables in that
 * user's own schema, so one student's tables are never seen by another, and the user is dropped afterwards with
 * everything in it.
 *
 * <p>What comes out is what SQLite's command line printed before, so expected outputs stay simple: one line per row,
 * columns joined by {@code |}, no header, a NULL as nothing. Text printed with {@code DBMS_OUTPUT} comes out too.
 */
@Slf4j
public class OracleSqlRunner {

    private static final String USER_PREFIX = "ITILMS_RUN_";
    private static final Duration SWEEP_EVERY = Duration.ofHours(1);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_ONLY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final CodeExecProperties properties;
    private final SecureRandom random = new SecureRandom();
    private final AtomicLong lastSweep = new AtomicLong();

    public OracleSqlRunner(CodeExecProperties properties) {
        this.properties = properties;
        DriverManager.setLoginTimeout(5);
        warmUpInBackground();
    }

    /**
     * The first user an Oracle creates after it starts takes ten seconds or more, which would be a trainer's or
     * student's first run. Doing one now, off the request path, spends that time here. Oracle may still be starting,
     * so it is tried for a few minutes.
     */
    private void warmUpInBackground() {
        Thread warmUp = new Thread(() -> {
            for (int attempt = 0; attempt < 20; attempt++) {
                try {
                    run("SELECT 1 FROM dual");
                    log.info("Oracle is warm");
                    return;
                } catch (RuntimeException e) {
                    try {
                        Thread.sleep(15_000);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }, "oracle-warm-up");
        warmUp.setDaemon(true);
        warmUp.start();
    }

    public RunResult run(String script) {
        CodeExecProperties.Oracle oracle = properties.getOracle();
        String user = USER_PREFIX + HexFormat.of().withUpperCase().formatHex(randomBytes(6));
        String password = "Pw" + HexFormat.of().formatHex(randomBytes(12)) + "#1";

        try (Connection admin = DriverManager.getConnection(oracle.getUrl(), oracle.getAdminUser(), oracle.getAdminPassword())) {
            sweepLeftovers(admin);
            try {
                try (Statement st = admin.createStatement()) {
                    st.execute("CREATE USER " + user + " IDENTIFIED BY \"" + password + "\" DEFAULT TABLESPACE " + tablespace(oracle) + " QUOTA 50M ON " + tablespace(oracle));
                    st.execute("GRANT CREATE SESSION, CREATE TABLE, CREATE VIEW, CREATE SEQUENCE, CREATE PROCEDURE, "
                            + "CREATE TRIGGER, CREATE TYPE, CREATE SYNONYM TO " + user);
                }
                // The clock starts here: making the user is our own time, not the script's.
                return runAs(oracle.getUrl(), user, password, script, System.nanoTime());
            } finally {
                drop(admin, user);
            }
        } catch (SQLException e) {
            log.warn("Oracle is not usable for a run: {}", e.getMessage());
            throw new CodeRunnerUnavailableException("The SQL database is not reachable. Try again in a moment.");
        }
    }

    /** Only letters, digits and underscores reach the CREATE USER statement. */
    private static String tablespace(CodeExecProperties.Oracle oracle) {
        String name = oracle.getTablespace();
        if (name == null || !name.matches("[A-Za-z][A-Za-z0-9_]*")) {
            throw new CodeRunnerUnavailableException("The SQL database is set up wrongly (tablespace name).");
        }
        return name;
    }

    /** Is the Oracle server there and does the admin account work? */
    public String check() {
        CodeExecProperties.Oracle oracle = properties.getOracle();
        try (Connection admin = DriverManager.getConnection(oracle.getUrl(), oracle.getAdminUser(), oracle.getAdminPassword())) {
            return admin.isValid(3) ? null : "Oracle did not answer.";
        } catch (SQLException e) {
            return "Oracle is configured but not reachable: " + e.getMessage();
        }
    }

    private RunResult runAs(String url, String user, String password, String script, long started) throws SQLException {
        int limit = properties.getLimits().getMaxOutputChars();
        long deadline = started + Duration.ofSeconds(properties.getLimits().getWallTimeSeconds()).toNanos();
        StringBuilder out = new StringBuilder();

        try (Connection c = DriverManager.getConnection(url, user, password)) {
            try (CallableStatement enable = c.prepareCall("begin dbms_output.enable(null); end;")) {
                enable.execute();
            }
            for (String sql : SqlScript.statements(script)) {
                if (System.nanoTime() > deadline) {
                    return result(Outcome.TIME_LIMIT_EXCEEDED, "Time Limit Exceeded", out, null, started);
                }
                try (Statement st = c.createStatement()) {
                    st.setQueryTimeout(properties.getLimits().getWallTimeSeconds());
                    if (st.execute(sql)) {
                        try (ResultSet rs = st.getResultSet()) {
                            appendRows(rs, out, limit);
                        }
                    }
                } catch (SQLTimeoutException e) {
                    return result(Outcome.TIME_LIMIT_EXCEEDED, "Time Limit Exceeded", out, null, started);
                } catch (SQLException e) {
                    return result(Outcome.RUNTIME_ERROR, "SQL Error", out, firstLine(e.getMessage()), started);
                }
                drainDbmsOutput(c, out);
                if (out.length() > limit) {
                    return result(Outcome.RUNTIME_ERROR, "Output Limit Exceeded", out.delete(limit, out.length()),
                            "The script printed too much, so it was stopped.", started);
                }
            }
        }
        return result(Outcome.SUCCESS, "Accepted", out, null, started);
    }

    private static RunResult result(Outcome outcome, String description, StringBuilder out, String stderr, long started) {
        double seconds = (System.nanoTime() - started) / 1_000_000_000.0;
        return new RunResult(outcome, description, out.toString(), stderr, null, null, seconds, null);
    }

    private static void appendRows(ResultSet rs, StringBuilder out, int limit) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int columns = meta.getColumnCount();
        while (rs.next() && out.length() <= limit) {
            for (int i = 1; i <= columns; i++) {
                if (i > 1) {
                    out.append('|');
                }
                out.append(cell(rs, i));
            }
            out.append('\n');
        }
    }

    private static String cell(ResultSet rs, int column) throws SQLException {
        int type = rs.getMetaData().getColumnType(column);
        if (type == Types.CLOB || type == Types.NCLOB) {
            Clob clob = rs.getClob(column);
            return clob == null ? "" : clob.getSubString(1, (int) Math.min(clob.length(), 10_000));
        }
        Object value = rs.getObject(column);
        if (value == null) {
            return "";
        }
        if (value instanceof BigDecimal number) {
            // 1500.00 and 1.5E+3 both come out as 1500: Oracle keeps no trailing zeros for a NUMBER.
            return number.signum() == 0 ? "0" : number.stripTrailingZeros().toPlainString();
        }
        if (value instanceof Timestamp || value instanceof java.sql.Date) {
            LocalDateTime time = value instanceof Timestamp t ? t.toLocalDateTime() : ((java.sql.Date) value).toLocalDate().atStartOfDay();
            return time.toLocalTime().toSecondOfDay() == 0 && time.getNano() == 0 ? DATE_ONLY.format(time) : DATE_TIME.format(time);
        }
        String text = rs.getString(column);
        return text == null ? "" : text;
    }

    /** What the script printed with DBMS_OUTPUT.PUT_LINE, one line each. */
    private static void drainDbmsOutput(Connection c, StringBuilder out) throws SQLException {
        try (CallableStatement get = c.prepareCall("begin dbms_output.get_line(?, ?); end;")) {
            get.registerOutParameter(1, Types.VARCHAR);
            get.registerOutParameter(2, Types.INTEGER);
            while (true) {
                get.execute();
                if (get.getInt(2) != 0) {
                    return;
                }
                String line = get.getString(1);
                out.append(line == null ? "" : line).append('\n');
            }
        }
    }

    private static void drop(Connection admin, String user) {
        try (Statement st = admin.createStatement()) {
            st.execute("DROP USER " + user + " CASCADE");
        } catch (SQLException e) {
            log.warn("Could not drop the throwaway user {}: {}", user, e.getMessage());
        }
    }

    /** A crash between create and drop would leave a user behind for good, so old ones are removed now and then. */
    private void sweepLeftovers(Connection admin) {
        long now = Instant.now().toEpochMilli();
        long last = lastSweep.get();
        if (now - last < SWEEP_EVERY.toMillis() || !lastSweep.compareAndSet(last, now)) {
            return;
        }
        try (Statement st = admin.createStatement();
             ResultSet rs = st.executeQuery("SELECT username FROM dba_users WHERE username LIKE '" + USER_PREFIX
                     + "%' AND created < SYSDATE - 1/24")) {
            List<String> stale = new java.util.ArrayList<>();
            while (rs.next()) {
                stale.add(rs.getString(1));
            }
            stale.forEach(user -> drop(admin, user));
        } catch (SQLException e) {
            log.warn("Could not look for leftover throwaway users: {}", e.getMessage());
        }
    }

    private byte[] randomBytes(int count) {
        byte[] bytes = new byte[count];
        random.nextBytes(bytes);
        return bytes;
    }

    private static String firstLine(String message) {
        if (message == null) {
            return "SQL error";
        }
        String stripped = message.strip();
        int end = stripped.indexOf('\n');
        return end < 0 ? stripped : stripped.substring(0, end);
    }
}
