package com.hankooknongsan.supplier.file;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 자료 종류 API의 요청·응답 형식 모음 */
public final class FileCategoryDtos {

    private FileCategoryDtos() {
    }

    /** 자료 종류 한 줄. required는 필수 서류인지다. */
    public record CategoryItem(Long id, String name, boolean active, boolean required) {
        /** 엔티티를 응답 형식으로 바꾼다. */
        static CategoryItem of(FileCategory c) {
            return new CategoryItem(c.getId(), c.getName(), c.isActive(), c.isRequired());
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

    /** 필수 서류 여부 변경 요청 */
    public record RequiredRequest(@NotNull(message = "필수 여부를 선택해 주세요.") Boolean required) {
    }

    /**
     * 한 업체의 필수 서류 한 줄 (#47).
     *
     * @param fileCount 그 업체에 이 종류로 올라온 파일 수. 0이면 누락이다.
     * @param exempt    이 업체에는 해당하지 않아 점검에서 뺐는지 (#85). true면 파일이 없어도 누락이 아니다.
     */
    public record RequiredDocument(Long categoryId, String name, long fileCount, boolean exempt) {
        /** 파일이 하나라도 있으면 제출한 것으로 본다. */
        public boolean isSubmitted() {
            return fileCount > 0;
        }
    }

    /** 필수 서류를 이 업체에서 해당 없음으로 빼거나 되돌리는 요청 (#85) */
    public record ExemptRequest(@NotNull(message = "해당 없음 여부를 선택해 주세요.") Boolean exempt) {
    }

    /** 파일의 종류 변경 요청. categoryId가 null이면 종류를 지운다. */
    public record AssignRequest(Long categoryId) {
    }
}
