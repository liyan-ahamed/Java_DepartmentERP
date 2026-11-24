package com.java.erp.modules.fees.service;

import com.java.erp.common.response.PagedResponse;
import com.java.erp.common.util.SecurityUtils;
import com.java.erp.exception.BadRequestException;
import com.java.erp.exception.ConflictException;
import com.java.erp.exception.ResourceNotFoundException;
import com.java.erp.modules.audit.service.AuditRecorder;
import com.java.erp.modules.auth.repository.UserRepository;
import com.java.erp.modules.fees.dto.request.*;
import com.java.erp.modules.fees.dto.response.*;
import com.java.erp.modules.fees.entity.FeePayment;
import com.java.erp.modules.fees.entity.FeeStructure;
import com.java.erp.modules.fees.entity.StudentFeeAccount;
import com.java.erp.modules.fees.repository.FeePaymentRepository;
import com.java.erp.modules.fees.repository.FeeStructureRepository;
import com.java.erp.modules.fees.repository.StudentFeeAccountRepository;
import com.java.erp.modules.gradebook.service.CurrentStudentResolver;
import com.java.erp.modules.notification.entity.Notification;
import com.java.erp.modules.notification.service.StudentNotifier;
import com.java.erp.modules.system.entity.Batch;
import com.java.erp.modules.system.entity.Section;
import com.java.erp.modules.system.entity.Student;
import com.java.erp.modules.system.repository.BatchRepository;
import com.java.erp.modules.system.repository.SectionRepository;
import com.java.erp.modules.system.repository.StudentRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * Fee recordkeeping (HOD only, students read their own via /fees/me). No payment gateway: payments
 * are recorded by the office and each one becomes a receipt. All money is BigDecimal with 2 decimals.
 */
@Service
public class FeeService {

    static final String MODULE = "FEES";
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final FeeStructureRepository structures;
    private final StudentFeeAccountRepository accounts;
    private final FeePaymentRepository payments;
    private final StudentRepository students;
    private final BatchRepository batches;
    private final SectionRepository sections;
    private final UserRepository users;
    private final CurrentStudentResolver currentStudent;
    private final ReceiptNumberGenerator receiptNumbers;
    private final StudentNotifier notifier;
    private final AuditRecorder audit;

    public FeeService(FeeStructureRepository structures, StudentFeeAccountRepository accounts, FeePaymentRepository payments,
                      StudentRepository students, BatchRepository batches, SectionRepository sections,
                      UserRepository users, CurrentStudentResolver currentStudent, ReceiptNumberGenerator receiptNumbers,
                      StudentNotifier notifier, AuditRecorder audit) {
        this.structures = structures;
        this.accounts = accounts;
        this.payments = payments;
        this.students = students;
        this.batches = batches;
        this.sections = sections;
        this.users = users;
        this.currentStudent = currentStudent;
        this.receiptNumbers = receiptNumbers;
        this.notifier = notifier;
        this.audit = audit;
    }

    // ---- Fee structures ----

    @Transactional
    public FeeStructureResponse createStructure(FeeStructureRequest request) {
        validateYearOrTerm(request);
        rejectDuplicateName(request, null);
        FeeStructure structure = new FeeStructure();
        apply(structure, request);
        structure = structures.saveAndFlush(structure);
        audit.record("CREATE", MODULE, "FeeStructure", structure.getId(), structureSummary(structure));
        return FeeStructureResponse.from(structure, 0);
    }

    /** The amount is fixed once the fee has been assigned to students (charges copy it). */
    @Transactional
    public FeeStructureResponse updateStructure(Long id, FeeStructureRequest request) {
        FeeStructure structure = requireStructure(id);
        validateYearOrTerm(request);
        long assigned = accounts.countByFeeStructureId(id);
        if (assigned > 0 && structure.getAmount().compareTo(request.amount()) != 0) {
            throw new ConflictException("The amount cannot change after the fee has been assigned to students");
        }
        Short requestedYear = request.studentYear() == null ? null : request.studentYear().shortValue();
        if (assigned > 0 && !Objects.equals(structure.getStudentYear(), requestedYear)) {
            throw new ConflictException("The student year cannot change after the fee has been assigned to students");
        }
        boolean sameKey = structure.getName().equalsIgnoreCase(request.name().trim())
                && Objects.equals(structure.getAcademicYear(), request.academicYear())
                && Objects.equals(structure.getStudentYear(), requestedYear);
        if (!sameKey) {
            rejectDuplicateName(request, id);
        }
        apply(structure, request);
        structures.saveAndFlush(structure);
        audit.record("FEE_STRUCTURE_UPDATED", MODULE, "FeeStructure", id, structureSummary(structure));
        return FeeStructureResponse.from(structure, assigned);
    }

    @Transactional(readOnly = true)
    public FeeStructureResponse getStructure(Long id) {
        return FeeStructureResponse.from(requireStructure(id), accounts.countByFeeStructureId(id));
    }

    @Transactional(readOnly = true)
    public PagedResponse<FeeStructureResponse> listStructures(String academicYear, Integer semester, Long batchId,
                                                              FeeStructure.FeeType feeType, Boolean active, int page, int size) {
        Specification<FeeStructure> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (academicYear != null) p.add(cb.equal(root.get("academicYear"), academicYear.trim()));
            if (semester != null) p.add(cb.equal(root.get("semester"), semester));
            if (batchId != null) p.add(cb.equal(root.get("batch").get("id"), batchId));
            if (feeType != null) p.add(cb.equal(root.get("feeType"), feeType));
            if (active != null) p.add(cb.equal(root.get("active"), active));
            return cb.and(p.toArray(Predicate[]::new));
        };
        Page<FeeStructure> result = structures.findAll(spec, PageRequest.of(page, size, Sort.by(Sort.Order.desc("id"))));
        return PagedResponse.of(result.getContent().stream()
                .map(f -> FeeStructureResponse.from(f, accounts.countByFeeStructureId(f.getId()))).toList(), result);
    }

    // ---- Assigning charges ----

    /** Creates one charge per targeted student; students already charged for this fee are skipped. */
    @Transactional
    public AssignFeeResponse assign(Long structureId, AssignFeeRequest request) {
        FeeStructure structure = requireStructure(structureId);
        if (!structure.isActive()) {
            throw new BadRequestException("This fee structure is inactive");
        }
        List<Student> targets = targets(structure, request == null ? new AssignFeeRequest(null, null, null) : request);
        LocalDate dueDate = request != null && request.dueDate() != null ? request.dueDate() : structure.getDueDate();

        Set<Long> alreadyCharged = accounts.findStudentIdsByFeeStructureId(structureId);
        List<StudentFeeAccount> created = new ArrayList<>();
        for (Student s : targets) {
            if (!alreadyCharged.contains(s.getId())) created.add(new StudentFeeAccount(s, structure, dueDate));
        }
        accounts.saveAll(created);
        accounts.flush();

        String due = dueDate == null ? "" : " Due " + dueDate + ".";
        for (StudentFeeAccount a : created) {
            notifier.notifyStudent(a.getStudent(), new StudentNotifier.Message(Notification.NotificationType.ACTION_REQUIRED,
                    "New fee: " + structure.getName(), structure.getName() + " of Rs. " + structure.getAmount().toPlainString()
                    + " has been added to your fee account." + due, MODULE, a.getId(), "FEE_ACCOUNT",
                    Notification.NotificationPriority.NORMAL));
        }
        audit.record("ASSIGN", MODULE, "FeeStructure", structureId, Map.of(
                "feeStructureId", structureId, "targeted", targets.size(), "assigned", created.size()));
        return new AssignFeeResponse(structureId, targets.size(), created.size(), targets.size() - created.size());
    }

    // ---- Accounts and payments ----

    @Transactional(readOnly = true)
    public PagedResponse<FeeAccountResponse> listAccounts(Long studentId, Long sectionId, Long batchId, Long feeStructureId,
                                                          StudentFeeAccount.Status status, boolean outstandingOnly,
                                                          int page, int size) {
        Specification<StudentFeeAccount> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (studentId != null) p.add(cb.equal(root.get("student").get("id"), studentId));
            if (sectionId != null) p.add(cb.equal(root.get("student").get("section").get("id"), sectionId));
            if (batchId != null) p.add(cb.equal(root.get("student").get("batch").get("id"), batchId));
            if (feeStructureId != null) p.add(cb.equal(root.get("feeStructure").get("id"), feeStructureId));
            if (status != null) p.add(cb.equal(root.get("status"), status));
            if (outstandingOnly) p.add(root.get("status").in(StudentFeeAccount.Status.UNPAID, StudentFeeAccount.Status.PARTIALLY_PAID));
            return cb.and(p.toArray(Predicate[]::new));
        };
        Page<StudentFeeAccount> result = accounts.findAll(spec,
                PageRequest.of(page, size, Sort.by(Sort.Order.asc("dueDate"), Sort.Order.asc("id"))));
        return PagedResponse.of(result.getContent().stream().map(FeeAccountResponse::from).toList(), result);
    }

    @Transactional(readOnly = true)
    public FeeAccountDetailResponse getAccount(Long id) {
        StudentFeeAccount account = accounts.findWithDetailsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("StudentFeeAccount", "id", id));
        return new FeeAccountDetailResponse(FeeAccountResponse.from(account),
                payments.findByAccountIdOrderByPaymentDateAscIdAsc(id).stream().map(ReceiptResponse::from).toList());
    }

    /**
     * Records a payment (locks the charge row first): 0 < amount <= balance, charge not PAID or
     * WAIVED, date not in the future. Returns the new receipt.
     */
    @Transactional
    public ReceiptResponse recordPayment(Long accountId, RecordPaymentRequest request) {
        StudentFeeAccount account = accounts.findForUpdate(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("StudentFeeAccount", "id", accountId));
        if (account.getStatus() == StudentFeeAccount.Status.PAID || account.getStatus() == StudentFeeAccount.Status.WAIVED) {
            throw new ConflictException("This fee is already " + account.getStatus());
        }
        BigDecimal amount = request.amount().setScale(2);
        if (amount.compareTo(account.balance()) > 0) {
            throw new BadRequestException("Payment of " + amount.toPlainString() + " exceeds the balance of "
                    + account.balance().toPlainString());
        }
        LocalDate date = request.paymentDate() == null ? LocalDate.now() : request.paymentDate();
        if (date.isAfter(LocalDate.now())) {
            throw new BadRequestException("Payment date cannot be in the future");
        }

        account.applyPayment(amount);
        accounts.saveAndFlush(account);
        FeePayment payment = payments.saveAndFlush(new FeePayment(account, receiptNumbers.next(date), amount, date,
                request.paymentMethod(), trimToNull(request.reference()), trimToNull(request.remarks()),
                users.getReferenceById(SecurityUtils.getCurrentUserId())));
        audit.record("PAYMENT", MODULE, "FeePayment", payment.getId(), Map.of(
                "paymentId", payment.getId(), "receiptNumber", payment.getReceiptNumber(), "feeAccountId", accountId,
                "studentId", account.getStudent().getId(), "amount", amount.toPlainString(),
                "newStatus", account.getStatus()));
        return ReceiptResponse.from(payments.findWithDetailsById(payment.getId()).orElseThrow());
    }

    /** Closes the remaining balance of an UNPAID or PARTIALLY_PAID charge without a payment. */
    @Transactional
    public FeeAccountResponse waive(Long accountId, WaiveFeeRequest request) {
        StudentFeeAccount account = accounts.findForUpdate(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("StudentFeeAccount", "id", accountId));
        StudentFeeAccount.Status old = account.getStatus();
        if (old == StudentFeeAccount.Status.PAID || old == StudentFeeAccount.Status.WAIVED) {
            throw new ConflictException("This fee is already " + old);
        }
        BigDecimal waived = account.balance();
        account.waive(request.reason().trim());
        accounts.saveAndFlush(account);
        audit.record("WAIVE", MODULE, "StudentFeeAccount", accountId, Map.of(
                "feeAccountId", accountId, "studentId", account.getStudent().getId(),
                "waivedAmount", waived.toPlainString(), "oldStatus", old));
        return FeeAccountResponse.from(accounts.findWithDetailsById(accountId).orElseThrow());
    }

    @Transactional(readOnly = true)
    public FeeSummaryResponse summary() {
        long count = 0;
        BigDecimal charged = ZERO, paid = ZERO, waived = ZERO, outstanding = ZERO;
        Map<StudentFeeAccount.Status, Long> byStatus = new EnumMap<>(StudentFeeAccount.Status.class);
        for (StudentFeeAccount.Status s : StudentFeeAccount.Status.values()) byStatus.put(s, 0L);
        for (StudentFeeAccountRepository.StatusTotals t : accounts.totalsByStatus()) {
            count += t.getAccounts();
            byStatus.put(t.getStatus(), t.getAccounts());
            charged = charged.add(t.getCharged());
            paid = paid.add(t.getPaid());
            BigDecimal open = t.getCharged().subtract(t.getPaid());
            if (t.getStatus() == StudentFeeAccount.Status.WAIVED) waived = waived.add(open);
            else outstanding = outstanding.add(open);
        }
        return new FeeSummaryResponse(count, charged, paid, waived, outstanding,
                accounts.overdueOutstanding(LocalDate.now()).setScale(2), byStatus);
    }

    @Transactional(readOnly = true)
    public MyFeesResponse myFees() {
        return feesOf(currentStudent.require().getId());
    }

    /** HOD: one student's fee account with the per-year breakdown (same shape as GET /fees/me). */
    @Transactional(readOnly = true)
    public MyFeesResponse studentFees(Long studentId) {
        if (!students.existsById(studentId)) {
            throw new ResourceNotFoundException("Student", "id", studentId);
        }
        return feesOf(studentId);
    }

    private MyFeesResponse feesOf(Long studentId) {
        List<StudentFeeAccount> charges = accounts.findByStudentIdOrderByDueDateAscIdAsc(studentId);
        List<FeeAccountResponse> list = charges.stream().map(FeeAccountResponse::from).toList();
        BigDecimal charged = ZERO, paid = ZERO, outstanding = ZERO, concession = ZERO, payable = ZERO;
        for (FeeAccountResponse a : list) {
            charged = charged.add(a.amount());
            paid = paid.add(a.paidAmount());
            outstanding = outstanding.add(a.balance());
            concession = concession.add(a.concessionAmount());
            payable = payable.add(a.payableAmount());
        }
        return new MyFeesResponse(charged, paid, outstanding, list, concession, payable, yearSummaries(charges));
    }

    /** Charges grouped by fee year (Year 1..4 first, then term charges without a year), each with its totals. */
    private static List<FeeYearSummary> yearSummaries(List<StudentFeeAccount> charges) {
        Map<Short, List<StudentFeeAccount>> byYear = new TreeMap<>(Comparator.nullsLast(Comparator.naturalOrder()));
        for (StudentFeeAccount a : charges) {
            byYear.computeIfAbsent(a.getFeeStructure().getStudentYear(), y -> new ArrayList<>()).add(a);
        }
        List<FeeYearSummary> result = new ArrayList<>();
        byYear.forEach((year, list) -> {
            List<FeeYearSummary.Item> items = list.stream()
                    .sorted(Comparator.comparing((StudentFeeAccount a) -> a.getFeeStructure().getId()))
                    .map(FeeYearSummary.Item::from).toList();
            BigDecimal actual = ZERO, concession = ZERO, payable = ZERO, paid = ZERO, balance = ZERO;
            for (FeeYearSummary.Item i : items) {
                actual = actual.add(i.actualAmount());
                concession = concession.add(i.concessionAmount());
                payable = payable.add(i.payableAmount());
                paid = paid.add(i.paidAmount());
                balance = balance.add(i.balanceAmount());
            }
            result.add(new FeeYearSummary(year, year == null ? "Other fees" : "Year " + year, actual, concession, payable,
                    paid, balance, items));
        });
        return result;
    }

    // ---- Year templates and concessions ----

    /** Years 1-4: the active charges of each year template; a year without charges is "not configured". */
    @Transactional(readOnly = true)
    public List<FeeTemplateResponse> templates() {
        List<FeeTemplateResponse> result = new ArrayList<>();
        for (short year = 1; year <= 4; year++) {
            List<FeeStructure> items = structures.findByStudentYearAndAcademicYearIsNullAndActiveTrueOrderByIdAsc(year);
            if (items.isEmpty()) {
                result.add(new FeeTemplateResponse(year, "Year " + year, false, null, null, null, List.of()));
                continue;
            }
            BigDecimal total = items.stream().map(FeeStructure::getAmount).reduce(ZERO, BigDecimal::add);
            result.add(new FeeTemplateResponse(year, "Year " + year, true, total, ZERO, total, items.stream()
                    .map(f -> new FeeTemplateResponse.Item(f.getId(), f.getName(), f.getFeeType(), f.getAmount(),
                            accounts.countByFeeStructureId(f.getId())))
                    .toList()));
        }
        return result;
    }

    /**
     * Sets the concession of one charge: 0 <= concession <= actual amount and the paid amount may not exceed the
     * new payable. Not for WAIVED charges. The status follows the new payable (fully conceded = PAID).
     */
    @Transactional
    public FeeAccountResponse setConcession(Long accountId, ConcessionRequest request) {
        StudentFeeAccount account = accounts.findForUpdate(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("StudentFeeAccount", "id", accountId));
        if (account.getStatus() == StudentFeeAccount.Status.WAIVED) {
            throw new ConflictException("This fee has been waived");
        }
        BigDecimal concession = request.concessionAmount().setScale(2);
        if (concession.compareTo(account.getAmount()) > 0) {
            throw new BadRequestException("Concession cannot exceed the actual amount of " + account.getAmount().toPlainString());
        }
        if (account.getPaidAmount().compareTo(account.getAmount().subtract(concession)) > 0) {
            throw new BadRequestException("Concession would leave the payable amount below the "
                    + account.getPaidAmount().toPlainString() + " already paid");
        }
        BigDecimal old = account.getConcessionAmount();
        account.applyConcession(concession);
        accounts.saveAndFlush(account);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("feeAccountId", accountId);
        summary.put("studentId", account.getStudent().getId());
        summary.put("oldConcession", old.toPlainString());
        summary.put("newConcession", concession.toPlainString());
        summary.put("newStatus", account.getStatus());
        audit.record("CONCESSION", MODULE, "StudentFeeAccount", accountId, summary);
        return FeeAccountResponse.from(accounts.findWithDetailsById(accountId).orElseThrow());
    }

    // ---- helpers ----

    private List<Student> targets(FeeStructure structure, AssignFeeRequest request) {
        if (structure.getStudentYear() != null && structure.getBatch() == null) {
            return yearTemplateTargets(structure.getStudentYear(), request);
        }
        Long batchId = structure.getBatch() == null ? null : structure.getBatch().getId();
        if (request.studentIds() != null && !request.studentIds().isEmpty()) {
            List<String> errors = new ArrayList<>();
            Map<Long, Student> found = new LinkedHashMap<>();
            for (Long id : new LinkedHashSet<>(request.studentIds())) {
                Optional<Student> s = students.findById(id);
                if (s.isEmpty()) errors.add("Student " + id + " does not exist");
                else if (!s.get().isActive()) errors.add("Student " + id + " is inactive");
                else if (batchId != null && !s.get().getBatch().getId().equals(batchId)) errors.add("Student " + id + " is not in this fee's batch");
                else found.put(id, s.get());
            }
            if (!errors.isEmpty()) throw new BadRequestException("Fee assignment failed", errors);
            return new ArrayList<>(found.values());
        }
        if (request.sectionId() != null) {
            Section section = sections.findById(request.sectionId())
                    .orElseThrow(() -> new BadRequestException("Section " + request.sectionId() + " does not exist"));
            if (batchId != null && !section.getBatch().getId().equals(batchId)) {
                throw new BadRequestException("Section " + request.sectionId() + " is not in this fee's batch");
            }
            return students.findBySectionIdAndActiveTrueOrderByRegisterNumberAsc(section.getId());
        }
        if (batchId == null) {
            throw new BadRequestException("Give studentIds or sectionId: this fee structure has no batch");
        }
        return students.findByBatchId(batchId).stream().filter(Student::isActive)
                .sorted(Comparator.comparing(Student::getRegisterNumber)).toList();
    }

    /**
     * Students a "Year N" template charge goes to: the given students / section (who must be in year N), or by
     * default every active student of the active cohort currently in year N.
     */
    private List<Student> yearTemplateTargets(short year, AssignFeeRequest request) {
        List<Student> candidates;
        if (request.studentIds() != null && !request.studentIds().isEmpty()) {
            List<String> errors = new ArrayList<>();
            Map<Long, Student> found = new LinkedHashMap<>();
            for (Long id : new LinkedHashSet<>(request.studentIds())) {
                Optional<Student> s = students.findById(id);
                if (s.isEmpty()) errors.add("Student " + id + " does not exist");
                else if (!s.get().isActive()) errors.add("Student " + id + " is inactive");
                else if (!inYear(s.get(), year)) errors.add("Student " + id + " is not in Year " + year);
                else found.put(id, s.get());
            }
            if (!errors.isEmpty()) throw new BadRequestException("Fee assignment failed", errors);
            return new ArrayList<>(found.values());
        }
        if (request.sectionId() != null) {
            Section section = sections.findById(request.sectionId())
                    .orElseThrow(() -> new BadRequestException("Section " + request.sectionId() + " does not exist"));
            if (!section.getBatch().isActive() || section.getBatch().getCurrentYear() == null
                    || section.getBatch().getCurrentYear() != year) {
                throw new BadRequestException("Section " + request.sectionId() + " is not in Year " + year);
            }
            return students.findBySectionIdAndActiveTrueOrderByRegisterNumberAsc(section.getId());
        }
        candidates = students.findActiveInActiveBatchOfYear(year);
        return candidates;
    }

    private static boolean inYear(Student s, short year) {
        return s.getBatch().isActive() && s.getBatch().getCurrentYear() != null && s.getBatch().getCurrentYear() == year;
    }

    /** A term charge needs a valid academic year; a year-template charge needs studentYear (and no academic year). */
    private static void validateYearOrTerm(FeeStructureRequest r) {
        if (r.studentYear() == null && r.academicYear() == null) {
            throw new BadRequestException("Give academicYear (term charge) or studentYear 1-4 (year template)");
        }
        if (r.academicYear() != null) {
            validateAcademicYear(r.academicYear());
        }
        if (r.studentYear() != null && r.academicYear() == null && r.batchId() != null) {
            throw new BadRequestException("A year-template charge applies to whichever cohort is in that year; do not give batchId");
        }
    }

    private void rejectDuplicateName(FeeStructureRequest r, Long excludeId) {
        String name = r.name().trim();
        boolean duplicate = r.academicYear() != null
                ? structures.existsByNameIgnoreCaseAndAcademicYear(name, r.academicYear())
                : structures.findByStudentYearAndAcademicYearIsNullAndActiveTrueOrderByIdAsc(r.studentYear().shortValue())
                        .stream().anyMatch(f -> f.getName().equalsIgnoreCase(name) && !f.getId().equals(excludeId));
        if (duplicate) {
            String scope = r.academicYear() != null ? r.academicYear() : "Year " + r.studentYear();
            throw new ConflictException("A fee structure named '" + name + "' already exists for " + scope);
        }
    }

    private void apply(FeeStructure f, FeeStructureRequest r) {
        Batch batch = null;
        if (r.batchId() != null) {
            batch = batches.findById(r.batchId()).orElseThrow(() -> new BadRequestException("Batch " + r.batchId() + " does not exist"));
        }
        f.setName(r.name().trim());
        f.setFeeType(r.feeType());
        f.setAcademicYear(r.academicYear());
        f.setStudentYear(r.studentYear() == null ? null : r.studentYear().shortValue());
        f.setSemester(r.semester());
        f.setBatch(batch);
        f.setAmount(r.amount().setScale(2));
        f.setDueDate(r.dueDate());
        f.setActive(r.active() == null || r.active());
    }

    private static void validateAcademicYear(String academicYear) {
        int start = Integer.parseInt(academicYear.substring(0, 4));
        int end = Integer.parseInt(academicYear.substring(5));
        if (end != start + 1) {
            throw new BadRequestException("Academic year must span consecutive years, e.g. 2026-2027");
        }
    }

    private static Map<String, Object> structureSummary(FeeStructure f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("feeStructureId", f.getId());
        m.put("feeType", f.getFeeType());
        m.put("academicYear", f.getAcademicYear());
        m.put("studentYear", f.getStudentYear());
        m.put("amount", f.getAmount().toPlainString());
        m.put("active", f.isActive());
        return m;
    }

    private FeeStructure requireStructure(Long id) {
        return structures.findWithDetailsById(id).orElseThrow(() -> new ResourceNotFoundException("FeeStructure", "id", id));
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
