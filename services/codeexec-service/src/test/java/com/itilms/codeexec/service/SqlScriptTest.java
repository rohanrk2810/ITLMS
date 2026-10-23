package com.itilms.codeexec.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** How an Oracle script is cut into the statements that are sent one by one. */
class SqlScriptTest {

    @Test
    @DisplayName("Plain statements end at a semicolon, and the semicolon is not sent")
    void plainStatements() {
        assertThat(SqlScript.statements("CREATE TABLE t (id NUMBER);\nINSERT INTO t VALUES (1);\nSELECT * FROM t;"))
                .containsExactly("CREATE TABLE t (id NUMBER)", "INSERT INTO t VALUES (1)", "SELECT * FROM t");
    }

    @Test
    @DisplayName("A semicolon inside a string or a comment ends nothing")
    void semicolonsInsideQuotesAndComments() {
        assertThat(SqlScript.statements("INSERT INTO t VALUES ('a;b'); -- done; really\nSELECT 1 FROM dual /* x; y */;"))
                .containsExactly("INSERT INTO t VALUES ('a;b')", "-- done; really\nSELECT 1 FROM dual /* x; y */");
    }

    @Test
    @DisplayName("A PL/SQL block keeps its inner semicolons and ends at a line holding only a slash")
    void plsqlBlocks() {
        String script = "BEGIN\n  INSERT INTO t VALUES (1);\n  COMMIT;\nEND;\n/\nSELECT * FROM t;";
        assertThat(SqlScript.statements(script))
                .containsExactly("BEGIN\n  INSERT INTO t VALUES (1);\n  COMMIT;\nEND;", "SELECT * FROM t");
    }

    @Test
    @DisplayName("CREATE OR REPLACE PROCEDURE is a block, CREATE TABLE is not")
    void createIsABlockOnlyForStoredCode() {
        String script = "CREATE OR REPLACE PROCEDURE p AS\nBEGIN\n  NULL;\nEND;\n/\nCREATE TABLE t (id NUMBER);";
        assertThat(SqlScript.statements(script))
                .containsExactly("CREATE OR REPLACE PROCEDURE p AS\nBEGIN\n  NULL;\nEND;", "CREATE TABLE t (id NUMBER)");
    }

    @Test
    @DisplayName("SQL*Plus commands are dropped, and so are statements that are only comments")
    void clientCommandsAndEmptyStatements() {
        assertThat(SqlScript.statements("SET SERVEROUTPUT ON\nPROMPT hello\n-- just a note\n;\nSELECT 1 FROM dual;\n"))
                .containsExactly("SELECT 1 FROM dual");
    }

    @Test
    @DisplayName("The last statement needs no semicolon")
    void lastStatementWithoutSemicolon() {
        assertThat(SqlScript.statements("SELECT 1 FROM dual")).containsExactly("SELECT 1 FROM dual");
    }
}
