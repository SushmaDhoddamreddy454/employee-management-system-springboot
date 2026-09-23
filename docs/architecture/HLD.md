# High-Level Design — Employee Management System

**Status:** DRAFT — pending Human Review Gate #3
**Source of truth for scope:** `docs/approved-requirements.md`

Legend used throughout: **[VERIFIED]** = confirmed by direct repository inspection. **[PROPOSED]** = future design, not yet implemented. **[PENDING]** = depends on an open human decision.

## 1. Purpose

Define the high-level design needed to implement EPIC-01 through EPIC-06 on the existing Employee Management System, grounded in the actual current codebase rather than assumptions.

## 2. Scope

This HLD covers exactly the approved scope:

- EPIC-01 — Authentication & Role-Based Access Control (GAP-01)
- EPIC-02 — Global Exception Handling (GAP-04)
- EPIC-03 — Input Validation & Business Rules (GAP-05/GAP-06)
- EPIC-04 — Configuration & Security Hygiene (GAP-09)
- EPIC-05 — Automated Testing (GAP-10)
- EPIC-06 — Server-side Employee Search & Pagination (GAP-02/GAP-03/GAP-12)

Out of scope: GAP-07, GAP-08 (no standalone service-layer EPIC), GAP-11, main-class duplication cleanup.

## 3. Current Architecture **[VERIFIED]**

Based on inspection of `src/main/java/com/example/employeemgt/**`, `src/main/resources/**`, and `src/test/java/**`:

- **Frontend:** `static/index.html` + `static/script.js`. Bootstrap 5 + Font Awesome + jQuery via CDN. `script.js` hardcodes `const API_URL = 'http://localhost:8080/api/employees'`. `loadEmployees()` fetches the *entire* collection on page load; `searchEmployees()` filters already-rendered `<tr>` elements client-side by substring match on name/email columns. There is no pagination UI or logic at all.
- **REST controller:** `EmployeeController` (`@RestController`, `@RequestMapping("/api/employees")`, `@CrossOrigin(origins = "*")`). Endpoints: `GET /`, `POST /`, `GET /{id}`, `PUT /{id}`, `DELETE /{id}`. Controller talks directly to `EmployeeRepository` — no intermediate service class exists anywhere in the codebase.
- **Repository:** `EmployeeRepository extends JpaRepository<Employee, Long>` — no custom query methods, no `Pageable` usage, no `Specification`/`Example` usage.
- **Entity:** `Employee` — fields `id` (Long, identity PK), `name` (`@NotBlank`), `email` (`@NotBlank @Email`), `dob` (`java.util.Date`, `@Temporal(DATE)`), `age` (`@Transient`, computed in the getter from `dob`), `salary` (`Double`, no constraints), `status` (`boolean`, default `true`). No `@Column(unique = true)` anywhere. An unused `org.antlr.v4.runtime.misc.NotNull` import is present but not applied to any field.
- **Database:** MySQL, reached via `mysql-connector-j`; `spring.jpa.hibernate.ddl-auto=update`; `spring.jpa.show-sql=true`.
- **Configuration:** `application.properties` has plaintext `spring.datasource.username=<configured username>` / `spring.datasource.password=<configured password>`, and `logging.level.org.springframework=DEBUG`.
- **Tests:** `EmployeemgtApplicationTests` contains only a `contextLoads()` smoke test. No other test class exists in the repository.
- **Not-found handling:** `getEmployeeById`/`updateEmployee` call `.orElseThrow(() -> new RuntimeException(...))`. Spring Boot's default handling of an uncaught `RuntimeException` returns **HTTP 500**, not 404 — there is no `@ControllerAdvice` anywhere in the codebase to change this.
- **Security:** No `spring-boot-starter-security` (or any security artifact) is declared in `pom.xml`. There is no authentication or authorization of any kind today.

## 4. Target Architecture **[PROPOSED]**

Same monolithic shape, with these layers added/modified:

1. Spring Security filter chain protecting `/api/employees/**`, enforcing ADMIN/HR/EMPLOYEE roles (mechanism **[PENDING]**).
2. `@RestControllerAdvice` global exception handler + a small set of custom exceptions (e.g., "employee not found", "duplicate email").
3. Strengthened `Employee` validation (unique email, non-negative salary, non-future DOB) plus a DB-level unique constraint on `email`.
4. Repository/controller changes to accept search + `Pageable` parameters and return paged results.
5. Externalized configuration for DB credentials, environment-appropriate `ddl-auto`/logging, and a restricted CORS policy.
6. New test suites: JUnit unit tests, `MockMvc`/`@SpringBootTest` integration tests, Cucumber/Gherkin + Selenium UI tests.
7. `script.js` updated to call the API with search/page parameters instead of filtering an already-fetched full list.

## 5. Logical Architecture **[PROPOSED]**

```
Frontend (static/index.html, script.js)
        ↓  fetch() with search/page query params
REST API  (EmployeeController)
        ↓
Security  (authentication + role-based authorization filter chain)
        ↓
Application/business processing  ([minimal service layer] — see below)
        ↓
Persistence  (EmployeeRepository, Pageable/filter queries)
        ↓
MySQL
```

**On the service layer:** the current codebase has the controller call the repository directly, with zero business logic elsewhere. Two approved EPICs introduce logic that doesn't cleanly belong in the controller (HTTP concerns) or the repository (persistence concerns):

- EPIC-03 needs uniqueness/salary/DOB rule checks that are more than a bean-validation annotation can express (e.g., "email unique excluding this employee's own current record on update").
- EPIC-06 needs to assemble a filtered, paginated query from optional search criteria.

A **minimal internal service class** (e.g., `EmployeeService`) is recommended as the place for this logic, sitting between the controller and repository. This is an implementation detail of EPIC-03/EPIC-06, **not** a new EPIC — GAP-08 remains explicitly out of scope as a *standalone* EPIC. If introduced, it should do only what EPIC-03/EPIC-06 require and nothing more.

## 6. Authentication & RBAC Architecture

- **Authentication boundary:** all `/api/employees/**` requests must be authenticated before reaching the controller logic; unauthenticated requests are rejected at the security-filter level (US-01-01).
- **Authorization boundary:** once authenticated, role is checked per operation. ADMIN and HR are authorized for create/read/update/delete (US-01-02). EMPLOYEE is authorized for read-only (GET) operations only (US-01-03).
- **Unauthorized vs. unauthenticated behavior:** an unauthenticated request must receive an authentication-failure response (e.g., 401); an authenticated request whose role lacks permission must receive an authorization-failure response (e.g., 403). These are distinct outcomes per the approved acceptance criteria and must not be conflated.
- **Protected endpoints:** all five existing `EmployeeController` endpoints become protected. There is no "public" employee endpoint in the approved scope.

**RECOMMENDATION — REQUIRES HUMAN APPROVAL:** Spring Security with HTTP Basic authentication and in-memory or database-backed `UserDetailsService`, using Spring Security's built-in role model (`hasRole("ADMIN")`, etc.), is a reasonable fit for a capstone-scale application — it needs no additional frontend session/token handling and integrates directly with `@PreAuthorize`/`antMatchers`-style rules. This is only a recommendation; the authentication mechanism itself remains an **open decision** (see Section 17, item 1) and must not be treated as approved until a human confirms it at Gate #3 or a dedicated decision review.

## 7. Exception Handling Architecture **[PROPOSED]**

A single `@RestControllerAdvice` class centralizes handling for:

- **Validation errors** (`MethodArgumentNotValidException` from `@Valid`) → HTTP 400 with field-level messages (US-02-03).
- **Not-found errors** (a new `EmployeeNotFoundException` replacing the current raw `RuntimeException`) → HTTP 404 (US-02-02).
- **Authorization errors** (`AccessDeniedException` from Spring Security) → HTTP 403.
- **Authentication errors** (`AuthenticationException`/entry point) → HTTP 401.
- **Unexpected errors** (anything else) → HTTP 500 with a standardized, non-leaky error body (US-02-01).

All branches return the same standardized response shape (see LLD Section 16), differing only in HTTP status and message/field content.

## 8. Validation Architecture **[PROPOSED]**

- **Request validation:** unchanged mechanism (`@Valid` + Jakarta Bean Validation annotations on `Employee`), now surfaced through the global exception handler instead of Spring's default 400 body.
- **Employee field validation:** existing `@NotBlank`/`@Email` retained; add validation for salary and DOB.
- **Unique email:** enforced both as an application-level check (needed to distinguish "create with duplicate" vs. "update without changing own email" per US-03-02) and as a **DB-level unique constraint** on the `employees.email` column, as defense-in-depth against race conditions.
- **Salary validation:** reject negative values (US-03-03). No upper bound is defined — **do not invent one** (Section 17, item 4).
- **DOB validation:** reject future dates (US-03-03). No minimum age/DOB lower bound is defined — **do not invent one** (Section 17, item 5).
- **Validation error response:** field-level errors surfaced via the same standardized error contract as Section 7.

## 9. Search & Pagination Architecture **[PROPOSED]**

- **API request flow:** `GET /api/employees` accepts optional search parameter(s) (e.g., a name/email query term) plus pagination parameters (page number, page size), and returns a page of results rather than the full collection.
- **Spring Data pagination:** implemented via `Pageable`/`Page<Employee>`, which `JpaRepository` already supports without additional dependencies.
- **Filtered result pagination:** when a search term is supplied, pagination applies to the *filtered* result set, not the unfiltered one (US-06-02 AC3).
- **Response metadata:** the response must convey enough paging metadata (e.g., total elements/pages, current page) for the frontend to render page controls — exact response shape is an implementation detail for the LLD, not a new business decision.
- **Frontend integration:** `script.js` sends the current search term and page number as query parameters and renders only the returned page, instead of fetching everything and filtering the DOM.
- **[PENDING]** Default page size is not approved — see Section 17, item 3. No numeric default should be hardcoded into design or code until confirmed.

## 10. Configuration & Security Architecture **[PROPOSED]**

- **Database credentials:** removed from `application.properties` in plaintext; sourced from environment variables (e.g., `${DB_USERNAME}`, `${DB_PASSWORD}`) or an externalized properties/profile mechanism.
- **Environment configuration:** Spring profiles (or equivalent) distinguish local/dev vs. other environments as needed for DDL/logging settings — no new environments are invented beyond what's needed to satisfy US-04-01/02.
- **Schema-management configuration:** `ddl-auto=update` is reconsidered for non-local environments (e.g., `validate`/`none` with externally managed schema) per US-04-02; exact target value per environment is an implementation detail, not a business decision.
- **Logging:** `logging.level.org.springframework=DEBUG` is removed/reduced so DEBUG is not the default (US-04-02).
- **CORS:** `@CrossOrigin(origins = "*")` is replaced with an explicit allow-list containing the approved frontend origin(s) actually used to serve `index.html` (US-04-03). No unrelated security hardening (e.g., CSRF changes unrelated to the approved scope) is introduced.

## 11. Database Architecture

**Current database model [VERIFIED]:**

```
employees
├── id            BIGINT, PK, IDENTITY
├── name          VARCHAR, NOT NULL (bean-validated, not DB-enforced)
├── email         VARCHAR, NOT NULL (bean-validated, not DB-enforced, no uniqueness)
├── dob           DATE
├── salary        DOUBLE (nullable, no constraint)
└── status        BOOLEAN (default true)
```
(`age` is `@Transient` — never persisted.)

**Target database model [PROPOSED]:**

- **REQUIRED:** unique constraint on `employees.email` (backs US-03-02 at the DB level).
- **OPTIONAL:** `NOT NULL` DB-level constraints mirroring existing bean validation on `name`/`email` (defense-in-depth; not required by any story, but low-risk and consistent with adding a unique constraint).
- **PENDING DECISION:** if the approved authentication mechanism requires persisted credentials/user records (e.g., a `users`/`app_user` table with username, password hash, and role), that schema is *not yet defined* — it depends entirely on the outcome of the authentication-mechanism decision (Section 17, item 1). No such table is designed here.
- **Explicitly not introduced:** audit fields (`created_at`, `updated_at`, etc.) — GAP-07 remains out of scope.

## 12. API Architecture

**Current endpoints [VERIFIED]** (all unauthenticated today):

| Method | Path | Purpose | Auth (current) | Request | Response | Error behavior (current) |
|---|---|---|---|---|---|---|
| GET | `/api/employees` | List all employees | None | — | `List<Employee>` (JSON array) | N/A |
| POST | `/api/employees` | Create employee | None | `Employee` JSON | Created `Employee` | Spring default 400 on `@Valid` failure |
| GET | `/api/employees/{id}` | Get one employee | None | — | `Employee` | Uncaught `RuntimeException` → **HTTP 500** (not 404) if missing |
| PUT | `/api/employees/{id}` | Update employee | None | `Employee` JSON | Updated `Employee` | Same 500-on-missing behavior as GET by id |
| DELETE | `/api/employees/{id}` | Delete employee | None | — | 200 empty body | No existence check — `deleteById` on a missing id throws `EmptyResultDataAccessException`, currently uncaught |

**Proposed endpoint changes [PROPOSED]** (same paths — no new resource paths invented):

| Method | Path | Purpose | Auth requirement | Role requirement | Request | Response | Error behavior (proposed) |
|---|---|---|---|---|---|---|---|
| GET | `/api/employees?search=&page=&size=` | Paged, optionally filtered list | Authenticated | ADMIN, HR, EMPLOYEE | Query params | `Page<Employee>` | 401 if unauthenticated |
| POST | `/api/employees` | Create employee | Authenticated | ADMIN, HR | `Employee` JSON | Created `Employee` | 400 (validation, incl. duplicate email), 401, 403 |
| GET | `/api/employees/{id}` | Get one employee | Authenticated | ADMIN, HR, EMPLOYEE | — | `Employee` | 404 if missing, 401 |
| PUT | `/api/employees/{id}` | Update employee | Authenticated | ADMIN, HR | `Employee` JSON | Updated `Employee` | 400, 404, 401, 403 |
| DELETE | `/api/employees/{id}` | Delete employee | Authenticated | ADMIN, HR | — | 200/204 | 404 if missing, 401, 403 |

Exact query-parameter names (`search`, `page`, `size`) are a reasonable, low-risk implementation choice consistent with Spring Data conventions — not a business decision requiring a gate, but they are explicitly marked **[PROPOSED]** rather than claimed as existing.

## 13. Frontend Architecture

- **Current [VERIFIED]:** `index.html` renders a static table + modal form; `script.js` fetches the full employee list once per load/save/delete and re-renders the whole table body; `searchEmployees()` hides/shows already-rendered rows; there is no page-size control, no page-navigation UI, and no auth-aware UI (no login form, no role-based hiding of Add/Edit/Delete controls).
- **Proposed [PROPOSED]:** `loadEmployees()` (or equivalent) passes the current search term and page number to the API and renders only the returned page; pagination controls are added to the UI; error responses from the new standardized error contract are surfaced via the existing `showNotification()` toast mechanism. If/when an authentication mechanism is approved, the frontend will need some minimal credential-handling and role-aware UI behavior (e.g., hiding write actions for EMPLOYEE) — the specific UI mechanism depends on the pending auth decision and is not designed further here.
- **Out of scope:** the hardcoded `API_URL` constant in `script.js` is preserved as-is; making it configurable/relative is GAP-11, explicitly out of scope.

## 14. Testing Architecture **[PROPOSED]**

- **Unit tests:** JUnit 5 (already available via `spring-boot-starter-test`) covering validation/business-rule logic (salary, DOB, uniqueness) in isolation.
- **API integration tests:** `@SpringBootTest` + `MockMvc` (or `TestRestTemplate`) covering endpoint behavior — success, validation failure, not-found, unauthorized, forbidden.
- **Cucumber/Gherkin:** feature files expressing the US-05-03 scenarios (search, pagination, changing search criteria) in Given/When/Then form matching the approved acceptance criteria verbatim.
- **Selenium WebDriver:** step-definition implementations driving a real browser against the running application to execute the Gherkin scenarios end-to-end.
- **Test data:** seeded via test setup (e.g., `@BeforeEach`/test fixtures) sufficient to exercise multi-page scenarios; no production data is used.
- **Execution/reporting:** standard Maven Surefire/Failsafe execution; Cucumber's built-in HTML/JSON reporting for evidence, per the capstone's test-evidence requirement.

## 15. Deployment Architecture **[PROPOSED — DESIGN ONLY, NOT PERFORMED]**

Local deployment remains: build the Spring Boot JAR (`mvn package`), run it against a local MySQL instance with credentials supplied via environment variables, serving both the API and the static frontend from the same application on `server.port=8080`. No containerization, cloud deployment, or CI/CD pipeline is introduced — none of that is part of the approved scope. This document does not perform any deployment.

## 16. Non-Functional Considerations

- **Security:** authentication/authorization (EPIC-01), restricted CORS, externalized secrets (EPIC-04) — directly in scope.
- **Maintainability:** a minimal service layer (if introduced) improves testability of EPIC-03/EPIC-06 logic without adding unnecessary layers elsewhere.
- **Performance:** server-side pagination (EPIC-06) avoids transferring the entire employee table on every page load — directly relevant at the dataset sizes this app is likely to see.
- **Scalability:** out of scope beyond what pagination naturally provides; no caching, async processing, or horizontal-scaling design is introduced.
- **Reliability:** centralized exception handling (EPIC-02) makes failure modes predictable for API consumers.
- **Observability:** reduced default logging verbosity (EPIC-04) is the only observability-adjacent change in scope; no new logging/monitoring infrastructure is introduced.
- **Testability:** EPIC-05 directly addresses this; the optional service layer also aids unit-testability of business rules independent of HTTP/persistence concerns.

## 17. Risks

| Risk | Area | Impact | Mitigation |
|---|---|---|---|
| Authentication mechanism undecided | EPIC-01 | Blocks concrete security config/LLD until resolved | Flag as pending; implement other EPICs first per approved order; escalate for decision before EPIC-01 implementation |
| Undefined page size | EPIC-06 | Pagination behavior can't be fully specified/tested | Use a placeholder in design only; require decision before implementation |
| Undefined salary/DOB limits | EPIC-03 | Only "negative salary" / "future DOB" rules can be implemented now | Implement only the confirmed rules; do not add invented bounds |
| Retrofitting security onto an open controller | EPIC-01 | Could break existing untested frontend flows if not sequenced carefully | Follow approved implementation order (exception handling & validation before auth); add tests before/alongside each change |
| Introducing a service layer beyond minimal need | EPIC-03/06 | Risk of scope creep resembling GAP-08 | Keep service methods limited to validation/business-rule and search/pagination logic only; no generic CRUD delegation "for architecture's sake" |
| DB-level unique constraint on existing data | EPIC-03 | If duplicate emails already exist in a running dataset, migration could fail | Note as an implementation-time check; not a redesign concern for this capstone's scope |

## 18. Traceability Matrix

| EPIC | User Stories | Architecture Component | Technical Change |
|---|---|---|---|
| EPIC-01 | US-01-01, US-01-02, US-01-03 | Security layer, Controller | Add Spring Security filter chain + role checks (mechanism pending) |
| EPIC-02 | US-02-01, US-02-02, US-02-03 | Exception handler | Add `@RestControllerAdvice`, custom exceptions, standardized error body |
| EPIC-03 | US-03-01, US-03-02, US-03-03 | Entity, [Service layer], DB schema | Add validation annotations/logic, unique email (app + DB), salary/DOB rules |
| EPIC-04 | US-04-01, US-04-02, US-04-03 | Configuration, CORS config | Externalize credentials, adjust ddl-auto/logging, restrict CORS |
| EPIC-05 | US-05-01, US-05-02, US-05-03 | Test suites | Add unit, integration, and Cucumber/Selenium tests |
| EPIC-06 | US-06-01, US-06-02, US-06-03 | Controller, Repository, [Service layer], Frontend | Add `Pageable`/search query support end-to-end |
