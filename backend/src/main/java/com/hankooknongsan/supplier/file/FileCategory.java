package com.hankooknongsan.supplier.file;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 자료 종류 (#54): 사업자등록증·계약서·성적서처럼 업체 자료가 어떤 서류인지 구분하는 이름.
 * 인증 종류와 같은 방식으로 관리자가 추가·이름 수정·사용 중지를 한다. 이미 붙인 파일을 보존하려고 삭제는 두지 않는다.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FileCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 화면 표시 이름 */
    private String name;

    /** 공백·대소문자를 제외한 이름의 중복 비교 키 */
    @Column(nullable = false, length = 300)
    private String nameKey;

    /** 새로 고를 수 있는지 여부. 사용 중지해도 이미 붙인 파일의 종류는 그대로 보인다. */
    private boolean active = true;

    /** 선택상자 표시 순서 */
    private int sortOrder;

    /** 새 종류는 사용 상태로 시작한다. */
    FileCategory(String name, String nameKey, int sortOrder) {
        this.name = name;
        this.nameKey = nameKey;
        this.sortOrder = sortOrder;
    }

    /** 이름을 바꾼다. */
    void rename(String name, String nameKey) {
        this.name = name;
        this.nameKey = nameKey;
    }

    /** 사용 여부를 바꾼다. */
    void changeActive(boolean active) {
        this.active = active;
    }
}
