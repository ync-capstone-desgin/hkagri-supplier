package com.hankooknongsan.supplier.forms;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import com.hankooknongsan.supplier.common.BaseEntity;
import com.hankooknongsan.supplier.file.StoredFile;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 업체와 무관한 사내 공용 양식 게시글. 첨부 내용과 삭제 이력은 공통 파일 저장소에 둔다. */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FormPost extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 200)
    private String title;
    @Column(length = 5000)
    private String description;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FormCategory category;
    @Column(nullable = false)
    private Long folderId;
    @Version
    private long version;
    private LocalDateTime deletedAt;
    private String deletedBy;
    @OneToMany
    @JoinTable(name = "form_post_file", joinColumns = @JoinColumn(name = "post_id"),
            inverseJoinColumns = @JoinColumn(name = "file_id", unique = true))
    @OrderBy("id ASC")
    private List<StoredFile> files = new ArrayList<>();

    /** 새 게시글의 제목·설명·분류를 설정한다. */
    FormPost(String title, String description, FormCategory category, Long folderId) {
        this.folderId = folderId;
        update(title, description, category);
    }

    /** 화면에서 보낸 게시글 정보를 정규화해 저장한다. */
    void update(String title, String description, FormCategory category) {
        this.title = title.strip();
        this.description = description == null || description.isBlank() ? null : description.strip();
        this.category = category;
    }

    /** 게시글은 감사 기록을 남기고 조회에서만 제외한다. */
    void delete(String by, LocalDateTime at) {
        this.deletedBy = by;
        this.deletedAt = at;
    }
}
