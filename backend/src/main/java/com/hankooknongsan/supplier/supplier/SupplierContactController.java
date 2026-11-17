package com.hankooknongsan.supplier.supplier;

import java.util.List;

import com.hankooknongsan.supplier.common.PageResponse;
import com.hankooknongsan.supplier.supplier.SupplierContactDtos.ContactItem;
import com.hankooknongsan.supplier.supplier.SupplierContactDtos.ContactRequest;
import com.hankooknongsan.supplier.supplier.SupplierContactDtos.NoteItem;
import com.hankooknongsan.supplier.supplier.SupplierContactDtos.NoteRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 협력업체 추가 담당자·소통 메모 API (#49).
 * 조회는 로그인한 모든 사용자, 등록·수정·삭제는 관리자·담당자다 (보안 설정의 기본 규칙).
 * 메모 삭제는 그중에서도 작성자 본인과 관리자만 할 수 있다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/suppliers/{supplierId}")
public class SupplierContactController {

    private final SupplierContactService service;

    /** 업체의 추가 담당자 목록을 조회한다. */
    @GetMapping("/contacts")
    public List<ContactItem> contacts(@PathVariable Long supplierId) {
        return service.contacts(supplierId);
    }

    /** 업체에 담당자를 추가한다. */
    @PostMapping("/contacts")
    @ResponseStatus(HttpStatus.CREATED)
    public ContactItem addContact(@PathVariable Long supplierId, @Valid @RequestBody ContactRequest req) {
        return service.addContact(supplierId, req);
    }

    /** 담당자 정보를 수정한다. */
    @PutMapping("/contacts/{contactId}")
    public ContactItem updateContact(@PathVariable Long supplierId, @PathVariable Long contactId,
                                     @Valid @RequestBody ContactRequest req) {
        return service.updateContact(supplierId, contactId, req);
    }

    /** 담당자를 삭제한다. */
    @DeleteMapping("/contacts/{contactId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteContact(@PathVariable Long supplierId, @PathVariable Long contactId, Authentication auth) {
        service.deleteContact(supplierId, contactId, auth.getName());
    }

    /** 업체의 소통 메모를 소통한 날짜가 최근인 순으로 조회한다. */
    @GetMapping("/notes")
    public PageResponse<NoteItem> notes(@PathVariable Long supplierId, Authentication auth,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        return service.notes(supplierId, auth.getName(), isAdmin(auth), PageRequest.of(page, size));
    }

    /** 소통 메모를 남긴다. */
    @PostMapping("/notes")
    @ResponseStatus(HttpStatus.CREATED)
    public NoteItem addNote(@PathVariable Long supplierId, @Valid @RequestBody NoteRequest req, Authentication auth) {
        return service.addNote(supplierId, req, auth.getName(), isAdmin(auth));
    }

    /** 소통 메모를 삭제한다. 작성자 본인과 관리자만 할 수 있다. */
    @DeleteMapping("/notes/{noteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteNote(@PathVariable Long supplierId, @PathVariable Long noteId, Authentication auth) {
        service.deleteNote(supplierId, noteId, auth.getName(), isAdmin(auth));
    }

    /** 로그인한 사람이 관리자인지 확인한다. */
    private static boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }
}
