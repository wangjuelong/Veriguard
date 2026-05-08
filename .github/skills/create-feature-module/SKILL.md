---
name: create-feature-module
description: >-
  Scaffolds a complete feature end-to-end: JPA entity, repository, service,
  DTOs, mapper, controller, migration, tests (fixture + composer + integration test),
  and frontend actions/page. Use when asked to create a new feature or module.
---

# Create Feature Module

## Prerequisites

- Entity name (singular, e.g. `PlatformGroup`)
- Table name (plural snake_case, e.g. `platform_groups`)
- Whether tenant-scoped or platform-level
- Fields with types and constraints

## Procedure

### Step 1 — Create the JPA Entity

Location: `veriguard-model/src/main/java/io/veriguard/database/model/`

Follow `Group.java` (tenant-scoped) or `Tenant.java` (platform-level):
- `@ControlledUuidGeneration` for ID
- `@Queryable` on filterable fields
- `@Transient @JsonIgnore ResourceType` field
- Collections initialized as mutable (`new ArrayList<>()`)
- Follow conventions from `database.instructions.md`

### Step 2 — Create the Repository

Location: `veriguard-model/src/main/java/io/veriguard/database/repository/`

```java
public interface {Entity}Repository extends JpaRepository<{Entity}, String>,
    JpaSpecificationExecutor<{Entity}> {}
```

### Step 3 — Add ResourceType + Capabilities

- Add value in `ResourceType.java`
- Add `ACCESS_`, `MANAGE_`, `DELETE_` in `Capability.java` with parent hierarchy

### Step 4 — Create the Service

Location: `veriguard-api/src/main/java/io/veriguard/service/`

- `@Service @RequiredArgsConstructor @Transactional(rollbackFor = Exception.class)`
- CRUD + search with pagination
- JavaDoc on all public methods

### Step 5 — Create DTOs + Mapper

Location: `veriguard-api/src/main/java/io/veriguard/api/{feature}/`

- `{Entity}Input` and `{Entity}Output` as Java `record`
- `{Entity}Mapper` with static `fromInput()` + `toOutput()`

### Step 6 — Create the Controller

Location: `veriguard-api/src/main/java/io/veriguard/api/{feature}/`

- `@AccessControl` + `@LogExecutionTime` + `@Operation` on every endpoint
- CRUD + search endpoints

### Step 7 — Create the Migration

Location: `veriguard-api/src/main/java/io/veriguard/migration/`

- Find next version number in existing migrations
- `CREATE TABLE`, FK constraints, indexes

### Step 8 — Create Test Fixtures + Composer

Location: `veriguard-api/src/test/java/io/veriguard/utils/fixtures/`

- Fixture: `createDefault{Entity}()` with random names
- Composer: extends `ComposerBase`, inner `Composer` class

### Step 9 — Create Integration Test

Location: `veriguard-api/src/test/java/io/veriguard/rest/` or `api/`

- `@Nested @DisplayName` groups, `@WithMockUser`, `assertThatJson`

### Step 10 — Create Frontend Actions + Page

> Follow templates and conventions from [frontend.instructions.md](../../instructions/frontend.instructions.md).

Location: `veriguard-front/src/actions/{feature}/` and `src/admin/components/`

- `{feature}-action.ts` — API calls (CRUD + search)
- `{feature}-helper.d.ts` — TypeScript types (or use auto-generated `api-types.d.ts`)
- `{feature}-schema.ts` — Zod validation schema
- List page with `Queryable` + `DataTable`
- Create/Edit form with React Hook Form + Zod
- Permission guards with CASL (`ability.can(ACTIONS.MANAGE, SUBJECTS.X)`)

### Step 11 — Verify

```bash
mvn spotless:apply
mvn test
cd veriguard-front && yarn lint && yarn check-ts && yarn test
```
