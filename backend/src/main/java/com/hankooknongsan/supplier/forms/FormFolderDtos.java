package com.hankooknongsan.supplier.forms;

import java.time.LocalDateTime;
import java.util.List;
import jakarta.validation.constraints.*;

/** 폴더 탐색·이름 변경 요청과 응답. */
public final class FormFolderDtos {
    /** 유틸리티 클래스의 생성을 막는다. */
    private FormFolderDtos() { }

    /** 생성할 부모 폴더와 이름. */
    public record CreateRequest(@NotNull @Positive Long parentId, @NotBlank @Size(max = 100) String name) { }

    /** 위치 변경 없이 이름만 수정한다. */
    public record RenameRequest(@NotBlank @Size(max = 100) String name) { }

    /** 폴더 식별자·이름과 표시할 감사 정보. */
    public record FolderItem(Long id, Long parentId, String name, LocalDateTime updatedAt, String updatedBy) {
        /** 엔티티를 폴더 탐색 항목으로 변환한다. */
        static FolderItem of(FormFolder folder) {
            return new FolderItem(folder.getId(), folder.getParentId(), folder.getName(), folder.getUpdatedAt(), folder.getUpdatedBy());
        }
    }

    /** 현재 폴더, 최상위부터의 경로, 직접 하위 폴더. */
    public record FolderView(FolderItem folder, List<FolderItem> path, List<FolderItem> children) { }
}
