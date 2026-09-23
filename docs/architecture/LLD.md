# Low-Level Design — Employee Management System

**Status:** DRAFT — pending Human Review Gate #3
**Source of truth for scope:** `docs/approved-requirements.md`, `docs/architecture/HLD.md`

All class/package proposals below are **[PROPOSED]** unless marked **[VERIFIED]** (confirmed to already exist in the repository). Nothing in this document has been implemented.

## 1. Purpose

Define concrete, implementation-ready class- and package-level design for EPIC-01 through EPIC-06, staying proportional to the existing small codebase.

## 2. Package Structure

**Current [VERIFIED]:**
```
com.example.employeemgt
├── (root) EmployeemgtApplication.java, EmployeemanagemntApplication.java
├── controller  → EmployeeController.java
├── model       → Employee.java
└── repository  → EmployeeRepository.java
```

**Proposed target:**
```
com.example.employeemgt
├── controller   → EmployeeController.java (modified)
├── service      → EmployeeService.java (new, minimal — EPIC-03/EPIC-06 only)
├── repository   → EmployeeRepository.java (modified: paging/search queries)
├── model        → Employee.java (modified: validation)
├── dto          → (only if warranted — see Section 8)
├── exception    → EmployeeNotFoundException.java, DuplicateEmailException.java
├── security     → SecurityConfig.java (mechanism pending)
└── config       → CorsConfig.java (or inline in SecurityConfig)
```

No packages beyond what EPIC-01–06 require are introduced. `dto` is listed conditionally (Section 8) — it is not automatically justified.

## 3. Class-Level Design

| Class | Responsibility | Key methods | Dependencies | EPIC/Story |
|---|---|---|---|---|
| `EmployeeController` (modified) | HTTP boundary for employee resource | `getAllEmployees(search, page, size)`, `createEmployee`, `getEmployeeById`, `updateEmployee`, `deleteEmployee` | `EmployeeService` or `EmployeeRepository` | EPIC-01, 02, 03, 06 |
| `EmployeeService` (new) | Business-rule validation + search/pagination query assembly | `findAll(search, pageable)`, `create(employee)`, `update(id, employee)`, `delete(id)` | `EmployeeRepository` | EPIC-03, EPIC-06 |
| `EmployeeRepository` (modified) | Persistence + paged/filtered queries | `findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(..., Pageable)`, `existsByEmail`, `existsByEmailAndIdNot` | Spring Data JPA | EPIC-03, EPIC-06 |
| `Employee` (modified) | JPA entity + field-level validation | getters/setters, `@Email`, new `@PositiveOrZero` on salary, custom/annotation check on DOB | — | EPIC-03 |
| `GlobalExceptionHandler` (new) | Centralized exception → standardized response mapping | `handleNotFound`, `handleValidation`, `handleDuplicateEmail`, `handleAccessDenied`, `handleAuthentication`, `handleGeneric` | Spring `@RestControllerAdvice` | EPIC-02 |
| `EmployeeNotFoundException` (new) | Signals missing employee | constructor(id) | — | EPIC-02, US-02-02 |
| `DuplicateEmailException` (new) | Signals email uniqueness violation | constructor(email) | — | EPIC-03, US-03-02 |
| `ApiErrorResponse` (new) | Standardized error body | fields per Section 16 | — | EPIC-02 |
| `SecurityConfig` (new) | Configures authentication + role-based authorization | `SecurityFilterChain filterChain(...)` | Spring Security | EPIC-01 |
| `CorsConfig` (new, or inline) | Restricts CORS to approved origin(s) | `CorsConfigurationSource` | Spring Web | EPIC-04 |

## 4. Controller Design

`EmployeeController` retains its five existing endpoints and their paths (no new resource paths). Changes:

- `getAllEmployees` gains optional `search`, `page`, `size` request parameters and returns `Page<Employee>` instead of `List<Employee>` (EPIC-06).
- `getEmployeeById`/`updateEmployee` stop catching-and-inlining a `RuntimeException`; they let `EmployeeNotFoundException` propagate to `GlobalExceptionHandler` (EPIC-02).
- `createEmployee`/`updateEmployee` delegate uniqueness/business-rule checks to `EmployeeService` rather than the repository directly (EPIC-03), if a service layer is introduced.
- All five endpoints become subject to the Spring Security filter chain; write endpoints (`POST`, `PUT`, `DELETE`) additionally require ADMIN or HR role (EPIC-01). No controller-level code needs to hand-check roles if method-security annotations (`@PreAuthorize`) or matcher-based rules in `SecurityConfig` are used — the specific mechanism is part of the pending auth decision.

## 5. Service/Application Layer

A minimal `EmployeeService` is technically necessary because:

- **EPIC-03** requires "unique email excluding the record being updated" logic and salary/DOB rule checks that go beyond what a bean-validation annotation on `Employee` can express cleanly (annotations validate a single object in isolation; the uniqueness check needs a repository lookup).
- **EPIC-06** requires assembling a `Pageable` query with an optional search predicate — logic that shouldn't live in the controller (HTTP layer) or be duplicated across multiple controller methods.

Proposed services:
- `EmployeeService.create(Employee)` — validates uniqueness/business rules, then saves (EPIC-03).
- `EmployeeService.update(Long id, Employee)` — loads existing, validates uniqueness excluding self, applies business rules, saves (EPIC-03).
- `EmployeeService.findAll(String search, Pageable pageable)` — delegates to a filtered/paged repository query (EPIC-06).

**Explicitly:** this is a technical implementation prerequisite of EPIC-03 and EPIC-06, mapped directly to their stories above. GAP-08 (a standalone service-layer EPIC) is **not** being created — no service methods are added beyond what these two EPICs require (e.g., no generic `findById`/`delete` wrapping is added just for symmetry unless a story needs it).

## 6. Repository Design

**Existing [VERIFIED]:** `EmployeeRepository extends JpaRepository<Employee, Long>` — no custom methods.

**Proposed changes:**
- Add a derived query method for filtered, paged search, e.g. `Page<Employee> findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(String name, String email, Pageable pageable)` — supports US-06-02/US-06-03.
- Add `boolean existsByEmail(String email)` and `boolean existsByEmailAndIdNot(String email, Long id)` to back the uniqueness checks in `EmployeeService` — supports US-03-02.
- Pagination itself requires no new dependency: `JpaRepository` already extends `PagingAndSortingRepository`.

## 7. Entity/Data Model

**`Employee` (modified) [PROPOSED changes on top of VERIFIED current fields]:**

| Field | Current | Proposed change |
|---|---|---|
| `id` | `Long`, PK, IDENTITY | unchanged |
| `name` | `@NotBlank` | unchanged |
| `email` | `@NotBlank @Email` | add `@Column(unique = true)` (DB-level, backs US-03-02) |
| `dob` | `Date`, `@Temporal(DATE)` | add validation rejecting future dates (US-03-03) — exact mechanism (custom `@PastOrPresent`-equivalent or manual check in `EmployeeService`) is an implementation choice, not a business decision |
| `age` | `@Transient`, computed | unchanged |
| `salary` | `Double`, unconstrained | add `@PositiveOrZero` (rejects negative; US-03-03). No upper bound added — **pending decision** |
| `status` | `boolean` | unchanged |

**Authentication/security entities:** if the approved authentication mechanism requires persisted credentials, a `User`/`AppUser`-style entity (username, password hash, role) would be introduced then — **not designed here**, since the mechanism itself is unapproved. This LLD does not speculate on its shape.

**Constraints/indexes:** unique constraint on `employees.email` (required). No audit fields (`created_at`/`updated_at`/etc.) are added — GAP-07 stays out of scope.

## 8. DTO Design

No DTOs are introduced by default. The existing controller already accepts/returns the `Employee` entity directly, and none of the approved stories require hiding/reshaping fields (e.g., no password field exists on `Employee` that would need to be excluded from responses). Introducing request/response DTOs purely for layering would be complexity beyond what EPIC-01–06 require and is avoided per the architecture quality rules.

**Exception:** if the approved authentication mechanism turns out to require a login request/response shape distinct from any entity (e.g., a credentials payload), a small DTO for that specific purpose may be justified at that time — this is deferred until the mechanism is decided and is not part of this LLD.

## 9. Exception Classes

| Exception | Extends | Thrown when | Mapped to |
|---|---|---|---|
| `EmployeeNotFoundException` | `RuntimeException` | `findById`/update/delete target does not exist | HTTP 404 (US-02-02) |
| `DuplicateEmailException` | `RuntimeException` | create/update uses an email already used by another employee | HTTP 400 or 409 (US-03-02) — exact status is an implementation choice consistent with "the request is rejected" |
| (Spring-provided) `MethodArgumentNotValidException` | — | `@Valid` failure | HTTP 400 with field errors (US-02-03) |
| (Spring Security-provided) `AuthenticationException` | — | unauthenticated access to protected endpoint | HTTP 401 (US-01-01) |
| (Spring Security-provided) `AccessDeniedException` | — | authenticated but insufficient role | HTTP 403 (US-01-02, US-01-03) |

## 10. Security Design

- **Authentication:** every request to `/api/employees/**` must be authenticated. **Mechanism is PENDING HUMAN APPROVAL — not selected here.**
- **Authorization:** role checks distinguish ADMIN/HR (full CRUD) from EMPLOYEE (read-only), expressed either via URL-matcher rules in `SecurityConfig` (e.g., permit GET to all three roles, restrict POST/PUT/DELETE to ADMIN/HR) or `@PreAuthorize` annotations on controller methods — either is a valid implementation of the same approved rule; the choice does not require a human gate, only the underlying authentication mechanism does.
- **Roles:** exactly `ADMIN`, `HR`, `EMPLOYEE` — no `MANAGER` or other role is introduced anywhere in security config.
- **Protected resources:** all five `EmployeeController` endpoints.
- **Unauthorized handling:** authenticated request, insufficient role → 403 via `AccessDeniedException` → `GlobalExceptionHandler`.
- **Unauthenticated handling:** no/invalid credentials → 401 via the security filter chain's authentication entry point → `GlobalExceptionHandler` (or Security's own entry point, kept consistent with the standardized error contract).

## 11. Validation Design

- **Annotations:** `@NotBlank`, `@Email` (existing, unchanged); `@PositiveOrZero` (new, salary).
- **Custom validators:** a DOB-not-in-future check — implementable either as a small custom constraint annotation or as an explicit check inside `EmployeeService.create/update`. Either is acceptable; this LLD does not mandate one over the other since it's a low-risk implementation detail.
- **Business-rule validation:** uniqueness (email) and DOB/salary rules live in `EmployeeService`, since they require repository lookups or cross-field/temporal logic beyond what a single annotation expresses.
- **Unique email handling:** `EmployeeService.create` checks `existsByEmail`; `EmployeeService.update` checks `existsByEmailAndIdNot(email, id)` so updating a record without changing its own email is allowed (US-03-02, AC3).
- **Database constraint:** unique index on `employees.email` as defense-in-depth against race conditions between the application-level check and the insert/update.
- **Validation error response:** field errors from `MethodArgumentNotValidException`, and business-rule errors from `DuplicateEmailException`/custom DOB/salary violations, all render through the same standardized error contract (Section 16).

## 12. Search/Pagination Design

- **Request parameters:** `search` (optional string, matched against name/email), `page` (zero-based page index), `size` (page size) — names chosen for consistency with Spring Data conventions; **default value of `size` is a pending decision and must not be hardcoded until approved** (see HLD Section 17, item 3; treat any placeholder in code during development as provisional).
- **Repository query:** `Page<Employee> findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(search, search, pageable)` when `search` is present; `findAll(pageable)` when absent — both via `EmployeeRepository`.
- **Service processing:** `EmployeeService.findAll(search, pageable)` chooses which repository method to call based on whether `search` is blank.
- **Response structure:** `Page<Employee>` serializes with Spring Data's default JSON shape (`content`, `totalElements`, `totalPages`, `number`, `size`, etc.) unless a narrower custom shape is later decided — not redesigning Spring's default here avoids inventing an unnecessary custom contract.
- **Frontend consumption:** `script.js` reads `content` for rows and the paging metadata fields to render page controls, sending `search`/`page`/`size` as query parameters on each request instead of fetching the full collection.

## 13. Configuration Design

- **Database credentials:** replace `spring.datasource.username=<configured username>` / `spring.datasource.password=<configured password>` with `spring.datasource.username=${DB_USERNAME}` / `spring.datasource.password=${DB_PASSWORD}` (or equivalent externalization), sourced from environment variables at runtime. Not implemented in this phase — design only.
- **DDL/logging:** `spring.jpa.hibernate.ddl-auto` and `logging.level.org.springframework` become environment-appropriate rather than hardcoded to `update`/`DEBUG` — e.g., via Spring profiles (`application-local.properties` vs. a non-local profile), or environment-variable overrides. Exact profile names are an implementation detail.
- **Environment variables/configuration strategy:** `.env`-style local development values are kept out of version control (e.g., via a local, git-ignored properties/profile file), while `application.properties` itself contains only placeholders/env-var references, not secrets.

## 14. CORS Design

`CorsConfigurationSource` (or `WebMvcConfigurer.addCorsMappings`) restricts allowed origins to the actual origin(s) that serve the frontend during development/testing (e.g., `http://localhost:8080` if served by the same Spring Boot app, since `index.html` is under `static/`). No arbitrary production domain is hardcoded — the approved-origin value(s) should reflect how the app is actually run, not a guessed deployment URL.

## 15. Testing Design

- **Unit test classes:** e.g., `EmployeeServiceTest` (salary/DOB/uniqueness rule behavior, mocked repository).
- **Integration tests:** e.g., `EmployeeControllerIntegrationTest` using `@SpringBootTest` + `MockMvc`, covering: successful CRUD, 404 on missing id, 400 on invalid/duplicate data, 401/403 once security is in place.
- **Security tests:** authenticated ADMIN/HR can write; authenticated EMPLOYEE cannot write; unauthenticated requests are rejected.
- **Validation tests:** negative salary rejected, future DOB rejected, duplicate email rejected (create and update cases), valid data accepted.
- **Pagination/search tests:** correct page returned for a given page/size; search narrows results; search + pagination combined; no-match returns an empty page.
- **Cucumber features:** `employee_search.feature`, `employee_pagination.feature` (or combined), with scenarios matching the Given/When/Then wording in `docs/approved-requirements.md` Section 5 (US-05-03) exactly — not reworded.
- **Selenium page interactions:** a page-object-style helper for the employee table/search box/pagination controls, driven by Cucumber step definitions against a running instance of the app.

## 16. Error Response Contract

**PROPOSED DESIGN — SUBJECT TO HUMAN REVIEW**

```json
{
  "timestamp": "2026-09-08T10:15:30Z",
  "status": 404,
  "error": "Not Found",
  "message": "Employee not found: 42",
  "path": "/api/employees/42",
  "fieldErrors": null
}
```

For validation failures, `fieldErrors` is populated, e.g.:

```json
{
  "timestamp": "2026-09-08T10:16:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/employees",
  "fieldErrors": [
    { "field": "email", "message": "Please provide a valid email address" },
    { "field": "salary", "message": "must be greater than or equal to 0" }
  ]
}
```

This shape is illustrative and not yet approved as final — field names/format may be adjusted during implementation review, but the presence of status/message/field-level detail satisfies US-02-01/02/03.

## 17. Sequence Flows

**1. Authentication [PENDING mechanism]**
```
Client → EmployeeController endpoint
  → Security filter chain: verify credentials
    → invalid/missing → 401 response
    → valid → continue to authorization check
```

**2. Authorized employee CRUD (ADMIN/HR)**
```
Client (POST/PUT/DELETE, authenticated as ADMIN or HR)
  → Security: role check passes
  → EmployeeController → EmployeeService (validate) → EmployeeRepository → MySQL
  → 200/201 response
```

**3. EMPLOYEE read-only request**
```
Client (GET, authenticated as EMPLOYEE) → Security: role check passes (read allowed)
  → EmployeeController → EmployeeRepository → 200 response
Client (POST/PUT/DELETE, authenticated as EMPLOYEE) → Security: role check fails
  → 403 response (never reaches controller logic)
```

**4. Validation failure**
```
Client (POST/PUT with invalid data) → EmployeeController (@Valid fails)
  → MethodArgumentNotValidException → GlobalExceptionHandler → 400 + fieldErrors
```

**5. Missing employee**
```
Client (GET/PUT/DELETE /{id}) → EmployeeService/Repository: not found
  → EmployeeNotFoundException → GlobalExceptionHandler → 404
```

**6. Duplicate email**
```
Client (POST/PUT with existing email) → EmployeeService: existsByEmail[AndIdNot] = true
  → DuplicateEmailException → GlobalExceptionHandler → 400
```

**7. Search**
```
Client (GET ?search=term&page=&size=) → EmployeeController → EmployeeService
  → EmployeeRepository.findByName...OrEmail...(term, pageable) → MySQL
  → Page<Employee> → 200 response
```

**8. Pagination**
```
Client (GET ?page=n&size=s) → EmployeeController → EmployeeService
  → EmployeeRepository.findAll(pageable) → MySQL
  → Page<Employee> (page n of s-sized pages) → 200 response
```

**9. Unauthorized request**
```
Client (write op, authenticated but wrong role) → Security: role check fails
  → AccessDeniedException → GlobalExceptionHandler → 403
```

## 18. Traceability

| Design element | EPIC | Story |
|---|---|---|
| `SecurityConfig` | EPIC-01 | US-01-01, US-01-02, US-01-03 |
| `GlobalExceptionHandler`, `EmployeeNotFoundException`, `ApiErrorResponse` | EPIC-02 | US-02-01, US-02-02, US-02-03 |
| `Employee` validation, `EmployeeService` uniqueness/business rules, `DuplicateEmailException`, unique index | EPIC-03 | US-03-01, US-03-02, US-03-03 |
| `application.properties` externalization, CORS config | EPIC-04 | US-04-01, US-04-02, US-04-03 |
| Unit/integration/Cucumber/Selenium test classes | EPIC-05 | US-05-01, US-05-02, US-05-03 |
| `EmployeeRepository` paged/filtered queries, `EmployeeService.findAll`, `EmployeeController` query params, `script.js` updates | EPIC-06 | US-06-01, US-06-02, US-06-03 |
