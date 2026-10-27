package com.hankooknongsan.supplier.file;

import com.hankooknongsan.supplier.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 첨부파일 정보 엔티티 (FR-021, FR-022 공통).
 * 파일 내용은 {@link com.hankooknongsan.supplier.file.storage.FileStorage}에 두고,
 * 여기에는 원래 이름·형식·크기와 저장 위치(키)를 기록한다. 올린 사람·시각은 등록자·등록일시로 남는다.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoredFile extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 사용자가 올린 원래 파일 이름 (내려받을 때 이 이름을 쓴다) */
    private String originalName;
    /** 파일 형식 (예: application/pdf) */
    private String contentType;
    /** 파일 크기 (바이트) */
    @Column(name = "size_bytes")
    private long size;
    /** 저장소 안의 위치. 원래 이름 대신 서버가 만든 고유 키를 쓴다. */
    private String storageKey;

    /** 저장소에 넣은 파일의 정보로 새 첨부파일 기록을 만든다. */
    public StoredFile(String originalName, String contentType, long size, String storageKey) {
        this.originalName = originalName;
        this.contentType = contentType;
        this.size = size;
        this.storageKey = storageKey;
    }
}
