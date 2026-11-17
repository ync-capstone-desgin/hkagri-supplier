package com.hankooknongsan.supplier.supplier;

import java.time.LocalDate;

import com.hankooknongsan.supplier.common.DeletableEntity;
import jakarta.persistence.Column;
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
 * 협력업체와 주고받은 내용을 남기는 소통 메모 (#49).
 * 기록이 나중에 바뀌지 않도록 수정은 두지 않는다. 잘못 쓴 메모는 작성자나 관리자가 지우고 다시 쓴다.
 * 작성자 아이디와 작성 시각은 공통 감사 항목(createdBy, createdAt)에 남는다.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("deleted_at is null")
public class SupplierNote extends DeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 이 메모가 속한 협력업체 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    /** 실제로 통화·메일 등으로 소통한 날짜. 작성한 날과 다를 수 있다. */
    private LocalDate contactDate;

    /** 메모 내용 */
    @Column(columnDefinition = "text")
    private String content;

    /** 작성 시점의 작성자 이름 (나중에 이름이 바뀌어도 그대로 보여 준다) */
    private String authorName;

    /** 소통한 날짜·내용·작성자 이름으로 새 메모를 만든다. */
    SupplierNote(Supplier supplier, LocalDate contactDate, String content, String authorName) {
        this.supplier = supplier;
        this.contactDate = contactDate;
        this.content = content;
        this.authorName = authorName;
    }
}
