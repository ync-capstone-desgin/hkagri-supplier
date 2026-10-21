package com.hankooknongsan.supplier.supplier;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.hankooknongsan.supplier.auth.AppUser;
import com.hankooknongsan.supplier.auth.AppUserRepository;
import com.hankooknongsan.supplier.common.ExpiryPolicy;
import com.hankooknongsan.supplier.common.BusinessException;
import com.hankooknongsan.supplier.common.PageResponse;
import com.hankooknongsan.supplier.supplier.SupplierDtos.BusinessNumberCheck;
import com.hankooknongsan.supplier.supplier.SupplierDtos.DuplicateSupplier;
import com.hankooknongsan.supplier.supplier.SupplierDtos.SupplierDetail;
import com.hankooknongsan.supplier.supplier.SupplierDtos.SupplierListItem;
import com.hankooknongsan.supplier.supplier.SupplierDtos.SupplierRequest;
import com.hankooknongsan.supplier.supplier.SupplierRepository.SupplierCounts;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 협력업체 등록·조회·수정·검색 업무 서비스 (FR-001~004).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SupplierService {

    private final SupplierRepository suppliers;
    private final AppUserRepository users;
    private final ExpiryPolicy policy;

    /** 조건에 맞는 협력업체 목록을 페이지 단위로 조회한다. */
    public PageResponse<SupplierListItem> search(String keyword, SupplierStatus status, String certType,
                                                 Pageable pageable) {
        Page<Supplier> page = suppliers.search(nullToEmpty(keyword).trim(), status, nullToEmpty(certType), pageable);
        Map<Long, SupplierCounts> counts = countsOf(page.getContent());
        return PageResponse.of(page, s -> toListItem(s, counts.get(s.getId())));
    }

    /** 조건에 맞는 협력업체 전체를 조회한다. (엑셀 다운로드용) */
    public List<SupplierListItem> searchAll(String keyword, SupplierStatus status, String certType) {
        return search(keyword, status, certType, Pageable.ofSize(10_000)).content();
    }

    /** 협력업체 한 곳의 상세정보를 조회한다. 없으면 404. */
    public SupplierDetail get(Long id) {
        Supplier s = find(id);
        SupplierCounts c = countsOf(List.of(s)).get(id);
        AppUser owner = s.getOwner();
        return new SupplierDetail(s.getId(), s.getCode(), s.getName(), s.getBusinessNumber(), s.getStatus(),
                s.getContactName(), s.getContactPhone(), s.getContactEmail(),
                owner == null ? null : owner.getId(), owner == null ? null : owner.getName(), s.getMemo(),
                c.getContractCount(), c.getCertCount(), c.getExpiringCount(),
                s.getCreatedAt(), s.getCreatedBy(), s.getUpdatedAt(), s.getUpdatedBy());
    }

    /**
     * 협력업체를 등록한다.
     * 저장 후 발급된 ID로 업체코드(SUP-0001 형식)를 부여한다.
     */
    @Transactional
    public SupplierDetail create(SupplierRequest req) {
        Supplier s = new Supplier(req.name(), req.status());
        apply(s, req);
        suppliers.save(s);
        s.assignCode("SUP-%04d".formatted(s.getId()));
        suppliers.flush();
        return get(s.getId());
    }

    /** 협력업체 기본정보를 수정한다. */
    @Transactional
    public SupplierDetail update(Long id, SupplierRequest req) {
        apply(find(id), req);
        suppliers.flush();
        return get(id);
    }

    /** ID로 협력업체 엔티티를 찾는다. 다른 기능(계약·인증)에서도 업체 확인에 사용한다. */
    public Supplier find(Long id) {
        return suppliers.findById(id).orElseThrow(() -> BusinessException.notFound("협력업체"));
    }

    /**
     * 사업자번호를 확인한다: 검증번호가 맞는지, 같은 번호로 등록된 다른 업체가 있는지.
     * 화면이 입력 중에 바로 알려주는 용도이며, 중복은 경고일 뿐 저장을 막지 않는다.
     *
     * @param number   확인할 사업자번호 (000-00-00000)
     * @param exceptId 수정 중인 업체 ID (중복 목록에서 제외, 등록이면 null)
     */
    public BusinessNumberCheck checkBusinessNumber(String number, Long exceptId) {
        List<DuplicateSupplier> duplicates = suppliers.findByBusinessNumberOrderByIdAsc(number).stream()
                .filter(x -> !x.getId().equals(exceptId))
                .map(x -> new DuplicateSupplier(x.getId(), x.getCode(), x.getName(), x.getStatus()))
                .toList();
        return new BusinessNumberCheck(BusinessNumber.isValid(number), duplicates);
    }

    /**
     * 요청 값을 엔티티에 반영한다. 담당자 ID가 잘못되면 입력 오류, 빈 문자열은 null로 저장한다.
     * 사업자번호는 새로 입력하거나 바꾼 경우에만 검증번호를 확인한다.
     * (이미 저장된 번호를 그대로 두고 다른 칸만 고칠 때는 막지 않는다)
     */
    private void apply(Supplier s, SupplierRequest req) {
        String businessNumber = blankToNull(req.businessNumber());
        if (businessNumber != null && !businessNumber.equals(s.getBusinessNumber())
                && !BusinessNumber.isValid(businessNumber)) {
            throw BusinessException.invalid("businessNumber", "올바른 사업자번호가 아닙니다. 숫자를 다시 확인해 주세요.");
        }
        AppUser owner = req.ownerId() == null ? null
                : users.findById(req.ownerId()).orElseThrow(() -> BusinessException.invalid("ownerId", "담당자를 찾을 수 없습니다."));
        s.update(req.name().trim(), businessNumber, req.status(), blankToNull(req.contactName()),
                blankToNull(req.contactPhone()), blankToNull(req.contactEmail()), owner, blankToNull(req.memo()));
    }

    /**
     * 업체들의 계약·인증 건수를 한 번에 조회해 업체 ID별로 묶는다.
     * "만료건수"는 만료 판정 규칙과 같은 기준(이미 만료 + 만료예정 기간 이내)을 쓴다.
     */
    private Map<Long, SupplierCounts> countsOf(List<Supplier> list) {
        if (list.isEmpty()) {
            return Map.of();
        }
        return suppliers.counts(list.stream().map(Supplier::getId).toList(),
                        policy.horizon())
                .stream().collect(Collectors.toMap(SupplierCounts::getId, Function.identity()));
    }

    /** 엔티티와 건수를 목록 행 DTO로 변환한다. */
    private static SupplierListItem toListItem(Supplier s, SupplierCounts c) {
        AppUser owner = s.getOwner();
        return new SupplierListItem(s.getId(), s.getCode(), s.getName(), s.getStatus(), s.getContactName(),
                owner == null ? null : owner.getId(), owner == null ? null : owner.getName(),
                c.getContractCount(), c.getCertCount(), c.getExpiringCount());
    }

    /** null을 빈 문자열로 바꾼다. (검색 조건 무시 처리용) */
    private static String nullToEmpty(String v) {
        return v == null ? "" : v;
    }

    /** 빈 문자열을 null로 바꾸고 앞뒤 공백을 제거한다. */
    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
