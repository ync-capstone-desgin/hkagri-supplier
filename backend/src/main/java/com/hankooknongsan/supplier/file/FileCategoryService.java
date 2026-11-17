package com.hankooknongsan.supplier.file;

import java.util.List;
import java.util.Locale;

import com.hankooknongsan.supplier.common.AppClock;
import com.hankooknongsan.supplier.common.BusinessException;
import com.hankooknongsan.supplier.common.SupplierPurgedEvent;
import com.hankooknongsan.supplier.file.FileCategoryDtos.CategoryItem;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 자료 종류 조회·관리 서비스 (#54). 인증 종류 관리와 같은 규칙(이름 중복 불가, 삭제 대신 사용 중지)을 쓴다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FileCategoryService {

    private final FileCategoryRepository categories;
    private final DocExemptionRepository exemptions;
    private final AppClock clock;

    /** 전체 종류를 표시 순서대로 조회한다. 화면이 사용 중지한 종류의 이름도 보여 줄 수 있게 모두 준다. */
    public List<CategoryItem> list() {
        return categories.findAllByOrderBySortOrderAscIdAsc().stream().map(CategoryItem::of).toList();
    }

    /** 새 종류를 마지막 순서에 추가한다. */
    @Transactional
    public CategoryItem create(String rawName) {
        String name = cleanName(rawName);
        // shortcut: 두 관리자가 같은 이름을 동시에 추가하면 DB 유일 제약이 막아 500이 난다. 관리자가 늘면 인증 종류처럼 제약 위반을 입력 오류로 바꾼다.
        if (categories.existsByNameKey(nameKey(name))) {
            throw duplicate();
        }
        return CategoryItem.of(categories.save(new FileCategory(name, nameKey(name), categories.nextSortOrder())));
    }

    /** 이름만 바꾼다. 이미 붙인 파일에는 새 이름이 보인다. */
    @Transactional
    public CategoryItem rename(Long id, String rawName) {
        FileCategory category = find(id);
        String name = cleanName(rawName);
        if (categories.existsByNameKeyAndIdNot(nameKey(name), id)) {
            throw duplicate();
        }
        category.rename(name, nameKey(name));
        return CategoryItem.of(category);
    }

    /** 사용 여부만 바꾼다. 사용 중지하면 새로 고를 수 없고, 이미 붙인 파일의 종류는 그대로 남는다. */
    @Transactional
    public CategoryItem changeActive(Long id, boolean active) {
        FileCategory category = find(id);
        category.changeActive(active);
        return CategoryItem.of(category);
    }

    /** 필수 서류 여부만 바꾼다. 필수로 하면 모든 업체에서 이 종류의 파일이 있는지 점검한다. */
    @Transactional
    public CategoryItem changeRequired(Long id, boolean required) {
        FileCategory category = find(id);
        category.changeRequired(required);
        return CategoryItem.of(category);
    }

    /** 한 업체의 필수 서류별 제출 여부를 조회한다 (#47). 필수 종류가 없으면 빈 목록이다. */
    public List<FileCategoryDtos.RequiredDocument> requiredDocuments(Long supplierId) {
        return categories.requiredDocuments(supplierId).stream()
                .map(r -> new FileCategoryDtos.RequiredDocument(r.getCategoryId(), r.getName(), r.getFileCount(),
                        r.getExemptCount() > 0)).toList();
    }

    /**
     * 필수 서류 하나를 이 업체에서 해당 없음으로 빼거나 되돌린다 (#85).
     * 뺀 종류는 그 업체의 누락 건수와 누락 업체 목록에서 빠진다. 이미 그 상태면 아무것도 하지 않는다.
     */
    @Transactional
    public List<FileCategoryDtos.RequiredDocument> changeExempt(Long supplierId, Long categoryId, boolean exempt, String actor) {
        if (categories.countLiveSupplier(supplierId) == 0) {
            throw BusinessException.notFound("협력업체");
        }
        FileCategory category = find(categoryId);
        if (!exempt) {
            exemptions.deleteOne(supplierId, categoryId);
        } else if (!exemptions.existsBySupplierIdAndCategoryId(supplierId, categoryId)) {
            if (!category.isRequired()) {
                throw BusinessException.invalid("categoryId", "필수 서류가 아닌 종류입니다.");
            }
            exemptions.save(new DocExemption(supplierId, categoryId, actor, clock.now()));
        }
        return requiredDocuments(supplierId);
    }

    /** 협력업체를 영구삭제하면 그 업체의 예외도 지운다 (#77, #85). */
    @EventListener
    @Transactional
    public void onSupplierPurged(SupplierPurgedEvent event) {
        exemptions.deleteBySupplier(event.supplierId());
    }

    /** 파일에 붙일 종류를 찾는다. null이면 종류 없음, 사용 중지한 종류는 새로 붙일 수 없다. */
    FileCategory forAssign(Long id) {
        if (id == null) {
            return null;
        }
        FileCategory category = categories.findById(id)
                .orElseThrow(() -> BusinessException.invalid("categoryId", "자료 종류를 찾을 수 없습니다."));
        if (!category.isActive()) {
            throw BusinessException.invalid("categoryId", "사용 중지한 자료 종류입니다. 사용 중인 종류를 선택해 주세요.");
        }
        return category;
    }

    /** ID로 종류를 찾는다. 없으면 404. */
    private FileCategory find(Long id) {
        return categories.findById(id).orElseThrow(() -> BusinessException.notFound("자료 종류"));
    }

    /** 이름 중복 오류 */
    private static BusinessException duplicate() {
        return BusinessException.invalid("name", "같은 이름의 자료 종류가 있습니다. 사용 중지한 종류도 확인해 주세요.");
    }

    /** 이름의 공백을 정리하고 빈 이름을 막는다. */
    private static String cleanName(String value) {
        String name = value.replaceAll("(?U)\\s+", " ").trim();
        if (name.isEmpty()) {
            throw BusinessException.invalid("name", "자료 종류 이름을 입력해 주세요.");
        }
        return name;
    }

    /** 공백·대소문자를 무시하는 중복 비교 키를 만든다. */
    private static String nameKey(String name) {
        return name.replace(" ", "").toLowerCase(Locale.ROOT);
    }
}
