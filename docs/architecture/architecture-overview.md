# Architecture Overview — Employee Management System

**Status:** DRAFT — pending Human Review Gate #3
**Source of truth for scope:** `docs/approved-requirements.md` (Human Review Gate #2 — APPROVED)

## 1. Purpose

This document provides a concise architecture overview for implementing the six approved EPICs (EPIC-01 through EPIC-06) on top of the existing Employee Management System. It distinguishes VERIFIED CURRENT STATE (confirmed by direct repository inspection) from PROPOSED FUTURE DESIGN, and identifies which decisions remain pending human approval.

## 2. Existing System Architecture (VERIFIED CURRENT STATE)

The repository (`com.example.employeemgt`) is a small monolithic Spring Boot 3.3.4 / Java 17 application:

- **Frontend:** Static HTML/JS/Bootstrap served from `src/main/resources/static/` (`index.html`, `script.js`). No build tooling; jQuery + Bootstrap 5 loaded via CDN.
- **Backend:** A single `@RestController` (`EmployeeController`) at `/api/employees` performing CRUD directly against `EmployeeRepository` (a plain `JpaRepository<Employee, Long>`). There is **no service layer**.
- **Data:** One JPA entity, `Employee` (`id`, `name`, `email`, `dob`, transient `age`, `salary`, `status`). No unique constraints beyond the primary key. MySQL via `mysql-connector-j`.
- **Security:** No Spring Security dependency in `pom.xml`. No authentication or authorization exists. CORS is fully open (`@CrossOrigin(origins = "*")`).
- **Error handling:** No `@ControllerAdvice`/global exception handler. Not-found cases throw a plain `RuntimeException`, which Spring maps to HTTP 500, not 404.
- **Validation:** `@Valid` is used on create/update; `Employee` has `@NotBlank`/`@Email` on `name`/`email` only. No uniqueness, salary, or DOB business-rule validation.
- **Configuration:** `application.properties` contains plaintext DB credentials (`<configured username>`/`<configured password>`), `spring.jpa.hibernate.ddl-auto=update`, `spring.jpa.show-sql=true`, and `logging.level.org.springframework=DEBUG`.
- **Search/pagination:** None on the server. `script.js` loads *all* employees in one `GET /api/employees` call and filters visible table rows client-side by string match on name/email.
- **Testing:** A single generated `EmployeemgtApplicationTests.contextLoads()` test. No unit tests, no integration tests, no Cucumber/Selenium.
- **Anomaly (noted, not addressed):** Two Spring Boot main-application classes exist side by side — `EmployeemgtApplication` and `EmployeemanagemntApplication`. Per the approved requirements, cleanup of this duplication is explicitly **out of scope** for this capstone and is only recorded here as an observed fact.

## 3. Proposed Target Architecture (PROPOSED FUTURE DESIGN)

The target architecture keeps the same overall shape — a Spring Boot monolith serving a static Bootstrap/JS frontend — and layers in exactly the six approved capabilities:

- A **security layer** (Spring Security) enforcing authentication and ADMIN/HR/EMPLOYEE role-based authorization on `/api/employees/**`.
- A **centralized exception-handling layer** (`@RestControllerAdvice`) producing standardized error responses for validation, not-found, auth, and unexpected errors.
- **Strengthened validation** on `Employee` (unique email, non-negative salary, non-future DOB), enforced at both the annotation level and the database level where appropriate.
- **Server-side search and pagination** using Spring Data `Pageable`/`Specification`-style querying, replacing the current "load everything, filter in the DOM" approach.
- **Externalized, hygienic configuration** (env-based credentials, safer `ddl-auto`, reduced logging, restricted CORS).
- **Automated test coverage** (JUnit unit tests, Spring Boot API integration tests, Cucumber/Gherkin + Selenium UI tests for search/pagination).

A **minimal internal service layer** is anticipated as a technical prerequisite for EPIC-03 (validation/business rules that don't belong on the entity or controller) and EPIC-06 (assembling search+pagination queries). This is **not** a new EPIC — see [Section 17](#17-open-architecture-decisions) and the LLD for detail.

## 4. Architecture Principles

1. Preserve existing verified behavior unless an approved requirement explicitly changes it.
2. Keep the design proportional to a small CRUD application — no unnecessary enterprise layering.
3. Every design element traces to one of EPIC-01–06 or is flagged as a technical prerequisite.
4. Do not resolve pending human decisions (auth mechanism, HR permission delta, page size, salary/DOB limits) — surface them, don't invent them.
5. No scope creep: GAP-07 (audit fields), GAP-08 (standalone service EPIC), GAP-11 (configurable API URL), and main-class cleanup stay out of scope.

## 5. Major Components

| Component | Status | Notes |
|---|---|---|
| Static frontend (`index.html`, `script.js`) | Existing, modified | Add server-side search/pagination calls (EPIC-06); no other UI framework change |
| `EmployeeController` | Existing, modified | Add pagination/search query params; role-gated write operations |
| Service layer (new, minimal) | Proposed | Only if needed for EPIC-03/EPIC-06 logic; not a new EPIC |
| `EmployeeRepository` | Existing, modified | Add paginated/filtered query methods (`JpaRepository` → `PagingAndSortingRepository` capabilities already inherited) |
| `Employee` entity | Existing, modified | Add validation annotations; DB-level unique constraint on email |
| Security config (new) | Proposed | Spring Security filter chain, role checks; mechanism pending approval |
| Global exception handler (new) | Proposed | `@RestControllerAdvice` + custom exceptions |
| Configuration (`application.properties` / env) | Existing, modified | Externalize secrets, adjust logging/DDL/CORS |
| Test suites (new) | Proposed | JUnit, Spring Boot Test, Cucumber, Selenium |

## 6. Component Responsibilities

- **Frontend:** render employee data returned by the API; submit search/page requests; display auth-driven UI states (e.g., hide write actions for EMPLOYEE) once an auth mechanism is approved.
- **Controller:** HTTP boundary — bind/validate requests, delegate to repository (or minimal service), map results to responses.
- **Service (if introduced):** encapsulate validation/business-rule logic (uniqueness checks, salary/DOB rules) and search/pagination query assembly that doesn't belong in the controller or repository.
- **Repository:** persistence access, including paginated/filtered queries.
- **Security layer:** authenticate requests, enforce role-based access per endpoint.
- **Exception handler:** translate exceptions into the standardized error contract.

## 7. Component Interactions

```
Browser (index.html/script.js)
   │  HTTPS/HTTP fetch (search/page params, credentials)
   ▼
EmployeeController  ──▶  Security layer (authn/authz check)
   │
   ▼
[Service layer, if required]  ──▶  EmployeeRepository ──▶ MySQL
   │
   ▼
Exception handler (on error) ──▶ standardized JSON error response
```

## 8. Technology Stack

**Current (verified):** Java 17, Spring Boot 3.3.4 (Web, Data JPA), Hibernate Validator, MySQL (`mysql-connector-j`), Maven, HTML/JS/Bootstrap 5, jQuery (CDN), JUnit 5 (via `spring-boot-starter-test`, unused beyond scaffold).

**Proposed additions:** Spring Security (mechanism TBD — pending decision), Cucumber-JVM, Gherkin, Selenium WebDriver (per approved testing target in `docs/approved-requirements.md` Section 7).

## 9. Security Architecture Overview

Authentication and role-based authorization are introduced at the Spring Security filter-chain level, gating `/api/employees/**`. ADMIN and HR are authorized for CRUD; EMPLOYEE is restricted to read (GET) operations. The authentication mechanism itself is an **open decision** — see Section 17. CORS moves from `origins = "*"` to an explicit allow-list of approved frontend origin(s).

## 10. Data Architecture Overview

Current: single `employees` table, no uniqueness beyond PK, `ddl-auto=update`. Target: add a unique constraint on `email`; introduce validation preventing negative salary / future DOB. If the approved authentication mechanism requires persisted user/credential data, additional table(s) would be introduced as proposed design — not decided here (see HLD Section 11). No audit fields are added (GAP-07 out of scope).

## 11. API Architecture Overview

Current: `/api/employees` (GET, POST), `/api/employees/{id}` (GET, PUT, DELETE) — all unauthenticated, unpaginated, `CrossOrigin("*")`. Target: same base resource, extended with query parameters for search and pagination on the collection endpoint, and role/authn enforcement on all endpoints. No new resource paths are invented beyond what's needed for the approved stories.

## 12. Frontend Architecture Overview

Current: single static page, client-side DOM filtering, no pagination, hardcoded `API_URL` constant. Target: `script.js` calls the API with search/page query parameters instead of filtering the full in-memory list; hardcoded API base URL is preserved as-is (GAP-11 explicitly out of scope).

## 13. Testing Architecture Overview

Target introduces three layers: JUnit unit tests (validation/business-rule logic), Spring Boot `@SpringBootTest`/`MockMvc` API integration tests (endpoint behavior including auth/error cases), and Cucumber/Gherkin feature files driven by Selenium WebDriver for the UI search/pagination flows (US-05-03).

## 14. Configuration Architecture

DB credentials move to environment variables / externalized configuration (no plaintext secrets in `application.properties`). `ddl-auto` and logging level are set appropriately per deployment environment. CORS is restricted to approved origin(s) instead of `*`.

## 15. Deployment Architecture Overview

No change to deployment topology: single Spring Boot JAR serving both API and static assets, backed by a local/target MySQL instance. This capstone activity does not perform deployment; only local verification is anticipated later, per the Definition of Done.

## 16. Architecture Risks

See consolidated risk list in HLD Section 17 and Implementation Plan Section 12. Highlights: undecided auth mechanism blocks EPIC-01 detailed design; undecided page size and salary/DOB limits block precise validation/pagination LLD; retrofitting security onto an already-open `EmployeeController` risks breaking the existing frontend if not sequenced carefully (mitigated by the recommended implementation order in `docs/approved-requirements.md` Section 8).

## 17. Open Architecture Decisions

1. Authentication mechanism (EPIC-01) — **PENDING HUMAN APPROVAL**
2. Exact HR permissions if different from ADMIN (EPIC-01) — **PENDING**
3. Default pagination/page size (EPIC-06) — **PENDING**
4. Exact salary upper/range limits, if required (EPIC-03) — **PENDING**
5. Exact DOB/age business-rule limits, if required (EPIC-03) — **PENDING**
6. Whether a minimal internal service layer is introduced under EPIC-03/EPIC-06 — recommended, not a new EPIC, technical judgment call (not a business decision, no gate required beyond normal architecture review).

## 18. Traceability to Approved EPICs

| EPIC | Area | Primary Architecture Touchpoints |
|---|---|---|
| EPIC-01 | Authentication & RBAC | Security layer, Controller |
| EPIC-02 | Global Exception Handling | Exception handler, error contract |
| EPIC-03 | Validation & Business Rules | Entity, [Service layer], DB constraint |
| EPIC-04 | Configuration & Security Hygiene | `application.properties`/env config, CORS |
| EPIC-05 | Automated Testing | Test suites (unit/integration/Cucumber+Selenium) |
| EPIC-06 | Server-side Search & Pagination | Controller, Repository, [Service layer], Frontend |

## Current State vs Target State — Summary

| Aspect | Current State (verified) | Target State (proposed) |
|---|---|---|
| Auth | None | Spring Security, ADMIN/HR/EMPLOYEE roles (mechanism pending) |
| Errors | Uncaught `RuntimeException` → HTTP 500 for not-found | Centralized handler → standardized responses, correct HTTP codes |
| Validation | Name/email only | + unique email, salary ≥ 0, DOB not in future |
| Search | Client-side DOM filter over full dataset | Server-side filtered query |
| Pagination | None (loads all rows) | Server-side `Pageable` (size pending) |
| Config | Plaintext DB creds, DEBUG logging, `ddl-auto=update` | Externalized creds, environment-appropriate logging/DDL |
| CORS | `origins = "*"` | Restricted to approved origin(s) |
| Tests | One `contextLoads()` test | Unit + integration + Cucumber/Selenium suites |
