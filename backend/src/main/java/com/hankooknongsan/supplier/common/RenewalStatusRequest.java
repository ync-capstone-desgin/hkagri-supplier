package com.hankooknongsan.supplier.common;

import jakarta.validation.constraints.NotNull;

/** 갱신 처리상태 변경 요청 (계약·인증 공통) */
public record RenewalStatusRequest(@NotNull(message = "처리상태를 선택해 주세요.") RenewalStatus renewalStatus) {
}
