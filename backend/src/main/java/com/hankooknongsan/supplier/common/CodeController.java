package com.hankooknongsan.supplier.common;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.hankooknongsan.supplier.auth.Role;
import com.hankooknongsan.supplier.supplier.SupplierStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공통 코드 API.
 * 화면의 선택상자·상태 태그에 쓰는 코드값과 한글 이름을 한 번에 내려준다.
 * 프론트엔드는 한글 이름을 따로 하드코딩하지 않고 이 값을 사용한다.
 */
@RestController
@RequestMapping("/api/codes")
public class CodeController {

    /** 코드 하나: 저장값(code)과 표시 이름(label) */
    public record Code(String code, String label) {
    }

    /** 코드 그룹별 코드 목록을 반환한다. 기능이 추가될 때마다 그룹을 늘린다. */
    @GetMapping
    public Map<String, List<Code>> codes() {
        Map<String, List<Code>> codes = new LinkedHashMap<>();
        codes.put("supplierStatus", of(SupplierStatus.values()));
        codes.put("renewalStatus", of(RenewalStatus.values()));
        codes.put("role", of(Role.values()));
        return codes;
    }

    /** enum 값 배열을 코드 목록으로 변환한다. */
    private static <E extends Enum<E> & Labeled> List<Code> of(E[] values) {
        return Arrays.stream(values).map(v -> new Code(v.name(), v.label())).toList();
    }
}
