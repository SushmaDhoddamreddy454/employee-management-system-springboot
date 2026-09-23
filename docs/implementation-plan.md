# Implementation Plan — Employee Management System

**Status:** DRAFT — pending Human Review Gate #3 (architecture) and subsequent gates
**Source of truth for scope:** `docs/approved-requirements.md`, `docs/architecture/HLD.md`, `docs/architecture/LLD.md`

This is a planning document. No files listed below have been modified; no code has been written.

## 1. Objective

Lay out a concrete, story-by-story plan for implementing the 6 approved EPICs / 18 approved user stories, so that implementation can proceed in a controlled, reviewable, human-gated sequence once architecture is approved.

## 2. Implementation Strategy

- Implement in the approved phase order (Section 3), which sequences low-risk, foundational changes (exception handling, validation) before higher-risk changes (authentication) that could break existing flows if done first.
- Keep each change scoped to its owning EPIC/story; do not bundle unrelated cleanup (e.g., duplicate main-class removal) into approved-scope commits.
- Add or update tests alongside each phase rather than deferring all testing to EPIC-05 — EPIC-05 formalizes and completes coverage, but validation/exception-handling changes should be verified as they land.
- Do not implement anything gated on a pending decision (auth mechanism, page size, salary/DOB limits) until that decision is resolved; implement everything else in the meantime.

## 3. Recommended Implementation Order

**Phase 1 — Global Exception Handling (EPIC-02)**
No dependency on other phases. Establishes `GlobalExceptionHandler` and the standardized error contract that later phases (validation, auth, search) will reuse for their own error cases.

**Phase 2 — Validation & Business Rules (EPIC-03)**
Depends on Phase 1 (reuses the error contract for 400 responses). Introduces `EmployeeService` as the home for uniqueness/business-rule checks — the first appearance of a service layer, reused by Phase 4.

**Phase 3 — Authentication & RBAC (EPIC-01)**
Depends on Phases 1–2 being stable, since it wraps every existing endpoint with new authn/authz behavior; doing it last among the "backend correctness" phases minimizes the risk of debugging validation/exception issues *through* a new security layer. **Blocked on the authentication-mechanism decision** (Section 13, item 1) — cannot proceed past design until resolved.

**Phase 4 — Search & Pagination (EPIC-06)**
Depends on Phase 2 (reuses `EmployeeService`) and benefits from Phase 3 being in place (search/pagination endpoints are also protected). **Blocked on the default-page-size decision** (Section 13, item 3) for final tuning, though the query mechanics can be implemented with a placeholder-free design (explicit `size` always required from the client) if the default is not yet approved.

**Phase 5 — Configuration & Security Hygiene (EPIC-04)**
Independent of Phases 1–4 functionally, but sequenced after them so that environment/CORS changes are validated against the final set of endpoints and behaviors rather than an intermediate state.

**Phase 6 — Automated Testing (EPIC-05)**
Formalizes and completes coverage across all preceding phases. Some unit/integration tests are written incrementally during Phases 1–4 (Section 2); Phase 6 is where Cucumber/Selenium UI coverage (US-05-03) and any remaining gaps are completed.

## 4. Story-by-Story Implementation Plan

### EPIC-01 — Authentication & Role-Based Access Control

**US-01-01 — Require authentication**
- Objective: reject unauthenticated access to protected endpoints; allow authenticated access per role.
- Files/components likely affected: new `security/SecurityConfig.java`; `pom.xml` (add `spring-boot-starter-security`); `EmployeeController` (no code change expected if matcher-based rules are used).
- Dependencies: authentication mechanism decision (open).
- Implementation tasks: add security dependency; configure filter chain requiring authentication on `/api/employees/**`.
- Testing tasks: integration test — unauthenticated request → 401; authenticated request → proceeds.
- Documentation tasks: note the chosen mechanism once approved.
- Risks: could break existing manual/frontend testing flows if not sequenced after Phases 1–2.
- Human approval required: **Yes** — authentication mechanism.

**US-01-02 — Role-based CRUD authorization**
- Objective: ADMIN/HR can perform CRUD; EMPLOYEE cannot write.
- Files/components likely affected: `security/SecurityConfig.java` (or `@PreAuthorize` on `EmployeeController` methods).
- Dependencies: US-01-01.
- Implementation tasks: define role-based access rules for POST/PUT/DELETE.
- Testing tasks: integration tests for ADMIN/HR write success, EMPLOYEE write rejection.
- Documentation tasks: document role-to-operation mapping in HLD/LLD once implemented (already drafted as proposed).
- Risks: none beyond US-01-01's.
- Human approval required: **Only if** HR permissions diverge from ADMIN (currently assumed identical, per approved requirements).

**US-01-03 — EMPLOYEE read-only access**
- Objective: EMPLOYEE can GET but not write.
- Files/components likely affected: same as US-01-02.
- Dependencies: US-01-01, US-01-02.
- Implementation tasks: define GET-permitted, write-denied rule for EMPLOYEE role.
- Testing tasks: integration test — EMPLOYEE GET succeeds, EMPLOYEE POST/PUT/DELETE → 403.
- Documentation tasks: none beyond above.
- Risks: none additional.
- Human approval required: No (covered by US-01-01/02 approval).

### EPIC-02 — Global Exception Handling

**US-02-01 — Standardized error responses**
- Objective: all API errors return a consistent structured response.
- Files/components likely affected: new `exception/GlobalExceptionHandler.java`, new `exception/ApiErrorResponse.java`.
- Dependencies: none.
- Implementation tasks: create `@RestControllerAdvice`; define `ApiErrorResponse`; add a catch-all handler for unexpected errors.
- Testing tasks: integration test — trigger an unexpected error, assert standardized body/500.
- Documentation tasks: record final error-response shape (currently proposed in LLD Section 16) once implemented.
- Risks: low.
- Human approval required: No.

**US-02-02 — Missing employee returns 404**
- Objective: GET/PUT (and other operations needing the record) on a nonexistent id return 404, not 500.
- Files/components likely affected: `EmployeeController` (replace inline `RuntimeException` with `EmployeeNotFoundException`), new `exception/EmployeeNotFoundException.java`, `GlobalExceptionHandler` (add handler).
- Dependencies: US-02-01.
- Implementation tasks: introduce `EmployeeNotFoundException`; update controller (or service, once introduced) to throw it; map to 404 in handler; also cover `DELETE` on missing id (currently uncaught `EmptyResultDataAccessException`).
- Testing tasks: integration tests — GET/PUT/DELETE on missing id → 404.
- Documentation tasks: none beyond above.
- Risks: low.
- Human approval required: No.

**US-02-03 — Validation errors return field details**
- Objective: invalid create/update requests return 400 with per-field messages.
- Files/components likely affected: `GlobalExceptionHandler` (handle `MethodArgumentNotValidException`).
- Dependencies: US-02-01.
- Implementation tasks: add handler mapping validation exceptions to `ApiErrorResponse.fieldErrors`.
- Testing tasks: integration test — submit multiple invalid fields, assert 400 + field-level messages for each.
- Documentation tasks: none beyond above.
- Risks: low.
- Human approval required: No.

### EPIC-03 — Input Validation & Business Rules

**US-03-01 — Extended server-side validation**
- Objective: server rejects invalid required fields/malformed email; accepts valid data.
- Files/components likely affected: `model/Employee.java` (validation already largely present for name/email — confirm coverage), possibly new `service/EmployeeService.java`.
- Dependencies: EPIC-02 (for error surfacing).
- Implementation tasks: verify/extend annotation coverage; introduce `EmployeeService.create/update` as the entry point for business-rule checks (used again in US-03-02/03).
- Testing tasks: unit tests for validation logic; integration test for the 201/200 success path.
- Documentation tasks: none beyond above.
- Risks: low.
- Human approval required: No.

**US-03-02 — Unique employee email**
- Objective: reject duplicate email on create; reject on update if it collides with another employee; allow update that keeps its own email.
- Files/components likely affected: `EmployeeRepository` (add `existsByEmail`, `existsByEmailAndIdNot`), `EmployeeService`, new `exception/DuplicateEmailException.java`, `model/Employee.java` (`@Column(unique = true)`).
- Dependencies: US-03-01, EPIC-02 (error handling).
- Implementation tasks: implement uniqueness checks in `EmployeeService`; add DB-level unique constraint; add exception + handler mapping.
- Testing tasks: unit tests (duplicate on create, duplicate on update, unchanged-email update allowed); integration test for the same three cases end-to-end.
- Documentation tasks: none beyond above.
- Risks: existing data with duplicate emails could break the DB migration — verify before adding the constraint.
- Human approval required: No (rule itself is approved; only the DB-migration risk needs implementation-time care, not a new gate).

**US-03-03 — Salary and DOB business rules**
- Objective: reject negative salary and future DOB; accept otherwise-valid data.
- Files/components likely affected: `model/Employee.java` (`@PositiveOrZero` on salary), `EmployeeService` or a custom validator for DOB-not-future.
- Dependencies: US-03-01.
- Implementation tasks: add salary constraint; implement DOB-future check.
- Testing tasks: unit tests (negative salary rejected, future DOB rejected, valid data accepted); integration tests for the same.
- Documentation tasks: none — explicitly do not add numeric salary/age limits beyond "non-negative"/"not future" without a decision.
- Risks: none beyond the open decisions noted below.
- Human approval required: **Only if** numeric salary/DOB range limits beyond "non-negative"/"not future" are later requested — not required for the currently approved rules.

### EPIC-04 — Configuration & Security Hygiene

**US-04-01 — Externalize database credentials**
- Objective: no plaintext DB credentials in source-controlled configuration.
- Files/components likely affected: `application.properties` (replace literal `<configured username>`/`<configured password>` with env-var placeholders).
- Dependencies: none.
- Implementation tasks: introduce `${DB_USERNAME}`/`${DB_PASSWORD}` (or equivalent) placeholders; document how to supply them locally (e.g., a git-ignored local override or environment variables).
- Testing tasks: manual/local verification that the app starts with env vars supplied; no new automated test strictly required, though a config-loading smoke test is reasonable.
- Documentation tasks: update run instructions (README or similar) to describe how to supply credentials — deferred to documentation phase.
- Risks: low; must not accidentally commit real credentials in a local override file.
- Human approval required: No.

**US-04-02 — Safer DDL and logging configuration**
- Objective: avoid unsafe schema auto-management and excessive debug logging by default.
- Files/components likely affected: `application.properties` (`spring.jpa.hibernate.ddl-auto`, `logging.level.org.springframework`), possibly Spring profiles.
- Dependencies: none.
- Implementation tasks: adjust default `ddl-auto` and logging level; introduce profile separation if needed for local vs. other environments.
- Testing tasks: manual verification of startup behavior under the new settings.
- Documentation tasks: document environment/profile strategy.
- Risks: changing `ddl-auto` could affect local dev convenience — balance per capstone's local-only deployment target.
- Human approval required: No.

**US-04-03 — Restricted CORS**
- Objective: only approved frontend origin(s) can call the API.
- Files/components likely affected: `EmployeeController` (remove `@CrossOrigin(origins = "*")`), new `config/CorsConfig.java`.
- Dependencies: none functionally, but should reflect whatever origin actually serves the frontend once EPIC-01 auth is in place (browser credentialed requests interact with CORS).
- Implementation tasks: define allowed origin(s) matching actual local serving setup; remove wildcard.
- Testing tasks: integration test verifying an approved origin is permitted and a disallowed one is rejected (via `MockMvc` CORS test support).
- Documentation tasks: none beyond above.
- Risks: low; must not hardcode a guessed production domain.
- Human approval required: No.

### EPIC-05 — Automated Testing

**US-05-01 — Unit tests**
- Objective: automated unit coverage for application logic.
- Files/components likely affected: new test classes, e.g. `EmployeeServiceTest`.
- Dependencies: EPIC-02/03 logic existing to test.
- Implementation tasks: write unit tests for validation/business-rule logic (salary, DOB, uniqueness) with mocked repository.
- Testing tasks: (this story is itself testing) — ensure both valid and invalid/failure paths are covered.
- Documentation tasks: none.
- Risks: low.
- Human approval required: No.

**US-05-02 — API integration tests**
- Objective: automated end-to-end API-level coverage.
- Files/components likely affected: new test classes, e.g. `EmployeeControllerIntegrationTest`.
- Dependencies: EPIC-01/02/03/06 endpoints existing to test.
- Implementation tasks: `@SpringBootTest` + `MockMvc` tests covering success, validation failure, not-found, unauthorized, forbidden.
- Testing tasks: as above.
- Documentation tasks: none.
- Risks: needs a test database/profile to avoid touching a real MySQL instance — implementation detail (e.g., an in-memory/test datasource profile), not a scope change.
- Human approval required: No.

**US-05-03 — Gherkin UI search and pagination**
- Objective: automated UI-level coverage for search and pagination via Cucumber/Selenium.
- Files/components likely affected: new `src/test/resources/features/*.feature`, new step-definition classes, Selenium page-object helper(s); `pom.xml` (add Cucumber-JVM, Selenium WebDriver dependencies).
- Dependencies: EPIC-06 frontend/backend search-and-pagination behavior must exist first.
- Implementation tasks: write Gherkin scenarios matching approved acceptance criteria verbatim; implement step definitions driving a browser against the running app.
- Testing tasks: execute the feature suite; capture Cucumber HTML/JSON report as evidence.
- Documentation tasks: none beyond report retention.
- Risks: browser/WebDriver environment setup for CI/local execution; sequencing after EPIC-06 is required.
- Human approval required: No.

### EPIC-06 — Server-side Employee Search & Pagination

**US-06-01 — Employee pagination**
- Objective: API returns employees page-by-page instead of the full list.
- Files/components likely affected: `EmployeeController` (`page`/`size` params), `EmployeeRepository`/`EmployeeService` (`Pageable` support).
- Dependencies: EPIC-03's `EmployeeService`, if introduced, is extended rather than duplicated.
- Implementation tasks: accept paging params; return `Page<Employee>`.
- Testing tasks: integration test — request page N, assert correct subset and metadata.
- Documentation tasks: none — default page size stays undecided until approved.
- Risks: none beyond the open page-size decision.
- Human approval required: **Yes** — default page size.

**US-06-02 — Server-side employee search**
- Objective: API filters employees server-side by search criterion.
- Files/components likely affected: `EmployeeRepository` (derived query method), `EmployeeService`, `EmployeeController` (`search` param).
- Dependencies: US-06-01 (pagination applied to filtered results per AC3).
- Implementation tasks: implement filtered query; combine with pagination.
- Testing tasks: integration tests — matching search returns filtered results; non-matching search returns empty page; search + pagination combined.
- Documentation tasks: none.
- Risks: low.
- Human approval required: No.

**US-06-03 — Frontend API-backed search and pagination**
- Objective: `script.js`/`index.html` consume server-side search/pagination instead of client-side filtering of a fully loaded list.
- Files/components likely affected: `static/script.js` (`loadEmployees`, `searchEmployees` rewritten to call the API with params; add page-navigation UI/logic), `static/index.html` (add pagination controls).
- Dependencies: US-06-01, US-06-02 backend support must exist first.
- Implementation tasks: replace DOM-filtering `searchEmployees()` with an API-calling version; add page controls and page-change handling; keep `API_URL` hardcoded (GAP-11 out of scope).
- Testing tasks: manual UI verification; covered by US-05-03's Cucumber/Selenium suite for automated coverage.
- Documentation tasks: none.
- Risks: must not silently change the hardcoded API base URL while touching `script.js`.
- Human approval required: No (blocked only on US-06-01's page-size decision for final tuning).

## 5. Database Changes

- **Required:** unique constraint on `employees.email` (US-03-02).
- **Proposed (non-blocking, defense-in-depth):** `NOT NULL` DB constraints mirroring existing `name`/`email` bean validation.
- **Pending decision:** any credential/user table needed for authentication (EPIC-01) — shape and necessity depend entirely on the unresolved authentication-mechanism decision; not designed or planned further until resolved.
- **Explicitly excluded:** audit fields (GAP-07).

## 6. API Changes

- **Existing APIs affected:** all five current `/api/employees` endpoints (EPIC-01 adds auth/role checks to all; EPIC-02 changes their error behavior; EPIC-03 changes create/update validation behavior; EPIC-06 changes the GET-collection endpoint's parameters and response shape).
- **Proposed new/changed APIs:** no new resource paths — `GET /api/employees` gains `search`/`page`/`size` query parameters and returns `Page<Employee>` instead of `List<Employee>` (a breaking response-shape change for that one endpoint, expected and required by EPIC-06).
- **Security changes:** authentication required on all endpoints; ADMIN/HR required for write; EMPLOYEE limited to read (EPIC-01).
- **Request/response changes:** error responses adopt the standardized `ApiErrorResponse` shape (EPIC-02); collection GET response becomes a `Page<Employee>` envelope (EPIC-06). Individual-resource request/response bodies (`Employee` JSON) are otherwise unchanged.
- **Current vs. proposed:** see HLD Section 12 for the full current/proposed endpoint table.

## 7. Frontend Changes

- **`index.html`:** add pagination controls (e.g., page number/next/previous); no other structural change; `style.css` link remains as-is (pre-existing, unrelated to approved scope).
- **`script.js`:** rewrite `loadEmployees()`/`searchEmployees()` to call `API_URL` with `search`/`page`/`size` query parameters and render only the returned page/content array; keep `API_URL` hardcoded (GAP-11 out of scope).
- **Authentication UI:** only if/when an authentication mechanism is approved — not planned further here since the mechanism is undecided.
- **Error handling:** surface the new standardized error responses (EPIC-02) through the existing `showNotification()` toast mechanism instead of the current generic "Failed to..." messages.
- Not implemented in this planning phase — described for future implementation only.

## 8. Testing Plan

| Story | Unit | API integration | Security | Validation | Search/pagination | Gherkin/Selenium |
|---|---|---|---|---|---|---|
| US-01-01 | — | ✓ | ✓ | — | — | — |
| US-01-02 | — | ✓ | ✓ | — | — | — |
| US-01-03 | — | ✓ | ✓ | — | — | — |
| US-02-01 | — | ✓ | — | — | — | — |
| US-02-02 | — | ✓ | — | — | — | — |
| US-02-03 | — | ✓ | — | ✓ | — | — |
| US-03-01 | ✓ | ✓ | — | ✓ | — | — |
| US-03-02 | ✓ | ✓ | — | ✓ | — | — |
| US-03-03 | ✓ | ✓ | — | ✓ | — | — |
| US-04-01 | — | — | — | — | — | — (manual/local verification) |
| US-04-02 | — | — | — | — | — | — (manual/local verification) |
| US-04-03 | — | ✓ | ✓ (CORS) | — | — | — |
| US-05-01 | ✓ (this story is the coverage itself) | — | — | — | — | — |
| US-05-02 | — | ✓ (this story is the coverage itself) | — | — | — | — |
| US-05-03 | — | — | — | — | ✓ | ✓ (this story is the coverage itself) |
| US-06-01 | — | ✓ | — | — | ✓ | ✓ |
| US-06-02 | — | ✓ | — | — | ✓ | ✓ |
| US-06-03 | — | — | — | — | ✓ (manual + Selenium) | ✓ |

## 9. Git Strategy

- One logical change per commit, scoped to a single story or a tightly related pair of stories within the same EPIC.
- Commit messages reference the story ID (e.g., "US-02-02: return 404 for missing employee").
- No commit bundles unrelated cleanup (duplicate main-class removal, audit fields, service-layer generalization, configurable API URL) with approved-scope work.
- **Commits are made only after human approval of the relevant implementation**, consistent with the Definition of Done in `docs/approved-requirements.md` Section 9. No commits have been made as part of this planning activity.

## 10. Code Review Strategy

- Review each change against its story's acceptance criteria in `docs/approved-requirements.md` — not against an expanded interpretation of it.
- Confirm no unapproved EPIC/role/feature (MANAGER, audit fields, standalone service EPIC, configurable API URL) has crept in.
- Confirm tests accompany the change per Section 8.
- Confirm pending decisions (Section 13) have not been silently resolved by a specific numeric/technical choice presented as final.
- Confirm existing verified behavior not targeted by the story is preserved.

## 11. Human Review Gates

- **Gate #3 — Architecture Approval:** review of `docs/architecture/architecture-overview.md`, `HLD.md`, `LLD.md`, and this implementation plan. **Not yet approved** — this document requests that approval.
- **Gate #4 — Implementation Plan Approval:** confirms the phase order, story breakdown, and test plan in this document before coding begins. **Not yet approved.**
- **Gate #5 — Pre-Implementation Approval:** final checkpoint immediately before code changes start, confirming all pending decisions blocking the first phase(s) are resolved. **Not yet approved.**

These are planned gates only; none have occurred yet.

## 12. Risks and Mitigations

| Risk | Mitigation |
|---|---|
| Authentication mechanism undecided blocks EPIC-01 | Implement Phases 1–2 (and much of Phase 4) first; escalate the decision before Phase 3 begins |
| Default page size undecided | Design US-06-01/02 to require an explicit `size` param rather than assuming a hardcoded default; finalize once approved |
| Salary/DOB numeric limits undecided | Implement only "non-negative"/"not future" rules now; do not add invented bounds |
| Security retrofit breaking existing behavior | Sequence EPIC-01 after EPIC-02/03; add integration tests before/alongside the security change |
| DB unique constraint failing against existing duplicate data | Check for existing duplicates before applying the migration; resolve data issues first if found |
| Scope creep toward GAP-07/08/11 or main-class cleanup | Explicit code-review checklist item (Section 10); no commit bundling (Section 9) |

## 13. Open Decisions

1. Authentication mechanism (blocks EPIC-01 implementation).
2. Exact HR permissions if different from ADMIN (currently assumed identical).
3. Default pagination/page size (blocks final tuning of EPIC-06).
4. Exact salary upper/range limits, if required beyond "non-negative" (EPIC-03).
5. Exact DOB/age business-rule limits, if required beyond "not in the future" (EPIC-03).

None of these are resolved by this document. None should be resolved by implementation without a Human Review Gate.

## 14. Final Traceability Matrix

| EPIC | Story | Design artifact | Implementation task | Test coverage |
|---|---|---|---|---|
| EPIC-01 | US-01-01 | HLD §6, LLD §10 | `SecurityConfig` (authn) | Integration + security test |
| EPIC-01 | US-01-02 | HLD §6, LLD §10 | `SecurityConfig`/`@PreAuthorize` (role rules) | Integration + security test |
| EPIC-01 | US-01-03 | HLD §6, LLD §10 | Role rule: GET allowed, write denied for EMPLOYEE | Integration + security test |
| EPIC-02 | US-02-01 | HLD §7, LLD §9/§16 | `GlobalExceptionHandler`, `ApiErrorResponse` | Integration test |
| EPIC-02 | US-02-02 | HLD §7, LLD §9 | `EmployeeNotFoundException` + handler | Integration test |
| EPIC-02 | US-02-03 | HLD §7, LLD §9 | Validation-exception handler | Integration test |
| EPIC-03 | US-03-01 | HLD §8, LLD §5/§11 | `Employee` validation, `EmployeeService` | Unit + integration test |
| EPIC-03 | US-03-02 | HLD §8, LLD §5/§6/§7/§11 | Uniqueness checks, `DuplicateEmailException`, unique index | Unit + integration test |
| EPIC-03 | US-03-03 | HLD §8, LLD §7/§11 | Salary/DOB validation | Unit + integration test |
| EPIC-04 | US-04-01 | HLD §10, LLD §13 | Externalized credentials | Manual/local verification |
| EPIC-04 | US-04-02 | HLD §10, LLD §13 | DDL/logging config | Manual/local verification |
| EPIC-04 | US-04-03 | HLD §10, LLD §14 | `CorsConfig` | Integration test |
| EPIC-05 | US-05-01 | HLD §14, LLD §15 | Unit test suite | (self) |
| EPIC-05 | US-05-02 | HLD §14, LLD §15 | Integration test suite | (self) |
| EPIC-05 | US-05-03 | HLD §14, LLD §15 | Cucumber/Selenium suite | (self) |
| EPIC-06 | US-06-01 | HLD §9, LLD §6/§12 | Paged `EmployeeRepository`/`EmployeeService`/controller | Integration + Selenium test |
| EPIC-06 | US-06-02 | HLD §9, LLD §6/§12 | Filtered query + pagination combination | Integration + Selenium test |
| EPIC-06 | US-06-03 | HLD §13, LLD §12 | `script.js`/`index.html` API-backed search/pagination | Manual + Selenium test |
