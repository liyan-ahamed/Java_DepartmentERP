# Progress

## Phase: Backend cleanup & stabilization (2026-10-02)

### Done
- Auth: removed `/auth/register`; `POST /users` (HOD, `MANAGE_USERS`) is the only way to create users. `username` is required; unknown roles → 400.
- Tokens: JWTs carry `ver` = `users.token_version`. Logout and deactivation bump the version → all access + refresh tokens revoked (all devices), persisted in DB. In-memory blacklist removed. Deactivated users are rejected by the JWT filter.
- Audit: `AuditSanitizer` redacts passwords/hashes/JWTs/secrets on write and on read. `entity_id` filled (first Long arg → result `id` → `user.id` → acting user). `old_value` no longer written (it held the request, not prior state).
- Roles: `user_roles` is the authorization source of truth; `users.role` is derived inside `User.setRoles/addRole`.
- Removed retired HOD→Staff `/schedules` module (backend + unlinked frontend pages). DB tables `schedules`, `schedule_participants` left untouched.
- Poll reminders: max one per student per poll per 24h; stop after response, after due date, or 7 days after creation when no due date.
- Errors: ownership → 403, bad JSON/enum/date/paging → 400, constraint violations → 409.
- Dashboard: staff = active `ROLE_STAFF`; total students uses the same scope as the breakdowns.
- Consolidated LeetCode Student/Section repositories into `system` repositories (+ `BatchRepository`).
- Removed `/health`, `/ping` (use `/actuator/health`), `PermissionRepository`, unused helpers/queries, `seed_dummy_data.sql`.
- Flyway: `clean-disabled=true`, removed `clean-on-validation-error`.
- `DataSeeder` (demo logins) runs only with `SPRING_PROFILES_ACTIVE=dev`.
- Tests: 30 tests (`mvnw test`) against a separate local DB `erp_db_test` (auto-created from `.env` credentials).

### Decisions
- Logout = sign out everywhere (simplest persistent revocation, no token table).
- Staff schedules removed rather than reconnected (superseded by polls/deadlines).
- New migration: V21 adds `users.token_version` (all users must log in once after deploy).

### Known issues / next steps
- Frontend audit-log page still uses mock data, not `/audit-logs`.

## Phase: Security & data-hygiene follow-up (2026-10-02)

### Done
- V22 permanently clears `old_value`/`new_value` of audit rows that may contain passwords/tokens (rows kept). Local DB: 5 rows cleared, 0 raw JWTs/hashes/passwords left.
- `JWT_SECRET` has no default. Startup fails with "JWT_SECRET is not set" if missing, or "too short" if under 64 bytes. Tokens are signed with HS512 explicitly. Local `.env` has a random dev-only secret (git-ignored); tests generate a random secret per run.
- Demo department data (V10) moved unchanged to `db/dev`, which only the `dev` and `test` profiles load (`spring.flyway.locations`). `DataSeeder` stays dev-only.
- Tests: 36 passing.

### How to run
- Local development DB: `SPRING_PROFILES_ACTIVE=dev` is required (it contains V10; the default profile refuses it with "applied migration not resolved locally: 10").
- Fresh production DB: default profile, set `JWT_SECRET` (64+ random bytes) and DB_* vars. You get schema + roles + permissions only, with no users or students.

### Known issues / next steps
- A fresh production DB has no users, and there is no bootstrap path for the first HOD account (`POST /users` needs an HOD). It must currently be inserted manually.
- Frontend audit-log page still uses mock data, not `/audit-logs`.

## Phase: Gradebook + Internal Assessment backend (2026-10-02)

### Done
- V23 adds `subjects`, `subject_offerings`, `assessments`, `student_grades`, `student_internal_marks` (FKs, CHECKs, unique keys incl. one *active* offering per subject+section+year+semester).
- New packages: `modules/academic` (subjects, offerings, `OfferingAccess` rules) and `modules/gradebook` (assessments, grades, analytics, internal marks).
- Endpoints: `/subjects`, `/subject-offerings`, `/assessments` (+ `/{id}/finalize`, `/{id}/grades` GET/PUT, `/{id}/summary`), `/grades/me`, `/internal-marks/me`, `/subject-offerings/{id}/internal-marks`.
- Lifecycle DRAFT -> FINALIZED (guarded single UPDATE; retry = 409). Finalize notifies class students once (in-app only).
- Audit: subject/offering create+update, assessment create/update/delete/finalize, `GRADE_BULK_UPDATE` (counts only, no marks).
- `GradebookDevSeeder` (dev profile only, runs after `DataSeeder`): CS301 (THEORY) + CS305 (THEORY_CUM_PRACTICAL) for the demo student's section, owned by staff@department.com.
- Tests: 86 passing (50 new).

### Decisions
- HOD manages subjects/offerings; STAFF manage assessments/grades of offerings assigned to them only; HOD is read-only for grading data.
- Offering staff must be an active ROLE_STAFF user (not student). Subject/batch/section/year/semester of an offering are fixed once it has assessments; subject type is fixed once offered.
- Bulk grading validates all rows first and returns every error; nothing is saved if any row fails.
- Students see FINALIZED assessments only; missing grade rows show as `NOT_GRADED`.
- Internal-mark rows are created empty (`PENDING_CALCULATION_CONFIGURATION`) on offering creation and topped up on each finalization. THEORY rows can never hold a practical total (entity guard).
- JSON uses camelCase for the new module (the request example used `studentId`).

### Known issues / next steps
- **Internal-mark calculation is intentionally NOT implemented** — waiting for exact formulas/weightages (and any end-semester dependency). A future `InternalMarksCalculator` should read FINALIZED assessments grouped by type and write totals with status CALCULATED + `calculation_version`.
- No reopening of finalized assessments yet.
- Notifications reach only students with a login (linked by `user_id` or matching email).
- Gradebook/analytics use the current active class list; grades of students who later left the section are kept but not shown there.
- Local dev DB contains one extra finalized "API verification quiz" (CS301) created during manual verification.

## Phase: Batch & section lookup APIs (2026-10-02)

### Done
- `GET /batches` (active batches: id, name, admissionYear, graduationYear, currentYear, active) and `GET /sections[?batchId=]` (sections of active batches: id, name, batchId, batchName, currentYear, active). HOD + STAFF only; STUDENT 403. Read-only.
- Lives in `modules/system` (where Batch/Section already are): `AcademicStructureController`, `AcademicStructureService`, `BatchResponse`, `SectionResponse`. Added `BatchRepository.findByActiveTrueOrderByCurrentYearAscBatchNameAsc`; sections reuse `findAllForActiveBatches`.
- No migration. Tests: 90 passing (4 new). Dev DB: 4 batches, all 10 sections returned with correct batchId.

### Decisions
- `sections` has no `is_active` column, so a section counts as active when its batch is active (`active` mirrors the batch).
- Unknown/inactive `batchId` filter returns an empty list (not 404).

### Next steps
- Frontend: replace the "infer batches from offerings" workaround with `/batches` + `/sections`.

## Phase: Gradebook switched off + Student Achievement & Activity Portfolio (2026-10-03)

### Gradebook feature flag
- `features.gradebook.enabled=${FEATURES_GRADEBOOK_ENABLED:false}` in `application.properties`. Default **off**.
- `@ConditionalOnGradebookEnabled` (`modules/gradebook/config`) on `AssessmentController`, `StudentGradeController`, `InternalMarksController` and `GradebookDevSeeder` (seeder = dev profile AND flag). No `if (enabled)` checks in services.
- Off: `/assessments/**`, `/grades/**`, `/internal-marks/**`, `/subject-offerings/{id}/internal-marks` return 404 for every method (401 without login).
- Still on: `/subjects`, `/subject-offerings`, `/batches`, `/sections`. Creating/updating an offering still creates its empty internal-mark rows (unchanged service behaviour, keeps data ready for re-enabling).
- Nothing deleted: entities, services, repositories, DTOs, V23 tables and all grade data kept. Re-enable: `FEATURES_GRADEBOOK_ENABLED=true` (verified live: 8 assessments, grades and internal marks returned).
- Tests: `GradebookTestBase` sets `features.gradebook.enabled=true`; the old shared fixture moved to `AcademicTestBase`. `SubjectIntegrationTest` now runs with the flag off.

### Portfolio
- V24 `portfolio_entries`: student_id FK, activity_type / level / status CHECKs, end_date >= start_date, `(status = 'DRAFT') = (submitted_at IS NULL)`, decisions need reviewer+time, rejection needs a note, drafts carry no review data. `version` column (optimistic locking). Indexes on student, status, type, level, submitted_at, reviewed_at, reviewed_by.
- Module `modules/portfolio` (controller, service, repository, entity, dto). Reuses Student/User/SubjectOffering repositories, `CurrentStudentResolver`, NotificationService, AuditService, ApiResponse/PagedResponse.
- Endpoints (all under `/api/portfolio`):
  - STUDENT: `POST /entries`, `GET /me` (status, activityType, year filters; paged), `GET /me/summary`, `PUT /entries/{id}`, `DELETE /entries/{id}`, `POST /entries/{id}/submit`.
  - STUDENT/STAFF/HOD: `GET /entries/{id}` (student: own only, 403 otherwise; faculty: in-scope non-drafts, drafts = 404).
  - STAFF/HOD: `GET /review` (default SUBMITTED oldest-first; filters status, activityType, level, batchId, sectionId, search; paged), `POST /entries/{id}/verify` (optional `reviewNote`), `POST /entries/{id}/reject` (`reviewNote` required, 10+ chars; `verificationNote` accepted as alias), `GET /students/{studentId}` (VERIFIED only), `GET /summary` (HOD = department, STAFF = their sections).
- Workflow: DRAFT -> SUBMITTED -> VERIFIED | REJECTED; REJECTED -> edit (stays REJECTED) -> submit again. Students edit/delete only DRAFT/REJECTED. Reviewers never edit content.
- Drafts need only activityType + title. Submission requires organization, description (20+ chars), level, startDate (not in the future). evidenceUrl must be http(s) with a host. No file upload (link only).
- Review scope: HOD = all students; STAFF = students of sections where they have an **active** subject offering. Staff with no active offering see nothing.
- Notifications (in-app only) to the student's login on verify ("Your Hackathon entry "X" has been verified.") and reject ("... needs changes."). Retry/concurrent decision -> 409, no duplicate.
- Audit module `PORTFOLIO`, entity `PortfolioEntry`, actions CREATE/UPDATE/DELETE/SUBMIT/VERIFY/REJECT. Payload: entryId, studentId, activityType, oldStatus, newStatus, reviewerId (decisions). No description, evidence link or note.
- `PortfolioDevSeeder` (dev only, runs when the table is empty): demo student VERIFIED Hackathon + REJECTED Workshop + DRAFT Internship; two classmates VERIFIED Certification + SUBMITTED Competition; one section-B student SUBMITTED Project showcase. Decisions attributed to staff@department.com.
- `GlobalExceptionHandler`: optimistic-lock conflicts -> 409.

### Decisions
- Review fields named `reviewed_by/reviewed_at/review_note` (latest decision, verify or reject); kept on resubmission so the reviewer sees the earlier rejection reason. Audit log holds the full history.
- Level is one enum (widest geographic scope; ONLINE only for online activities with no geographic scope).
- Unknown JSON fields (e.g. `studentId` in a student request) are refused with 400 by the global ObjectMapper.
- Student requests for another student's entry -> 403 (project convention); faculty requests for a draft -> 404.

### Tests
- 137 passing (47 new: 42 portfolio, 5 gradebook feature flag). Gradebook tests run with the flag on and pass.

### Live verification (dev profile, flag off)
- Health UP, V24 applied, gradebook APIs 404, lookup APIs 200, full student -> staff reject -> resubmit -> verify flow, notifications, HOD summary, out-of-scope staff 403, audit rows. Restart with flag on: gradebook APIs + existing data back.

### Permanent dev test data (local DB)
- 6 seeded portfolio entries (above) plus two VERIFIED "Live check: Tech Fest 2026" entries (ids 7, 8) for the demo student, with their notifications and audit rows (the live script ran twice).

### Known issues / next steps
- STAFF review scope depends on subject offerings. On a fresh dev DB with the gradebook off, no offerings are seeded, so staff@department.com sees an empty queue until the HOD creates offerings via `/subject-offerings` (or the gradebook seeder runs once with the flag on).
- Notifications only reach students with a login (linked by `user_id` or matching email).
- Frontend: portfolio UI not built; gradebook UI still exists but its marking APIs are off.

## Phase: Student-facing ERP modules + Gradebook back on (2026-10-09)

### Gradebook (= Score)
- Back ON in the dev profile: `application-dev.properties` -> `features.gradebook.enabled=${FEATURES_GRADEBOOK_ENABLED:true}`. Base default stays off; the flag and `@ConditionalOnGradebookEnabled` are unchanged, so it can still be switched off.
- No second Score backend. Verified live: assessments CRUD, bulk grades, finalize, summary, `/grades/me`, internal marks.
- Internal-mark calculation is still NOT implemented: all 424 rows `PENDING_CALCULATION_CONFIGURATION`, totals null.

### LeetCode roster -> student records (dev DB)
- Before: the dev DB had 0 students matching the 252-row CSV (the roster import had never run).
- With approval, ran the LeetCode module's own importer once (`LEETCODE_SECOND_YEAR_ROSTER`): 252 roster students in batch 2023-2027, sections A-D (63 each), 208 with a URL. The 171 old V10 second-year demo rows were deactivated, not deleted. A backup of `students`/`sections` was taken first.
- The CSV was not changed (same SHA-256 before and after). After seeding and all live checks, every LeetCode column of every second-year row is byte-identical to the snapshot taken right after the import.
- Reconciliation (read-only, logged at dev startup by `LeetCodeRosterReconciliationReport`): 655 active students; 252 linked; 0 unmatched CSV rows; 0 second-year students outside the CSV; duplicate names in the CSV: "Pooja Sri S", "Tharun M" (2 each, different register numbers). The CSV has no register numbers, so matching is by exact name.
- New modules reference `students.id` only. No duplicate student tables.

### Migrations (additive)
- V25 `course_enrollments` | V26 `timetable_slots` | V27 `attendance_sessions`, `student_attendance` | V28 `lesson_plans` | V29 `fee_structures`, `student_fee_accounts`, `fee_payments` + `fee_receipt_number_seq` | V30 `exam_periods`, `exam_registrations`, `exam_registration_subjects` | V31 `exam_results` | V32 `feedback_forms`, `feedback_questions`, `feedback_submissions`, `feedback_responses`, `feedback_answers`.
- FKs, CHECKs (statuses, money/marks ranges, status vs. paid amount, published vs. published_at, ...), unique keys and indexes. No historical migration edited.

### Modules (packages under `modules/`)
- `enrollment`, `timetable`, `attendance`, `lessonplan`, `fees` (incl. receipts), `exams`, `results`, `feedback`.
- Shared helpers: `EnrollmentRoster` (class list of an offering = its ENROLLED active students; used by attendance, exams, results, feedback and the student timetable/lesson-plan views), `AuditRecorder` (short audit summaries), `StudentNotifier` (in-app notices to students with a login, optional cooldown). Gradebook keeps its section-based class list (unchanged).

### Endpoints and roles
- Enrollment `/course-enrollments`: HOD list/filter, `POST` (one student, same batch), `POST /subject-offerings/{id}/enroll-section` (idempotent), `PUT /{id}/status` (ENROLLED<->DROPPED, ENROLLED->COMPLETED). STAFF: list + `GET /subject-offerings/{id}` for own offerings. STUDENT: `GET /me`. No self-enrollment.
- Timetable `/timetable`: HOD `POST`, `PUT /{id}`, `DELETE /{id}` (deactivates), `GET` (filters batch/section/staff/offering/day/active). STAFF: `GET` (own), `GET /me`. STUDENT: `GET /me` (enrolled offerings). Conflicts (same section or same staff, same day, overlapping times, active slots of active offerings) -> 409; touching slots allowed. Writes serialized with an advisory lock.
- Attendance `/attendance`: STAFF (own active offerings) `POST /sessions`, `PUT /sessions/{id}`, `DELETE /sessions/{id}`, `PUT /sessions/{id}/records` (bulk, all-or-nothing), `POST /sessions/{id}/finalize`. HOD + STAFF (own): `GET /sessions`, `GET /sessions/{id}`, `GET /sessions/{id}/records` (NOT_MARKED rows), `GET /subject-offerings/{id}/summary`. STUDENT: `GET /me`. HOD is read-only.
- Lesson plans `/lesson-plans`: STAFF `POST`, `PUT /{id}` (PLANNED only), `POST /{id}/complete|cancel|reopen`. HOD + STAFF (own) `GET` with filters. STUDENT `GET /me`. All three roles: `GET /{id}`, `GET /subject-offerings/{id}/progress` (student must be enrolled).
- Fees `/fees` (HOD only): `POST|GET /structures`, `GET|PUT /structures/{id}`, `POST /structures/{id}/assign` (studentIds | sectionId | whole batch), `GET /accounts` (filters, `outstandingOnly`), `GET /accounts/{id}`, `POST /accounts/{id}/payments`, `POST /accounts/{id}/waive`, `GET /summary`. STUDENT `GET /fees/me`. STAFF: no access.
- Receipts `/receipts`: STUDENT `GET /me`, `GET /{id}` (own only, else 403). HOD `GET` (search) and `GET /{id}`.
- Exams: `/exam-periods` HOD create/update/`open`/`close`, HOD + STAFF read. `/exam-registrations` STUDENT `GET /periods`, `GET /periods/{id}/eligible-subjects`, `PUT /periods/{id}/me`, `POST /periods/{id}/me/submit`, `GET /me`; HOD `GET`, `POST /{id}/approve|reject`; `GET /{id}` for HOD or the owner.
- Results `/results`: STAFF `PUT /subject-offerings/{o}/periods/{p}` (own offering, unpublished rows); HOD + STAFF (own) `GET` and `GET /subject-offerings/{o}/periods/{p}`; HOD `POST .../publish`; STUDENT `GET /me` (published only). No GPA/CGPA.
- Feedback `/feedback`: HOD `POST /forms`, `PUT /forms/{id}` (DRAFT), `POST /forms/{id}/open|close`, `GET /forms`, `GET /forms/{id}/analytics`, `GET /forms/{id}/responses`. STUDENT `GET /forms/me`, `GET /forms/{id}`, `POST /forms/{id}/submit` (once). STAFF: no access.
- `/me` endpoints always resolve the student from the login; unknown JSON fields such as `studentId` are rejected (400).

### Rules
- Attendance (`AttendancePolicy` is the only calculation): attended = PRESENT + ON_DUTY; eligible = PRESENT + ABSENT + ON_DUTY (EXCUSED left out); % = attended / eligible * 100 (2 dp), null when eligible = 0. FINALIZED sessions only. Minimum from `attendance.minimum-percentage=75` (env `ATTENDANCE_MINIMUM_PERCENTAGE`). Finalize fails while any enrolled student is unmarked; nobody is marked ABSENT automatically. Finalized sessions are immutable.
- Fees: NUMERIC(12,2) / BigDecimal everywhere. balance = amount - paid (0 when WAIVED). UNPAID -> PARTIALLY_PAID -> PAID follows the paid amount (DB CHECK). The charge row is locked while paying; a payment cannot exceed the balance. A structure's amount is fixed once assigned.
- Receipts: every payment is a receipt numbered `RCPT-<year>-<6 digits>` from a sequence; UNIQUE.
- Exam registration: eligible = active offerings of the period's term the student is ENROLLED in. Students act only while the period is OPEN and inside the window. DRAFT -> SUBMITTED -> APPROVED / REJECTED (reason required); REJECTED -> edit -> DRAFT -> resubmit. Period: DRAFT -> OPEN -> CLOSED (final).
- Results: PASS/FAIL need marks; ABSENT has no marks or grade; WITHHELD marks optional; marks <= max. The offering's term must match the period's. Published rows are immutable.
- Feedback anonymity: `feedback_submissions` records who submitted (one per form). Answers live in `feedback_responses` / `feedback_answers` with random UUIDs; for anonymous forms they hold no student and no time and have no link to the submission row. Anonymous response lists are shuffled; text answers in analytics are sorted alphabetically. Submissions are not audited.

### Notifications (in-app only)
- Exam period opened, registration approved/rejected, result published, feedback form opened, fee charge assigned, attendance shortage (on finalize; at most one per student per subject per 7 days, `attendance.shortage-notification-cooldown-days`). Gradebook "marks published" is unchanged.

### Audit
- ENROLLMENT: ENROLL, ENROLL_SECTION, STATUS_CHANGE. TIMETABLE: CREATE/UPDATE/DELETE. ATTENDANCE: CREATE/UPDATE/DELETE, BULK_SAVE (counts only), FINALIZE. LESSON_PLAN: CREATE/UPDATE/COMPLETE/CANCEL/REOPEN. FEES: structure CREATE/UPDATE, ASSIGN (counts), PAYMENT (receipt no., amount), WAIVE. EXAMS: period CREATE/UPDATE/OPEN/CLOSE, registration SAVE/SUBMIT/APPROVE/REJECT. RESULTS: BULK_SAVE (counts), PUBLISH. FEEDBACK: CREATE/UPDATE/OPEN/CLOSE.

### Dev seeding (`ErpModulesDevSeeder`, dev profile only, idempotent via marker subject CS201)
- Uses roster students of 2023-2027 sections A and B only. Skips with a warning until the roster import has run.
- Links one demo STUDENT login (`reg2023a058@student.erp.com`, same dev password as the other seed users) to REG2023A058; only `students.user_id` is set.
- Seeds: CS201/CS203/CS205 offerings (staff@department.com, staff_ananya); section A + B enrollments (1 DROPPED); 10 weekly slots; 6 FINALIZED + 1 DRAFT CS201 sessions and 4 CS203 sessions with mixed statuses (some students below 75%); 14 lesson-plan topics (PLANNED/COMPLETED); tuition + exam fee for section A (paid / partly paid / unpaid, 95 receipts); a CLOSED internal period with CS201 results published and CS203 unpublished; an OPEN end-semester period with 10 SUBMITTED/APPROVED registrations; an OPEN anonymous CS201 feedback form with 10 submissions; a DRAFT department survey.

### Tests
- 211 passing, 0 failures (74 new: enrollment 8, timetable 8, attendance 12 + policy 6, lesson plan 6, fees/receipts 8, exams 9, results 7, feedback 8, LeetCode-untouched 1, gradebook dev flag 1). No existing test changed.

### Build / run
- `mvnw package` OK. Dev start from the jar: Flyway validated 32 migrations (V25-V32 applied), `/actuator/health` UP, seeders skip on restart (no duplicates).
- Live API checks: 75/75 passed as HOD, STAFF and the roster student. They deliberately changed some dev data: registration 1 APPROVED, registration 2 REJECTED, the demo student's end-sem registration SUBMITTED, CS203 internal results published, the CS201 draft session finalized, one extra 100.50 payment (RCPT-2026-000096), one extra completed lesson plan, one deactivated Saturday slot, the demo student's feedback submitted.
- `/leetcode/stats` was deliberately not called: it queries LeetCode and writes stats.

### Known issues / next steps
- Fresh dev DB: `LeetCodeRosterImporter` has no `@Order` and runs after the seeders, so module demo data is seeded on the next start after the roster import (the seeder logs a warning).
- Gradebook demo data (CS301/CS305) belongs to batch 2024-2028 section A (Aarav Sharma), not to roster students; the roster demo student has no gradebook marks.
- Changing an offering's section/staff after timetable slots exist is not re-checked for timetable conflicts.
- No scheduled fee-due reminders (only a notice when a charge is assigned). No PDF receipts (all data is in `GET /receipts/{id}`).
- Feedback anonymity is enforced at application/API level; a database administrator could still try to correlate rows through PostgreSQL internals (e.g. transaction ids).
- Notifications reach only students with a login (only REG2023A058 among roster students in dev).
- Frontend not built for these modules.

## Phase: Academic simplification, official periods, curriculum, holidays (2026-10-12)

Backend only; no frontend code changed.

### Official timetable periods
- One central definition: `modules/timetable/period/InstitutionPeriodSchedule` (Java constant, no DB table: the schedule is fixed institution-wide). P1 08:15-09:05, P2 09:05-09:55, Break 09:55-10:10, P3 10:10-11:00, P4 11:00-11:50, P5 11:50-12:40, Lunch 12:40-13:30, P6 13:30-14:15, P7 14:15-15:00, P8 15:00-15:45.
- `GET /timetable/periods` (HOD, STAFF, STUDENT): periodNumber, label, startTime, endTime, type (TEACHING/BREAK/LUNCH), teaching.
- A slot = one teaching period. Requests send `periodNumber` (1-8); times are filled in from the period. Older requests with only startTime/endTime still work if they match a period exactly. Break/lunch, unofficial times, period 0/9, period+times mismatch, inactive offering -> 400. Section/staff overlap rules unchanged (409). A lab = several consecutive slots.
- `GET /timetable` gained `semester` and `weekOf=YYYY-MM-DD`; `GET /timetable/me` gained `weekOf`. With weekOf each slot also carries `date`, `isHoliday`, `holidayName`, `holidayScope` (NATIONAL first). Holidays never change or delete recurring slots. Faculty fields keep their names `staffId`/`staffName`; `room` is the existing free-text column (left empty in new demo slots).

### Cohorts (dev data, `config/CurriculumDevSeeder`)
- Active: 2026-2030 Y1, 2025-2029 Y2, 2024-2028 Y3, 2023-2027 Y4 (derived from the academic year, not hard-coded). `GET /batches` returns exactly these four.
- Decision (user): the LeetCode Second-Year roster stays Second Year. Batch id 2 was RENAMED in place "2023-2027" -> "2025-2029" (admission 2025). No student row changed; sections, enrollments, attendance follow automatically. LeetCode code unchanged (it still finds the Year-2 batch).
- New batches: 2026-2030 (id 5) and an empty 2023-2027 (id 6, sections only; no Semester 7 curriculum supplied). Every active cohort has sections A-D.
- Deactivated (not deleted): 2022-2026, 2021-2025.

### Curriculum
- `config/DevCurriculum` holds Semesters 1-3 exactly as supplied (codes, names incl. Tamil, credits). One Subject per code (COUN, LIB, LC, AT, TT recur); the semester lives on the offering.
- Subject types (user rule): code suffix T = THEORY, J = THEORY_CUM_PRACTICAL, R = PROJECT_BASED (new enum value). Left UNCLASSIFIED (null) for the HOD: 2321SDA101L/201L/301L (L), 2321GEM101B (B), ATT, AT, COUN, LC, LEET, LIB, NPTEL, SS, TT. A null type can be set once later; after that it is fixed as before.
- Offerings (AY 2026-2027): Sem 1 -> 2026-2030 A-D (48); Sem 2 -> 2026-2030 A-D (56, upcoming even term: no enrollments/timetable yet); Sem 3 -> 2025-2029 A-D (48). Staff = the 11 existing @department.com STAFF accounts, least-loaded first (staff@department.com teaches in every cohort). No new faculty.
- Legacy demo offerings CS201 (x2), CS203, CS205 of the roster cohort and their 10 slots DEACTIVATED (attendance/results/exam/feedback rows kept). `/course-enrollments/me` rows now include `offeringActive` (and `credits`) so retired courses can be hidden. CS301/CS305 (gradebook demo, 2024-2028) left active as legacy.

### Enrollments
- Existing Student rows only. Roster students (2025-2029) enrolled in their section's Sem 3 offerings (3024 rows). 40 new, clearly labelled "Demo Student 2026 A01..D10" rows (DEMO2026A01...) in 2026-2030, which had no students, enrolled in Sem 1 (480 rows). New login: demo2026a01@student.erp.com (same dev password as the other seed users).

### Demo timetable (sample data, not an official timetable)
- 208 active slots (Sem 1: 96, Sem 3: 112), Mon-Fri, periods 1-8 only, credits = periods per week (0-credit = 1). No section or staff collisions. Only offerings without slots are filled, so HOD edits survive restarts.

### Subjects / offerings API
- `credits` (int >= 0, null = not recorded) on subject requests/responses and in offering responses. `subjectType` optional (null = unclassified).
- Filtering reused (no new list endpoints): `/subject-offerings?batchId&semester&sectionId&subjectId&active` (STAFF always scoped to own), `/sections?batchId`, `/subjects?subjectType`.
- New `GET /subject-offerings/{id}/summary` (HOD or assigned STAFF): enrollmentCount, finalizedAttendanceSessions, attendancePercentage (AttendancePolicy formula), finalizedAssessmentCount, averageAssessmentPercentage, publishedResultCount, passCount, failCount. One aggregate SQL query; no internal marks.

### Holidays
- Module `modules/holiday`; table `holidays` (V34), unique (holiday_date, scope, name); scopes NATIONAL, TAMIL_NADU.
- Source research: Nager.Date has no India; Calendarific covers Indian states but needs a key and its classification could not be verified without one; the authoritative lists are PDFs (no API). So the source is a reviewed dataset per year: `src/main/resources/holidays/<year>.json`.
  - NATIONAL = the 14 compulsory central-government holidays, DoPT O.M. F.No.12/2/2023-JCA (03-07-2025), cross-checked against a CGEWCC copy of the O.M.
  - TAMIL_NADU = TN G.O.(Ms.) No.708 (Nov 2025): 23 of 24 days, cross-checked against two published copies. Left out: 01-04 Annual Closing of Bank Accounts (banks only). Islamic dates are subject to moon sighting.
- Sync (`HolidaySyncService`): idempotent upsert per scope (same date+name kept; moved date / renamed holiday updated in place; new ones inserted; rows of a scope removed only after a successful read that returned that scope). Source missing/unreadable -> cache untouched (NO_DATA / SOURCE_UNAVAILABLE). Runs at startup and weekly (Mon 03:00) for the current and next year; `POST /holidays/sync?year=` is HOD only. `GET /holidays?year&scope&dateFrom&dateTo` for HOD/STAFF/STUDENT.
- Config: `HOLIDAYS_DATASET_LOCATION` (default classpath:holidays/; point at a folder to add a year without a rebuild), `HOLIDAYS_SYNC_ENABLED`, `HOLIDAYS_SYNC_CRON`. No API keys.
- 2027: no file yet (the TN G.O. for 2027 usually comes out Nov/Dec 2026) -> sync reports NO_DATA. Add `holidays/2027.json` once the O.M./G.O. are published.

### Lesson Plans
- `features.lesson-plans.enabled=${FEATURES_LESSON_PLANS_ENABLED:false}` (all profiles). `@ConditionalOnLessonPlansEnabled` on `LessonPlanController`; lesson-plan dev seeding checks the flag. Off: `/lesson-plans/**` -> 404. Service, entity, table and data kept.

### Migrations (additive)
- V33: `subjects.credits` (CHECK >= 0), `subject_type` nullable, type CHECK adds PROJECT_BASED. V34: `holidays`. No demo data in Flyway.

### Tests
- 243 passing, 0 failures (32 new: periods 4, timetable periods/weekly/holidays/roles 7, holiday dataset 3, holiday sync/API 5, curriculum seeder 7, course filtering/credits/summary 4, lesson-plan flag 2).
- Existing tests changed for intended behaviour changes (not weakened): TimetableIntegrationTest times moved onto official periods (the old 09:30-10:20 overlap case now crosses the break, so the section conflict uses the same period); SubjectIntegrationTest no longer expects a missing subjectType to be 400 (that case replaced by negative credits); LessonPlanIntegrationTest runs with the flag on.

### Live verification (jar, dev profile, port 8081, same dev DB)
- Health UP, 34 migrations validated, seeder report all zeros on restart (idempotent), holiday sync 2026: 37 unchanged, 2027: NO_DATA. 36/36 API checks passed as HOD, staff@department.com, reg2023a058 (roster, Sem 3) and demo2026a01 (Sem 1).
- LeetCode: student table (identity + all LeetCode columns) byte-identical before/after the live run; the 252 active roster rows equal the CSV name+URL exactly; no pre-existing student row has updated_at after 2026-10-09; CSV unchanged.

### How the dev DB got migrated
- The IDE-run instance on :8080 uses devtools and restarted itself when the code was compiled, so V33/V34 and the curriculum seeder were first applied by that instance (2026-10-11 ~22:29 DB time), before a manual backup was taken. The backup taken afterwards (post-migration) is in the session scratchpad only.

### Known issues / next steps
- Frontend: use `/timetable/periods`, `weekOf`, `/holidays`, `credits`, `offeringActive`; hide Lesson Plans; handle `subjectType` null and PROJECT_BASED.
- The timetable conflict check ignores semester: before timetabling Sem 2 (even term), deactivate the Sem 1 offerings/slots.
- Gradebook demo offerings CS301/CS305 (2024-2028, labelled semester 1) are still active legacy data.
- Changing an offering's section/staff after timetable slots exist is still not re-checked for conflicts.
- `hibernate.jdbc.time_zone=UTC` stores TIME columns shifted by -5:30 (08:15 is saved as 02:45) and reads them back correctly through JPA. Raw SQL on `timetable_slots` times must allow for this (pre-existing behaviour).

### CS201 Data Structures removed (2026-10-12, user request)
- Dev DB (one transaction, after a backup in the session scratchpad): subject CS201, its 2 offerings (2025-2029 A/B, already inactive) and every dependent row: 126 enrollments, 7 timetable slots, 7 attendance sessions + 441 marks, 126 empty internal-mark rows, 63 exam results, 11 exam-registration subject lines (no registration left empty), 9 lesson plans, the "Mid-semester feedback: CS201" form with its questions, 11 submissions, 11 responses and 44 answers. Audit logs kept. Students/LeetCode untouched (snapshot identical).
- Code: `ErpModulesDevSeeder` no longer creates CS201; its idempotency marker is now CS203. On a fresh dev DB its course-feedback demo targets CS203, CS203 internal results are published and CS205 results unpublished. `CurriculumDevSeeder` legacy list is CS203/CS205.
- Tests: 243 passing.

## Phase: Backend gaps of the latest frontend pass (2026-10-12)

Backend only; no frontend code changed.

### Holidays: college calendar is authoritative
- New scope `COLLEGE` + column `category` (V35). File `holidays/college/2026.json` = the 24 college holidays (incl. 2026-01-23 "Project Day - Classes Suspended", 2026-04-23 "Legislative Assembly Election 2026"), category GENERAL_HOLIDAY. Source class `CollegeCalendarSource`; location `HOLIDAYS_COLLEGE_CALENDAR_LOCATION` (default classpath:holidays/college/).
- Effective calendar (`HolidayService`): a year with COLLEGE rows uses only those; other years use one row per date, TAMIL_NADU before NATIONAL. Used by `GET /holidays` (no scope), timetable `weekOf`, attendance day view and staff dashboard. `?scope=NATIONAL|TAMIL_NADU|COLLEGE` returns stored rows (provenance). NATIONAL/TAMIL_NADU rows for 2026 stay stored.
- Sync runs every source separately; each source only writes/removes rows of the scopes it owns (`HolidaySource.scopes()`), so the government-list sync can never overwrite or delete college rows. Response adds `sources[]` per source; top-level status SOURCE_UNAVAILABLE if any source failed, else SYNCED/NO_DATA.
- Responses add `category`. 2027: no file yet -> NO_DATA.

### Attendance (on top of AttendanceSession / StudentAttendance; nothing replaced)
- `GET /attendance/day?date=&batchId=&semester=&sectionId=`: classes from the recurring timetable + the session recorded for each (id, status). HOD: sectionId required (400 otherwise); STAFF: own offerings only (no staffId parameter); STUDENT: own enrolled classes with `myAttendanceStatus` (PRESENT/ABSENT/ON_DUTY/EXCUSED from FINALIZED sessions, else NOT_RECORDED; drafts are hidden from students). Holidays: `holiday`, `holidayName`, `holidayCategory` on the day and each class; slots never removed, no sessions invented.
- Class detail reuses `GET /attendance/sessions/{id}/records` (HOD read-only, unchanged).
- `GET /attendance/me/summary` (STUDENT): per active enrolled subject (totalClasses, present, absent, onDuty, excused, eligibleClasses, attendedClasses, attendancePercentage, minimumPercentage, belowThreshold) + `overall` from summed counts. `GET /attendance/me` unchanged.
- `GET /attendance/summary?batchId=&semester=&sectionId=` (HOD: sectionId required; STAFF: own offerings): per subject classesConducted, studentCount, present/absent/onDuty/excusedRecords, eligible/attendedAttendanceRecords, averageAttendancePercentage (= attended records / eligible records for the class, never a mean of student %), studentsBelowThreshold, minimumPercentage; `overall` the same over all listed subjects (students below threshold judged on their combined counts).
- Queries: a fixed number of grouped queries per request (offerings, slots, sessions on the date, counts grouped by offering/student/status, enrolled pairs); no per-student or per-session queries. Same AttendancePolicy formula everywhere.

### Self-service account settings
- `PUT /auth/me/email` {currentPassword, newEmail} and `PUT /auth/me/password` {currentPassword, newPassword (8-100)}. Wrong current password -> 400 "Current password is incorrect"; invalid -> 400; email in use (case-insensitive) -> 409; unknown fields (e.g. userId) -> 400; unauthenticated -> 401.
- Both increment token_version (all access + refresh tokens revoked) and return `{reauthenticationRequired: true, user}`.
- Audit: SELF_EMAIL_CHANGED (userId, oldEmail, newEmail), SELF_PASSWORD_CHANGED (userId only). No passwords or hashes (sanitizer still applies).
- `/auth/me` unchanged: `roles: [...]` (now always one element).

### Fees: Year templates, breakdown, concessions
- V36: `fee_structures.student_year` (1-4, template charge, no academic year, not tied to a batch); `student_fee_accounts.concession_amount` (0 <= concession <= amount); paid/status CHECKs now use payable = amount - concession.
- Existing model kept: one fee structure = one particular; one student charge per particular with its own payments/receipts (item-level paid amounts already existed, no allocation model needed).
- Dev seed (`FeeTemplateDevSeeder`, idempotent): Year 1 = Tuition Fee 130,000 + Placement Value Added Course 25,000 + Book Fee 25,000 + e-learning 25,000 = 205,000; Year 2 = Tuition Fee 130,000 + e-learning 25,000 + Culturals and Events Fee 1,525 = 156,525. Concession 0. Charged to the active students of the cohort currently in that year (dev: 40 Year-1 demo students x 4, 252 Year-2 roster students x 3 = 916 charges). Year 3/4: nothing configured.
- API: `GET /fees/templates` (HOD; years 1-4, `configured=false` for 3/4), `GET /fees/students/{studentId}` (HOD, same shape as /fees/me), `PUT /fees/accounts/{id}/concession` (HOD; 400 above actual or below paid; WAIVED -> 409). `/fees/me` adds `totalConcession`, `totalPayable`, `years[]` (year, label, totals actual/concession/payable/paid/balance, itemBreakdown with particular, actualAmount, concessionAmount, payableAmount, paidAmount, balanceAmount, status). Charges add concessionAmount, payableAmount, studentYear. Structures accept `studentYear` instead of `academicYear`; assigning a year-template charge without a target charges the cohort in that year.
- Audit: structure update action is now FEE_STRUCTURE_UPDATED; CONCESSION on concession changes.

### Marks-entry deadlines + staff dashboard
- V37: `assessments.marks_entry_due_at` TIMESTAMPTZ. `PUT /assessments/{id}/marks-entry-deadline` {marksEntryDueAt | null} HOD only (gradebook feature flag, on in dev); 409 once finalized; only the deadline changes. Audit MARKS_ENTRY_DEADLINE_UPDATED. Assessments return `marksEntryDueAt`.
- `GET /dashboard/staff` (STAFF only): `upcomingClasses` (own timetable, next 14 days from now, chronological, institution holidays skipped and listed in `skippedHolidays`), `thingsToComplete` (computed, nothing stored): ATTENDANCE_FINALIZATION per draft session; MARKS_ENTRY per DRAFT assessment with a deadline whose gradebook class list (active students of the section) still has students without a GRADED/ABSENT/EXEMPTED row: expectedCount, remainingCount, dueAt, status OVERDUE / DUE_SOON (within 48 h) / PENDING. Disappears when remaining = 0 or the assessment is finalized.
- Config: `dashboard.staff.upcoming-days=14`, `dashboard.staff.marks-entry-due-soon-hours=48`. Term start/end dates: NOT stored anywhere, so `termDatesConfigured=false` and a fixed horizon is used.

### Single role + Users API
- V38: normalizes roles (linked student -> STUDENT, else HOD, else STAFF; ambiguous -> migration fails), then UNIQUE(user_roles.user_id); users.role mirror kept. Dev DB: no account needed normalizing (all 18 already had exactly one role).
- `User.addRole` replaces the role; `setRoles` requires exactly one. Create/update still take `roles: [...]` but exactly one (`[]` or 2+ -> 400; `STAFF` and `ROLE_STAFF` both accepted; omitted on create keeps the old STAFF default). Role change audited as USER_ROLE_CHANGED (oldRole/newRole).
- All `/users` endpoints HOD-only (STAFF/STUDENT 403). `GET /users?role=HOD|STAFF|STUDENT&batchId=&sectionId=&search=&active=` filtered in SQL; responses add `role`, `studentId`, `registerNumber`, `batchId`, `batchName`, `sectionId`, `sectionName` (students only), loaded in one query.

### Portfolio Analytics flag
- `features.portfolio-analytics.enabled=${FEATURES_PORTFOLIO_ANALYTICS_ENABLED:false}`. `GET /portfolio/summary` moved unchanged into `PortfolioAnalyticsController` (conditional) -> 404 when off. Student portfolio, `/portfolio/me/summary`, review queue, verify/reject, verified student portfolio unchanged. Analytics tests run with the flag on.

### Migrations (all additive)
- V35 holidays category + COLLEGE scope; V36 fee year templates + concessions; V37 assessment marks-entry deadline; V38 single role per user.

### Data changes (dev DB, backup `erp_db_before_phase3.dump` in the session scratchpad, taken before any change)
- Old demo fees deleted (user-approved): structures "Tuition Fee 2026-2027 (Semester 3)" (45,000) and "Semester Exam Fee (Semester 3)" (1,500), their 126 charges and 96 receipts (95 seeded + RCPT-2026-000096 from an earlier live check). Old seeder no longer creates fees.
- New: 7 fee template structures + 916 charges (above); 24 COLLEGE holiday rows.
- Roles: no account changed.
- Live check left one deactivated throwaway user (id 19, live.settings.*.new@department.com) used to test email/password change; assessment 6 deadline was set and cleared again.

### Tests
- 276 passing, 0 failures (33 new: college calendar 3, attendance day/summary 9, account settings 4, users/single role 6, fee templates 4, staff dashboard + deadlines 5, portfolio flag 2).
- Existing tests adapted for intended changes: HolidayIntegrationTest mocks the official-dataset source (two sources now) and expects the de-duplicated effective calendar (4 instead of 5 for its year); TimetablePeriodIntegrationTest weekOf expects the single effective holiday on a date; PortfolioReviewIntegrationTest and PortfolioSummaryIntegrationTest run with the analytics flag on.

### Live verification (jar, dev profile, :8081, same dev DB)
- Health UP, 38 migrations validated, seeders idempotent (all zero), 54/54 API checks passed as HOD, staff@department.com, reg2023a058 (Year 2) and demo2026a01 (Year 1).
- LeetCode: student table (identity + all LeetCode columns) identical before/after; no student row updated since 2026-10-11; CSV unchanged.

### Notes / next steps
- The IDE-run :8080 dev instance auto-restarts on every file save and applied V35-V38 seconds after the files were written (the backup had been taken first). Its `mvnw spring-boot:run` process was found stopped at the end of the session (not stopped by the agent); Docker Desktop had stopped too.
- Frontend can now enable: settings, attendance day/summary views, staff dashboard, marks deadlines, user role filters, fee breakdown, holiday category.
- Term dates are still unconfigured; Year 3/4 fee templates are unconfigured.

## Phase: Curriculum requirements, registration status, section roster, catalog filter, holiday calendar management (2026-10-21)

Backend only; no frontend code changed. Not committed. API agreed with the frontend agent in the shared contract `Front end/erp_java-frontend/docs/ERP_API_CONTRACT.md` (sections A-E; backend marked them implemented on :8080, frontend to verify).

### Migrations (additive, no historical migration edited)
- V39 `curriculum_requirements` (batch + semester, UNIQUE, @Version), `curriculum_elective_groups` (required_selections >= 1, name unique per curriculum, deferred), `curriculum_subjects` (elective_group_id NULL = compulsory; UNIQUE(curriculum_id, subject_id) so a subject is in one place only; composite FK keeps an option inside a group of the same curriculum). Nothing seeded.
- V40 `holiday_calendars` (calendar year, DRAFT/PUBLISHED, published_at/by, CHECK published <=> published_at); holidays + `end_date` (same year, < 60 days), `description`, categories + NATIONAL/STATE/INSTITUTIONAL/ACADEMIC_BREAK/OTHER, PUBLIC_HOLIDAY only on government rows, unique college name per year (case-insensitive), `last_synced_at` nullable; `holiday_batches` (cohort applicability, none = everyone). Every year with college rows (2026) got a PUBLISHED calendar.
- Dry-run on a restored copy first (all constraint checks behaved). Backup before any change: `erp_db_before_curriculum_holidays.dump` (session scratchpad), verified by restoring into a temp DB and comparing row counts. The :8080 devtools instance applied V39/V40 seconds after the files were saved (expected).

### Endpoints
- `GET /sections/{id}/students` (HOD any; STAFF only sections with an active offering of theirs; paged, size <= 200, `active`, `search`, register-number order). Replaces the frontend's internal-marks workaround; independent of the gradebook flag.
- `GET|PUT|DELETE /curricula/batches/{batchId}/semesters/{semester}` (GET HOD+STAFF, writes HOD). Whole-document replace with `version` (409 stale), group ids kept, all errors at once (`electiveGroups[i].field: ...`). Rules: at least one compulsory subject or group; subjects exist, active and have an active offering for that batch + semester; no duplicates; one place per subject; groups >= 2 options, 1 <= requiredSelections <= options; limits 30/10/30. Audit CURRICULUM_CREATED/UPDATED/DELETED.
- `GET /course-enrollments/registration-status` (+ `/summary`, `/students/{id}`), HOD only. Filters batchId*, semester*, sectionId, registrationStatus, search, page, size (<= 200). One row per active student (zero enrollments included); filters before paging; fixed number of queries.
- `GET /subjects` + `semester`, `batchId` (EXISTS on active offerings, no duplicate rows) and `offeredIn[]` on every subject response.
- Holidays: `GET /holidays/calendars/{year}`, `POST /holidays/calendars/{year}/publish|unpublish`, `POST /holidays`, `PUT|DELETE /holidays/{id}` (HOD writes). `GET /holidays` gains `batchId` and new item fields (startDate, endDate, description, batchIds, calendarStatus, updatedAt; `date` and `lastSyncedAt` kept).

### Rules / decisions
- Registration (`RegistrationEvaluator`, the only implementation): counts = ENROLLED or COMPLETED on an active offering of the cohort + semester. NOT_REGISTERED when none (even without a curriculum); REQUIREMENTS_UNKNOWN when registrations exist but no curriculum; ALL_REGISTERED only when every compulsory subject and every group (judged separately) is satisfied; extras never block.
- Holidays (user-confirmed): effective calendar = PUBLISHED college holidays only, filtered by cohort. Government lists are reference only (explicit `scope=`), never in the default list and never affecting timetable `weekOf`, `/attendance/day` or `/dashboard/staff`. The old government fallback is gone. Drafts are HOD-only and affect nothing.
- Calendar year (Jan-Dec), not academic year. Ranges cannot cross a year. Published years can be edited directly (audited: HOLIDAY_CREATED/UPDATED/DELETED, CALENDAR_PUBLISHED/UNPUBLISHED).
- College calendar file: imported only for a year without a calendar (creates it PUBLISHED); afterwards the sync reports that source as `SKIPPED`, so it never overwrites HOD edits. New per-source status value SKIPPED.
- Categories are not remapped: existing college rows stay GENERAL_HOLIDAY.

### Tests (focused only, per instructions)
- 62 run, 0 failures: new RegistrationEvaluatorTest (9), CurriculumIntegrationTest (4), HolidayCalendarManagementIntegrationTest (5), SectionRosterAndCatalogFilterIntegrationTest (2); existing classes in touched areas re-run.
- Existing tests changed for the intended holiday behaviour (not weakened): HolidayIntegrationTest reads government rows via `scope=`; TimetablePeriodIntegrationTest uses college rows + a published calendar and asserts government rows are ignored; AttendanceDay/StaffDashboard tests publish their year's calendar; CollegeHolidayCalendar test also asserts SKIPPED on resync. Helper `publishHolidayCalendar(year)` in ErpModulesTestBase.
- Flyway: V39/V40 applied cleanly to erp_db_test during the run. Full suite not run.
- Live read-only checks on :8080: 13/13 (roster 63 students; all 63 REQUIREMENTS_UNKNOWN; sem-3 catalog 12 subjects; 2026 PUBLISHED with 24 holidays; government rows only by scope; STAFF publish = 403).

### Known issues / next steps
- No curriculum is configured yet: every class shows REQUIREMENTS_UNKNOWN / NOT_REGISTERED until the HOD saves one (by design; no authoritative classification available).
- The migrated 2026 calendar's `publishedAt` reads 5:30 h ahead (DB-default NOW() + `hibernate.jdbc.time_zone=UTC`, the known pre-existing shift); `publishedBy` is null for it.
- The deferred group-name constraint is only checked at commit, which rolled-back tests never reach; the service checks names first.
- IDE auto-build writes "Unresolved compilation" stub classes on errors and Maven then skips those files: check `target/classes` for stubs (or touch the sources) before trusting an incremental `mvnw.cmd compile`. Only `mvnw.cmd` exists (no `mvnw`).
- Frontend: verify A-E against :8080 and set IMPLEMENTED in the contract; stop using `/subject-offerings/{id}/internal-marks` as a class list.

## Phase: Staff academic selector, read-only results, COE role, assessment workflow (2026-10-21)

Backend only. No frontend code changed and nothing committed. Agreed in the shared contract, sections G–L: the frontend proposed G–J, the backend confirmed them with edits and added K and L.

### User decisions (this session)
- **COE role:** a new COE (Controller of Examinations) role enters end-semester marks. STAFF can only read them.
- **SUPPLEMENTARY:** blocked for STAFF just like END_SEMESTER.
- **Current semester:** calculated from the term calendar.
  - Year 1: Sep–Dec / Jan–Apr.
  - Years 2–4: odd semester Jun–Nov, even semester Jan–Apr.

### Migration (additive)
- V41 `add_coe_role`: `ALTER TYPE dept_role ADD VALUE 'COE'` + `roles.ROLE_COE`. No permissions are granted and no user gets the role.
- Safety steps:
  - Dry-run in a rolled-back transaction first.
  - Backup before saving the file: `erp_db_before_coe_role.dump` in the session scratchpad (47 tables, DB at V40).
  - The :8080 devtools instance applied V41 at 07:31, right after the backup (expected).
  - Also applied to `erp_db_test` by the test run.

### Endpoints
- **Academic selector:**
  - `GET /batches` + `currentSemester`, `currentAcademicYear`, `termStatus` (`AcademicCalendar`, the only definition).
  - New `GET /subject-offerings/teaching-terms`: batch → semesters (CURRENT / PREVIOUS / UPCOMING) of my offerings; HOD sees all.
  - Classes use the existing `GET /subject-offerings?batchId&semester`.
- **Results:**
  - `PUT /results/subject-offerings/{o}/periods/{p}`:
    - END_SEMESTER / SUPPLEMENTARY periods → COE only (STAFF/HOD 403);
    - INTERNAL → assigned STAFF (unchanged), COE 403.
  - Publish: HOD only (unchanged).
  - COE can read `/results`, sheets and `/exam-periods`.
  - New read-only `GET /results/by-exam?batchId&semester&exam=END_SEMESTER|CAT_1|CAT_2`:
    - STAFF see published end-semester rows only;
    - CAT = FINALIZED gradebook assessments only;
    - HOD sees all; STAFF their own offerings.
- **Assessments:**
  - `GET /assessments` + `batchId`.
  - New `GET /assessments/overview?batchId&semester` (by offering, statistics per assessment, `readyToFinalize`).
  - `PUT /grades` entry with `status: null, marks: null` clears a grade; response adds `clearedCount`.
  - New `POST /assessments/{id}/grades/import/preview` and `POST /assessments/{id}/grades/import` (`confirmOverwrite`).
  - Finalize → 400 while any class-list student lacks a valid mark / ABSENT / EXEMPTED.

### Rules / decisions
- **One rule set for marks:**
  - `GradeRules`: 0..max, at most 2 decimals, absent/exempted have no marks.
  - `AssessmentStatistics`: GRADED marks only; absent is never zero.
  - Both are used by the summary, overview, import and finalize.
- **Offering scope:** `OfferingScope` handles cohort + semester. HOD sees all; STAFF their own. Active and inactive offerings are both included (history).
- **Not added:**
  - `subjectAcronym` (nothing is stored);
  - `studentCount` on every offering (overview `classSize` / roster `total_elements` instead);
  - `finalizedBy` (the audit log has it).
- **Import:**
  - All or nothing.
  - Unmatched / duplicate / invalid rows → 400.
  - Overwrites need `confirmOverwrite` (else 409).
  - Blank rows never clear a grade; stored remarks are kept.
  - Audited as `GRADE_IMPORT` with counts only.
- **Kept as they are:** historical assessments finalized before the new rule ("API verification quiz", CS301 "CAT 2").
- **Portfolio:** no backend change. No endpoint lists activity types, and no types or entries were touched.

### Assessment test data finding (not modified)
- **Assessment 9 "[QA AUDIT] Assessment A (edited)":**
  - Created on 2026-10-02 (audit 161).
  - Edited on 2026-10-02 (audit 166) and 2026-10-11 (audit 270).
  - All of these came from headless-Chrome browser QA runs as staff@department.com via :8080 on the dev DB.
- **Other leftovers from the same QA run:**
  - user 15 `qa.audit.temp` (active STAFF);
  - inactive subjects 3 / 4 `QA-AUDIT-X…`.
- These are not seed data and not backend tests (those use `erp_db_test`, rolled back).
- **Rule recorded in contract §L:** live checks on :8080 are read-only; writes need the user's OK.

### Tests (focused, per instructions)
- First run: 13 classes, 75 tests, 0 failures. New:
  - `AssessmentCalculationTest` (3);
  - `AcademicCalendarTest` (3);
  - `EndSemesterResultAuthorizationIntegrationTest` (4);
  - `AssessmentWorkflowIntegrationTest` (5).
- Re-run after the `/batches` change: `AcademicCalendarTest` and `AcademicStructureIntegrationTest`, 7/7.
- No "Unresolved compilation" stubs. Full suite not run.
- **Existing tests adapted to the new finalize rule (not weakened):**
  - `GradebookNotificationAuditIntegrationTest` and `InternalMarksIntegrationTest` now give every class student a mark or ABSENT before finalizing.
- **Live read-only checks on :8080:**
  - teaching-terms: 2026-2030 → 1, 2025-2029 → 3, 2024-2028 → 5;
  - overview;
  - CAT_1 view;
  - STAFF PUT on the END_SEMESTER period → 403. That check used a fake student id; 0 rows exist for that period.

### Known issues / next steps
- No COE account exists. The HOD creates one with `POST /users` and `roles: ["COE"]`. The frontend needs COE navigation and handling of `ROLE_COE`.
- `CurriculumDevSeeder` still uses its own term rule (odd Jun–Nov, even Dec–May) to choose which dev offerings are current. It was not changed.
- Legacy CS301/CS305 offerings (2024-2028, labelled Semester 1) show as PREVIOUS Semester 1 for staff@department.com.
- Awaiting the user's decision: whether to delete the QA leftovers (assessment 9, user 15, subjects 3/4).
