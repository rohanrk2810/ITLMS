# Code style

How code in this repository is written, so that a new file reads like the old ones. When in doubt, copy a finished service (notification, placement or finance) or a finished page.

## 1. General

- Match the file you are editing: indentation, naming, comment density, and line endings (many files are CRLF on Windows).
- Comments explain **why** (a decision, a trap, a rule from the spec), not what the next line does. Write them as short plain sentences.
- Do not leave dead code or commented-out code. Do not add a feature flag nobody asked for.
- Fix the cause, not the symptom; when a bug is fixed add a test that fails without the fix.

## 2. Java (backend)

**Packages** per service: `controller`, `service` and `service/impl`, `repository`, `entity`, `dto/request`, `dto/response`, `config`, `consumer`, `security`, `specification`.

**Classes**
- Lombok for boilerplate: `@RequiredArgsConstructor` for constructor injection, `@Getter/@Setter`, `@Builder` on entities.
- DTOs are `record`s with Bean Validation (`@Valid` on the controller parameter, `@NotBlank`, `@Size`...). Responses have a static `from(entity)`.
- Entities map to Flyway-created tables; `ddl-auto: validate`.
- A unidirectional `@OneToMany` with `@JoinColumn` must set `nullable = false`, or the child insert writes a NULL key.

**Controllers** stay thin: parse, authorize, call the service, return a DTO. Every method has `@PreAuthorize` with a `Roles` constant; the caller comes from `@AuthenticationPrincipal AppPrincipal`. Swagger annotations (`@Operation`, `@Tag`) on public API.

**Services**
- Interface plus `Impl`. Business rules live here.
- Ownership checks beside the role check (a student reads only their own records).
- `@Transactional` on writes. If a method records a failure and then throws (a failed login counter), use `noRollbackFor` so the record survives.
- Errors: throw the shared exceptions from `common-lib` (`ResourceNotFoundException` 404, `BusinessRuleException` 400/409, `ForbiddenOperationException` 403, `DuplicateResourceException` 409). `GlobalExceptionHandler` shapes the response; never build an error body by hand.
- Messages are for people: say what is wrong and what to do.

**Cross-service**
- Facts, not commands: publish events with `EventPublisher.publishAfterCommit`. Consumers are idempotent (native `INSERT ... ON CONFLICT DO NOTHING/UPDATE`), not `save()` plus catching a constraint error, which poisons the transaction.
- Synchronous calls use Feign with a fallback that returns null so the check fails closed.
- Never read another service's tables.

**Database**: migrations are `V<n>__snake_case_description.sql`, one concern each, never edited after they are applied. Explain the why in a header comment.

**Config**: values in `config-repo/<service>.yml`, secrets only as `${ENV_VAR}` with no default.

## 3. TypeScript / React (frontend)

- **API layer:** one `src/api/<domain>.ts` per backend service, exporting typed functions over the shared axios client (`api/client.ts`). Pages never call axios directly. Copy an existing module for a new one.
- **Data:** TanStack Query. Query keys are arrays starting with the domain (`['announcements', 'list']`); invalidate by prefix after a mutation. Mutations use `useMutation` with `onSuccess` toast and `onError: toast.error(apiErrorMessage(error, 'fallback'))`.
- **State:** server data in Query; app-wide client state in a small zustand store (`stores/`); everything else local.
- **Roles:** decide what to show from the whole list, `useAuthStore((s) => s.roles)` with `hasRole(roles, [...])`, never from `user.role` alone; a person can hold extra duties. `role === 'STUDENT'` and `role === 'ADMIN'` on the main role are fine (neither takes extras). On the backend use `principal.hasRole(...)`, `isStaff()`, never `principal.role().equals(...)`.
- **Routing:** every page is `React.lazy` in `App.tsx`. Roles are enforced with `<Route element={<ProtectedRoute roles={[...]} />}>` (a layout route, not a wrapper with children) and mirrored in `components/nav-items.ts`.
- **Components:** function components, named exports, props typed inline or with an interface. Shared UI in `components/ui` (shadcn style), feature components beside their page. Use the tokens in [27-design-system.md](27-design-system.md).
- **Hooks and helpers** live in `src/lib` and are named `use-*.ts` for hooks.
- **Types:** no `any`; API shapes are interfaces in the same `api/` module. Keep types in step with the backend DTO.
- **Imports:** `@/` alias for `src`, third-party first, then `@/api`, `@/components`, `@/lib`, `@/stores`.
- **Lint:** `npm run lint` (oxlint) and `npm run build` (tsc plus vite) must pass; do not add new warnings in files you touch.
- Names: files `kebab-case.tsx`, components `PascalCase`, hooks `useThing`, constants `UPPER_SNAKE`.

## 4. Testing

- Backend: Mockito unit tests, no Spring context, no database; one test class per service class, named `<Class>Test`. Tests set the principal with `SecurityContextHolder` and `AppPrincipal`. Cover who is refused as well as who is allowed. Run `mvn -B -ntp clean test`.
- Pure logic (scoring, permissions, offsets) is a pure function with its own test.
- Anything that touches the running stack is added to `deploy/scripts/smoke-test.mjs` as a `section()` so one failure does not hide the rest.
- Frontend: Vitest tests sit beside the code as `*.test.ts(x)` (`npm test`); Playwright browser tests with a mocked API are in `frontend/e2e/` (`npm run e2e`). A change to routes, roles, forms or error messages gets a test in one of them. Pages are still verified in a real browser by hand.
- Details: [06-test-plan.md](06-test-plan.md).

## 5. Git

- One short sentence per commit in the form `Area: what changed and why it matters` (see `git log`): `Tests: give one student extra attempts from the results sheet (+1 attempt)`.
- Commit after each finished piece; do not mix a feature and an unrelated cleanup.
- No `Co-Authored-By` or tool-attribution trailers.
- Never commit secrets, `deploy/docker/.env`, build output, the spec PDF, or Office lock files (`~$*`).

## 6. Documentation

- A feature that changes behaviour changes its doc in the same commit; new departures from the PDF go in `docs/02-documentation-review.md`.
- Docs are plain Markdown, English, in short paragraphs and tables; link to code paths instead of pasting long code.
- Numbers (test counts, endpoint counts) are regenerated from a real run, not copied from memory, and carry the date.
