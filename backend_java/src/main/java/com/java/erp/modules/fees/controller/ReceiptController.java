package com.java.erp.modules.fees.controller;

import com.java.erp.common.response.ApiResponse;
import com.java.erp.common.response.PagedResponse;
import com.java.erp.modules.fees.dto.response.ReceiptResponse;
import com.java.erp.modules.fees.entity.FeePayment;
import com.java.erp.modules.fees.service.ReceiptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/receipts")
@Tag(name = "Receipts", description = "Fee payment receipts (derived from recorded payments)")
public class ReceiptController {

    private final ReceiptService service;

    public ReceiptController(ReceiptService service) {
        this.service = service;
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "My receipts")
    public ApiResponse<List<ReceiptResponse>> mine() {
        return ApiResponse.success("Receipts fetched successfully", service.myReceipts());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('HOD','STUDENT')")
    @Operation(summary = "Get a receipt", description = "Students can open their own receipts only.")
    public ApiResponse<ReceiptResponse> get(@PathVariable Long id) {
        return ApiResponse.success("Receipt fetched successfully", service.get(id));
    }

    @GetMapping
    @PreAuthorize("hasRole('HOD')")
    @Operation(summary = "Search receipts / payment history")
    public ApiResponse<PagedResponse<ReceiptResponse>> search(
            @RequestParam(required = false) String receiptNumber,
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) Long sectionId,
            @RequestParam(required = false) Long feeStructureId,
            @RequestParam(required = false) FeePayment.PaymentMethod paymentMethod,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return ApiResponse.success("Receipts fetched successfully",
                service.search(receiptNumber, studentId, sectionId, feeStructureId, paymentMethod, from, to, page, size));
    }
}
