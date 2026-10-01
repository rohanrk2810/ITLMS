# Student-chosen language

A coding question or a practice lesson can let the student pick the language to write in. **Off unless the author turns it on.**

- **Test coding question** (assessment-service V6): `quiz_questions.allow_language_choice`. When on, the student sees a language selector (every language except SQL). The test cases are plain input and output, so any language is checked against them. Enforced on the server: `run-tests` and saving an answer accept a language other than the question's only if the question allows it, never SQL, and refuse anything else ("This question must be answered in ..."). The language used is stored with the answer (`quiz_answers.code_language`); the verdict key mixes it in, so the same text run as Python and as Java are different verdicts, and the re-run at submission uses the language the student wrote in. A SQL question cannot offer a choice. Starter code is only offered in the question's own language.
- **Practice lesson** (course-service V3): `lessons.allow_language_choice`. The practice editor shows the selector; each language keeps its own draft. This one is a convenience, not a control: the practice editor can run any language the server runs anyway.
- Authors set it with a checkbox in the question editor and the lesson editor (hidden for SQL).
