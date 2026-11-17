package com.hankooknongsan.supplier.supplier;

import com.hankooknongsan.supplier.common.DeletableEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

/**
 * 협력업체의 추가 담당자 (#49). 기본정보의 "업체 담당자"(대표 담당자) 외에 연락하는 사람을 여러 명 둔다.
 * 삭제는 다른 업무 자료와 같이 지운 표시만 남긴다.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("deleted_at is null")
public class SupplierContact extends DeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 이 담당자가 속한 협력업체 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    /** 이름 */
    private String name;
    /** 직책·부서 (예: 품질팀 과장) */
    private String title;
    /** 전화번호 */
    private String phone;
    /** 이메일 */
    private String email;

    /** 업체에 새 담당자를 만든다. 나머지 정보는 {@link #update}로 채운다. */
    SupplierContact(Supplier supplier) {
        this.supplier = supplier;
    }

    /** 담당자 정보를 한 번에 수정한다. */
    void update(String name, String title, String phone, String email) {
        this.name = name;
        this.title = title;
        this.phone = phone;
        this.email = email;
    }
}
