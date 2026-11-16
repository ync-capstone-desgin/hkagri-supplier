package com.hankooknongsan.supplier.forms;

import java.time.LocalDateTime;
import java.util.List;
import com.hankooknongsan.supplier.file.FileDtos.FileInfo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 자료실 게시글 요청과 목록·상세 응답. 첨부는 기존 파일 DTO를 사용한다. */
public final class FormDtos {
    /** DTO 모음이므로 직접 생성하지 않는다. */
    private FormDtos() { }

    /** 등록·수정의 JSON post 파트. 삭제 지정이 없으면 기존 첨부를 유지한다. */
    public record PostRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 5000) String description,
            @NotBlank @Size(max = 20) String category,
            List<@NotNull @Positive Long> removeFileIds,
            @Positive Long folderId) {
        /** 폴더를 지정하지 않는 기존 호출은 최상위 등록·기존 위치 수정으로 유지한다. */
        public PostRequest(String title, String description, String category, List<Long> removeFileIds) {
            this(title, description, category, removeFileIds, null);
        }
    }

    /** 페이지 목록에는 파일을 펼치지 않고 게시글 정보만 전달한다. */
    public record PostItem(Long id, String title, String description, FormCategory category, Long folderId,
                           LocalDateTime createdAt, String createdBy, LocalDateTime updatedAt, String updatedBy) {
        /** 게시글을 목록 응답으로 바꾼다. */
        static PostItem of(FormPost post) {
            return new PostItem(post.getId(), post.getTitle(), post.getDescription(), post.getCategory(), post.getFolderId(),
                    post.getCreatedAt(), post.getCreatedBy(), post.getUpdatedAt(), post.getUpdatedBy());
        }
    }

    /** 삭제되지 않은 첨부의 파일 정보가 포함된 상세 응답. */
    public record PostDetail(Long id, String title, String description, FormCategory category, Long folderId,
                             LocalDateTime createdAt, String createdBy, LocalDateTime updatedAt, String updatedBy,
                             List<FileInfo> files) {
        /** 공통 파일 API에서 먼저 삭제한 첨부도 응답에서 제외한다. */
        static PostDetail of(FormPost post) {
            return new PostDetail(post.getId(), post.getTitle(), post.getDescription(), post.getCategory(), post.getFolderId(),
                    post.getCreatedAt(), post.getCreatedBy(), post.getUpdatedAt(), post.getUpdatedBy(),
                    post.getFiles().stream().filter(f -> !f.isDeleted()).map(FileInfo::of).toList());
        }
    }
}
