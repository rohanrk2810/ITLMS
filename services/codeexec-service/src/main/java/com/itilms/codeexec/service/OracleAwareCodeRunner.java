package com.itilms.codeexec.service;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.itilms.common.code.CodeLanguage;

/** Sends SQL to Oracle and every other language to the sandbox the service was already using. */
public class OracleAwareCodeRunner implements CodeRunner {

    private final CodeRunner sandbox;
    private final OracleSqlRunner oracle;

    public OracleAwareCodeRunner(CodeRunner sandbox, OracleSqlRunner oracle) {
        this.sandbox = sandbox;
        this.oracle = oracle;
    }

    @Override
    public RunResult run(CodeLanguage language, String sourceCode, String stdin) {
        return language == CodeLanguage.SQL ? oracle.run(sourceCode) : sandbox.run(language, sourceCode, stdin);
    }

    @Override
    public RunnerStatus status() {
        RunnerStatus base = sandbox.status();
        String problem = oracle.check();
        List<LanguageStatus> languages = new ArrayList<>(base.languages().stream()
                .filter(l -> l.language() != CodeLanguage.SQL).toList());
        languages.add(new LanguageStatus(CodeLanguage.SQL, null, "Oracle", problem == null ? "Oracle" : null, problem == null));
        String message = problem == null ? base.message() : problem;
        return new RunnerStatus(true, base.reachable() && problem == null, message, languages);
    }

    @Override
    public Set<CodeLanguage> enabledLanguages() {
        Set<CodeLanguage> languages = EnumSet.noneOf(CodeLanguage.class);
        languages.addAll(sandbox.enabledLanguages());
        languages.add(CodeLanguage.SQL);
        return languages;
    }
}
