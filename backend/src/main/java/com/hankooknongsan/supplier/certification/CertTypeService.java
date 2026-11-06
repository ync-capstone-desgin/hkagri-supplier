package com.hankooknongsan.supplier.certification;

import java.util.Locale;
import java.util.UUID;

import com.hankooknongsan.supplier.certification.CertificationDtos.CertTypeItem;
import com.hankooknongsan.supplier.common.BusinessException;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 종류의 식별자와 기존 인증 연결을 유지하면서 이름·사용 여부만 관리한다. */
@Service
@RequiredArgsConstructor
@Transactional
public class CertTypeService {
    private final CertTypeRepository types;

    /** 새 종류를 마지막 표시 순서에 추가하고 불변 코드를 생성한다. */
    public CertTypeItem create(CertTypeDtos.CreateRequest request) {
        String name = cleanName(request.name());
        String code = "CT" + UUID.randomUUID().toString().replace("-", "").substring(0, 28);
        String key = nameKey(name);
        checkDuplicate(key, code);
        return save(new CertType(code, name, key, types.nextSortOrder()));
    }

    /** 현재 사용 여부를 유지하면서 이름만 수정한다. */
    public CertTypeItem update(String code, CertTypeDtos.UpdateRequest request) {
        CertType type = types.findForUpdate(code).orElseThrow(() -> BusinessException.notFound("인증 종류"));
        String name = cleanName(request.name());
        String key = nameKey(name);
        checkDuplicate(key, code);
        type.update(name, key, type.isActive());
        return save(type);
    }

    /** 현재 이름을 유지하면서 사용 여부만 변경한다. */
    public CertTypeItem changeActive(String code, boolean active) {
        CertType type = types.findForUpdate(code).orElseThrow(() -> BusinessException.notFound("인증 종류"));
        type.update(type.getName(), type.getNameKey(), active);
        return save(type);
    }

    /** 자기 자신을 제외하고 비활성 종류까지 포함해 중복을 검사한다. */
    private void checkDuplicate(String key, String code) {
        if (types.existsByNameKeyAndCodeNot(key, code)) {
            throw BusinessException.invalid("name", "같은 이름의 인증 종류가 있습니다. 비활성 종류도 확인해 주세요.");
        }
    }

    /** 동시 저장의 이름 충돌도 입력칸 오류로 돌려준다. */
    private CertTypeItem save(CertType type) {
        try {
            return CertTypeItem.of(types.saveAndFlush(type));
        } catch (DataIntegrityViolationException e) {
            // 동시 등록은 DB 유일 제약으로 막고, 실패한 트랜잭션에서 재조회하지 않는다.
            for (Throwable cause = e; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException violation
                        && "uq_cert_type_name_key".equals(violation.getConstraintName())) {
                    throw BusinessException.invalid("name", "같은 이름의 인증 종류가 있습니다. 비활성 종류도 확인해 주세요.");
                }
            }
            throw e;
        }
    }

    /** 표시 이름의 유니코드 공백을 정리하고 빈 이름을 차단한다. */
    private static String cleanName(String value) {
        String name = value.replaceAll("(?U)\\s+", " ").trim();
        if (name.isEmpty()) throw BusinessException.invalid("name", "인증 종류 이름을 입력해 주세요.");
        return name;
    }

    /** 표시 이름과 별도로 공백·대소문자를 무시하는 비교 키를 만든다. */
    private static String nameKey(String name) {
        return name.replace(" ", "").toLowerCase(Locale.ROOT);
    }
}
