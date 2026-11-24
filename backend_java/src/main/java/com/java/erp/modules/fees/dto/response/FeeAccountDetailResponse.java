package com.java.erp.modules.fees.dto.response;

import java.util.List;

public record FeeAccountDetailResponse(
        FeeAccountResponse account,
        List<ReceiptResponse> payments
) {
}
