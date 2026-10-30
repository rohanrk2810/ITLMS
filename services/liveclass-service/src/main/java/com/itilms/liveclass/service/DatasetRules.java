package com.itilms.liveclass.service;

import java.util.List;

import com.itilms.common.exception.BusinessRuleException;
import com.itilms.liveclass.dto.request.DataTable;

/**
 * How big the tables shown with a question may be. The limits are far above any lesson database; they exist so one
 * pasted file cannot fill the database or stall every student's browser.
 */
public final class DatasetRules {

    public static final int MAX_TABLES = 20;
    public static final int MAX_COLUMNS = 60;
    public static final int MAX_ROWS = 20_000;
    public static final int MAX_CELLS = 500_000;

    /**
     * Up to this many cells travel inside the question itself. A bigger database is fetched once, separately, so the
     * five-second polling every student's page does is not carrying megabytes each time.
     */
    public static final int INLINE_CELLS = 3_000;

    private DatasetRules() {
    }

    public static void validate(List<DataTable> tables) {
        if (tables == null || tables.isEmpty()) {
            return;
        }
        if (tables.size() > MAX_TABLES) {
            throw new BusinessRuleException("Show at most " + MAX_TABLES + " tables with a question.");
        }
        long cells = 0;
        for (DataTable t : tables) {
            if (t.name() == null || t.name().isBlank() || t.columns() == null || t.columns().isEmpty()
                    || t.columns().size() > MAX_COLUMNS || t.columns().stream().anyMatch(c -> c == null || c.isBlank())) {
                throw new BusinessRuleException("Every table needs a name and 1 to " + MAX_COLUMNS + " named columns.");
            }
            List<List<String>> rows = t.rows() == null ? List.of() : t.rows();
            if (rows.size() > MAX_ROWS) {
                throw new BusinessRuleException("Table " + t.name() + " has " + rows.size() + " rows; the limit is "
                        + MAX_ROWS + ".");
            }
            if (rows.stream().anyMatch(row -> row == null || row.size() != t.columns().size())) {
                throw new BusinessRuleException("Table " + t.name() + ": every row needs a value for each column.");
            }
            cells += (long) rows.size() * t.columns().size();
        }
        if (cells > MAX_CELLS) {
            throw new BusinessRuleException("The tables hold " + cells + " values together; the limit is " + MAX_CELLS + ".");
        }
    }

    /** Number of values across all tables, header excluded. */
    public static long cells(List<DataTable> tables) {
        if (tables == null) {
            return 0;
        }
        return tables.stream().mapToLong(t -> (long) (t.rows() == null ? 0 : t.rows().size()) * t.columns().size()).sum();
    }
}
