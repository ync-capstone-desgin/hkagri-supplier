package com.hankooknongsan.supplier.supplier;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.hankooknongsan.supplier.auth.AppUser;
import com.hankooknongsan.supplier.auth.AppUserRepository;
import com.hankooknongsan.supplier.common.AppClock;
import com.hankooknongsan.supplier.common.BusinessException;
import com.hankooknongsan.supplier.common.PageResponse;
import com.hankooknongsan.supplier.history.ChangeAction;
import com.hankooknongsan.supplier.history.ChangeLogService;
import com.hankooknongsan.supplier.history.ChangeTarget;
import com.hankooknongsan.supplier.supplier.SupplierContactDtos.ContactItem;
import com.hankooknongsan.supplier.supplier.SupplierContactDtos.ContactRequest;
import com.hankooknongsan.supplier.supplier.SupplierContactDtos.NoteItem;
import com.hankooknongsan.supplier.supplier.SupplierContactDtos.NoteRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 협력업체 추가 담당자와 소통 메모 서비스 (#49).
 * 담당자 추가·수정·삭제는 업체의 변경 이력에 함께 남긴다. 메모는 그 자체가 기록이므로 변경 이력에 넣지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SupplierContactService {

    private final SupplierContactRepository contacts;
    private final SupplierNoteRepository notes;
    private final SupplierService supplierService;
    private final AppUserRepository users;
    private final ChangeLogService history;
    private final AppClock clock;

    /** 업체의 추가 담당자를 등록한 순서대로 조회한다. 업체가 없으면 404. */
    public List<ContactItem> contacts(Long supplierId) {
        supplierService.find(supplierId);
        return contacts.findBySupplierIdOrderByIdAsc(supplierId).stream().map(ContactItem::of).toList();
    }

    /** 업체에 담당자를 추가한다. */
    @Transactional
    public ContactItem addContact(Long supplierId, ContactRequest req) {
        Supplier supplier = supplierService.find(supplierId);
        SupplierContact contact = new SupplierContact(supplier);
        apply(contact, req);
        contacts.save(contact);
        log(supplier, "담당자 추가", null, describe(contact));
        return ContactItem.of(contact);
    }

    /** 담당자 정보를 수정한다. 그 업체의 담당자가 아니면 404. */
    @Transactional
    public ContactItem updateContact(Long supplierId, Long contactId, ContactRequest req) {
        SupplierContact contact = findContact(supplierId, contactId);
        String before = describe(contact);
        apply(contact, req);
        contacts.flush();
        log(contact.getSupplier(), "담당자 수정", before, describe(contact));
        return ContactItem.of(contact);
    }

    /** 담당자를 삭제한다. 지운 표시만 남긴다. */
    @Transactional
    public void deleteContact(Long supplierId, Long contactId, String actor) {
        SupplierContact contact = findContact(supplierId, contactId);
        contact.delete(actor, clock.now());
        log(contact.getSupplier(), "담당자 삭제", describe(contact), null);
    }

    /** 업체의 소통 메모를 소통한 날짜가 최근인 순으로 조회한다. 지금 로그인한 사람이 지울 수 있는지도 함께 준다. */
    public PageResponse<NoteItem> notes(Long supplierId, String actor, boolean admin, Pageable pageable) {
        supplierService.find(supplierId);
        return PageResponse.of(notes.findBySupplierIdOrderByContactDateDescIdDesc(supplierId, pageable),
                n -> toItem(n, actor, admin));
    }

    /** 소통 메모를 남긴다. 소통한 날짜는 오늘까지만 허용한다 (앞날의 일은 기록이 아니다). */
    @Transactional
    public NoteItem addNote(Long supplierId, NoteRequest req, String actor, boolean admin) {
        Supplier supplier = supplierService.find(supplierId);
        if (req.contactDate().isAfter(clock.today())) {
            throw BusinessException.invalid("contactDate", "소통한 날짜는 오늘 이후일 수 없습니다.");
        }
        String authorName = users.findByUsername(actor).map(AppUser::getName).orElse(actor);
        SupplierNote note = notes.save(new SupplierNote(supplier, req.contactDate(), req.content().trim(), authorName));
        return toItem(note, actor, admin);
    }

    /** 메모를 삭제한다. 작성자 본인과 관리자만 할 수 있고, 지운 표시만 남긴다. */
    @Transactional
    public void deleteNote(Long supplierId, Long noteId, String actor, boolean admin) {
        supplierService.find(supplierId);
        SupplierNote note = notes.findByIdAndSupplierId(noteId, supplierId).orElseThrow(() -> BusinessException.notFound("메모"));
        if (!deletable(note, actor, admin)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "작성자 본인과 관리자만 메모를 삭제할 수 있습니다.");
        }
        note.delete(actor, clock.now());
    }

    /** 그 업체에 속한 담당자를 찾는다. 업체나 담당자가 없으면 404. */
    private SupplierContact findContact(Long supplierId, Long contactId) {
        supplierService.find(supplierId);
        return contacts.findByIdAndSupplierId(contactId, supplierId).orElseThrow(() -> BusinessException.notFound("담당자"));
    }

    /** 요청 값을 담당자에 반영한다. 빈 문자열은 null로 저장한다. */
    private static void apply(SupplierContact contact, ContactRequest req) {
        contact.update(req.name().trim(), blankToNull(req.title()), blankToNull(req.phone()), blankToNull(req.email()));
    }

    /** 변경 이력에 보여 줄 담당자 한 줄 요약: "홍길동 · 품질팀 과장 · 010-0000-0000 · a@b.c" (빈 항목은 뺀다) */
    private static String describe(SupplierContact c) {
        return Stream.of(c.getName(), c.getTitle(), c.getPhone(), c.getEmail())
                .filter(v -> v != null).collect(Collectors.joining(" · "));
    }

    /** 담당자 변경을 업체의 변경 이력(수정)으로 남긴다. 추가는 이전 값이, 삭제는 바뀐 값이 null이다. */
    private void log(Supplier supplier, String label, String before, String after) {
        Map<String, String> beforeMap = new LinkedHashMap<>();
        Map<String, String> afterMap = new LinkedHashMap<>();
        beforeMap.put(label, before);
        afterMap.put(label, after);
        history.record(ChangeTarget.SUPPLIER, supplier.getId(), supplier.getId(), supplier.getName(), supplier.getName(),
                ChangeAction.UPDATE, beforeMap, afterMap);
    }

    /** 메모를 응답 형식으로 바꾼다. */
    private static NoteItem toItem(SupplierNote n, String actor, boolean admin) {
        return new NoteItem(n.getId(), n.getContactDate(), n.getContent(), n.getCreatedBy(), n.getAuthorName(),
                n.getCreatedAt(), deletable(n, actor, admin));
    }

    /** 작성자 본인이거나 관리자면 지울 수 있다. */
    private static boolean deletable(SupplierNote note, String actor, boolean admin) {
        return admin || actor.equals(note.getCreatedBy());
    }

    /** 빈 문자열을 null로 바꾸고 앞뒤 공백을 제거한다. */
    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
