package com.java.erp.modules.fees.controller;

import com.java.erp.common.response.ApiResponse;
import com.java.erp.common.response.PagedResponse;
import com.java.erp.modules.fees.dto.request.*;
import com.java.erp.modules.fees.dto.response.*;
import com.java.erp.modules.fees.entity.FeeStructure;
import com.java.erp.modules.fees.entity.StudentFeeAccount;
import com.java.erp.modules.fees.service.FeeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Fee administration is HOD only; STAFF have no access; STUDENT sees only /fees/me. */
@RestController
@RequestMapping("/fees")
@Tag(name = "Fees", description = "Fee structures, student charges, payments (no gateway) and balances")
public class FeeController {

    private final FeeService service;

    public FeeController(FeeService service) {
        this.service = service;
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "My fee charges, payments made and balances")
    public ApiResponse<MyFeesResponse> mine() {
        return ApiResponse.success("Fee details fetched successfully", service.myFees());
    }

    @PostMapping("/structures")
    @PreAuthorize("hasRole('HOD')")
    @Operation(summary = "Create a fee structure")
    public ResponseEntity<ApiResponse<FeeStructureResponse>> createStructure(@Valid @RequestBody FeeStructureRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Fee structure created successfully", service.createStructure(request)));
    }

    @GetMapping("/structures")
    @PreAuthorize("hasRole('HOD')")
    @Operation(summary = "List fee structures")
    public ApiResponse<PagedResponse<FeeStructureResponse>> listStructures(
            @RequestParam(required = false) String academicYear,
            @RequestParam(required = false) Integer semester,
            @RequestParam(required = false) Long batchId,
            @RequestParam(required = false) FeeStructure.FeeType feeType,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success("Fee structures fetched successfully",
                service.listStructures(academicYear, semester, batchId, feeType, active, page, size));
    }

    @GetMapping("/structures/{id}")
    @PreAuthorize("hasRole('HOD')")
    @Operation(summary = "Get a fee structure")
    public ApiResponse<FeeStructureResponse> getStructure(@PathVariable Long id) {
        return ApiResponse.success("Fee structure fetched successfully", service.getStructure(id));
    }

    @PutMapping("/structures/{id}")
    @PreAuthorize("hasRole('HOD')")
    @Operation(summary = "Update a fee structure", description = "The amount is fixed once assigned to students.")
    public ApiResponse<FeeStructureResponse> updateStructure(@PathVariable Long id, @Valid @RequestBody FeeStructureRequest request) {
        return ApiResponse.success("Fee structure updated successfully", service.updateStructure(id, request));
    }

    @PostMapping("/structures/{id}/assign")
    @PreAuthorize("hasRole('HOD')")
    @Operation(summary = "Charge a fee to students",
               description = "studentIds, else sectionId, else the structure's batch. Already charged students are skipped.")
    public ApiResponse<AssignFeeResponse> assign(@PathVariable Long id, @Valid @RequestBody(required = false) AssignFeeRequest request) {
        return ApiResponse.success("Fee assigned successfully", service.assign(id, request));
    }

    @GetMapping("/accounts")
    @PreAuthorize("hasRole('HOD')")
    @Operation(summary = "List student fee charges", description = "outstandingOnly=true lists UNPAID and PARTIALLY_PAID charges.")
    public ApiResponse<PagedResponse<FeeAccountResponse>> listAccounts(
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) Long sectionId,
            @RequestParam(required = false) Long batchId,
            @RequestParam(required = false) Long feeStructureId,
            @RequestParam(required = false) StudentFeeAccount.Status status,
            @RequestParam(defaultValue = "false") boolean outstandingOnly,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return ApiResponse.success("Fee accounts fetched successfully",
                service.listAccounts(studentId, sectionId, batchId, feeStructureId, status, outstandingOnly, page, size));
    }

    @GetMapping("/accounts/{id}")
    @PreAuthorize("hasRole('HOD')")
    @Operation(summary = "A fee charge with its payments")
    public ApiResponse<FeeAccountDetailResponse> getAccount(@PathVariable Long id) {
        return ApiResponse.success("Fee account fetched successfully", service.getAccount(id));
    }

    @PostMapping("/accounts/{id}/payments")
    @PreAuthorize("hasRole('HOD')")
    @Operation(summary = "Record a payment", description = "Creates a receipt. Amount must not exceed the balance.")
    public ResponseEntity<ApiResponse<ReceiptResponse>> recordPayment(@PathVariable Long id, @Valid @RequestBody RecordPaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Payment recorded successfully", service.recordPayment(id, request)));
    }

    @PostMapping("/accounts/{id}/waive")
    @PreAuthorize("hasRole('HOD')")
    @Operation(summary = "Waive the remaining balance of a charge")
    public ApiResponse<FeeAccountResponse> waive(@PathVariable Long id, @Valid @RequestBody WaiveFeeRequest request) {
        return ApiResponse.success("Fee waived successfully", service.waive(id, request));
    }

    @GetMapping("/summary")
    @PreAuthorize("hasRole('HOD')")
    @Operation(summary = "Department fee totals and outstanding balances")
    public ApiResponse<FeeSummaryResponse> summary() {
        return ApiResponse.success("Fee summary fetched successfully", service.summary());
    }

    @GetMapping("/templates")
    @PreAuthorize("hasRole('HOD')")
    @Operation(summary = "Fee templates of Year 1-4",
               description = "Active charges per student year with totals; configured=false when no structure is set for a year.")
    public ApiResponse<List<FeeTemplateResponse>> templates() {
        return ApiResponse.success("Fee templates fetched successfully", service.templates());
    }

    @GetMapping("/students/{studentId}")
    @PreAuthorize("hasRole('HOD')")
    @Operation(summary = "One student's fee account", description = "Same shape as GET /fees/me: charges, totals and the per-year breakdown.")
    public ApiResponse<MyFeesResponse> studentFees(@PathVariable Long studentId) {
        return ApiResponse.success("Student fees fetched successfully", service.studentFees(studentId));
    }

    @PutMapping("/accounts/{id}/concession")
    @PreAuthorize("hasRole('HOD')")
    @Operation(summary = "Set the concession of a charge",
               description = "0 <= concession <= actual amount and not below what is already paid; payable = actual - concession.")
    public ApiResponse<FeeAccountResponse> concession(@PathVariable Long id, @Valid @RequestBody ConcessionRequest request) {
        return ApiResponse.success("Concession updated successfully", service.setConcession(id, request));
    }
}
