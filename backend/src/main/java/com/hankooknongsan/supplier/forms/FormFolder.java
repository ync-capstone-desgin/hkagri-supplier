package com.hankooknongsan.supplier.forms;

import java.time.LocalDateTime;
import com.hankooknongsan.supplier.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 사내 공유 양식 폴더. 실제 파일 경로와 무관한 논리적인 계층이다. */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FormFolder extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long parentId;
    @Column(nullable = false, length = 100)
    private String name;
    private LocalDateTime deletedAt;
    private String deletedBy;

    /** 검증한 부모 아래 새 폴더를 만든다. */
    FormFolder(Long parentId, String name) {
        this.parentId = parentId;
        this.name = name;
    }

    /** 폴더 위치는 유지하고 이름만 바꾼다. */
    void rename(String name) { this.name = name; }

    /** 빈 폴더를 논리 삭제해 감사 이력을 보존한다. */
    void delete(String actor, LocalDateTime at) {
        deletedBy = actor;
        deletedAt = at;
    }
}
