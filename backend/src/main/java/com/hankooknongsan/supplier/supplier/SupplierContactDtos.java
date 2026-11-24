package com.hankooknongsan.supplier.supplier;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 협력업체 추가 담당자·소통 메모 API의 요청·응답 형식 모음 (#49) */
public final class SupplierContactDtos {

    private SupplierContactDtos() {
    }

    /** 담당자 등록·수정 요청. 이름만 필수다. */
    public record ContactRequest(
            @NotBlank(message = "이름을 입력해 주세요.") @Size(max = 50, message = "이름은 50자 이내로 입력해 주세요.") String name,
            @Size(max = 50, message = "직책은 50자 이내로 입력해 주세요.") String title,
            @Size(max = 30, message = "전화번호는 30자 이내로 입력해 주세요.") String phone,
            @Email(message = "이메일 형식이 올바르지 않습니다.") @Size(max = 100, message = "이메일은 100자 이내로 입력해 주세요.") String email) {
    }

    /** 담당자 한 명 */
    public record ContactItem(Long id, String name, String title, String phone, String email) {
        /** 엔티티를 응답 형식으로 바꾼다. */
        static ContactItem of(SupplierContact c) {
            return new ContactItem(c.getId(), c.getName(), c.getTitle(), c.getPhone(), c.getEmail());
        }
    }

    /** 소통 메모 작성 요청 */
    public record NoteRequest(
            @NotNull(message = "소통한 날짜를 선택해 주세요.") LocalDate contactDate,
            @NotBlank(message = "내용을 입력해 주세요.") @Size(max = 2000, message = "내용은 2000자 이내로 입력해 주세요.") String content) {
    }

    /** 소통 메모 한 건. author는 작성자 아이디, deletable은 지금 로그인한 사람이 지울 수 있는지다. */
    public record NoteItem(Long id, LocalDate contactDate, String content, String author, String authorName,
                           LocalDateTime createdAt, boolean deletable) {
    }
}
