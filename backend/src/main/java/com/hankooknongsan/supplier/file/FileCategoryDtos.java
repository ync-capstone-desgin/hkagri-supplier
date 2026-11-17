package com.hankooknongsan.supplier.file;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 자료 종류 API의 요청·응답 형식 모음 */
public final class FileCategoryDtos {

    private FileCategoryDtos() {
    }

    /** 자료 종류 한 줄 */
    public record CategoryItem(Long id, String name, boolean active) {
        /** 엔티티를 응답 형식으로 바꾼다. */
        static CategoryItem of(FileCategory c) {
            return new CategoryItem(c.getId(), c.getName(), c.isActive());
        }
    }

    /** 종류 추가·이름 수정 요청 */
    public record NameRequest(
            @NotBlank(message = "자료 종류 이름을 입력해 주세요.")
            @Size(max = 100, message = "이름은 100자 이내로 입력해 주세요.") String name) {
    }

    /** 사용 여부 변경 요청 */
    public record ActiveRequest(@NotNull(message = "사용 여부를 선택해 주세요.") Boolean active) {
    }

    /** 파일의 종류 변경 요청. categoryId가 null이면 종류를 지운다. */
    public record AssignRequest(Long categoryId) {
    }
}
