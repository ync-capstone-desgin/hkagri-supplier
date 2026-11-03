package com.hankooknongsan.supplier.forms;

import java.util.HashSet;
import java.util.List;
import com.hankooknongsan.supplier.common.AppClock;
import com.hankooknongsan.supplier.common.BusinessException;
import com.hankooknongsan.supplier.common.PageResponse;
import com.hankooknongsan.supplier.file.FileService;
import com.hankooknongsan.supplier.file.StoredFileRepository;
import com.hankooknongsan.supplier.forms.FormDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** 게시글과 첨부의 변경을 하나의 트랜잭션으로 처리한다. 실패한 새 파일은 FileService가 정리한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FormService {
    private final FormPostRepository posts;
    private final StoredFileRepository files;
    private final FileService fileService;
    private final AppClock clock;

    /** 제목 검색·분류 필터·페이지 크기를 검증하고 목록을 반환한다. */
    public PageResponse<PostItem> list(String keyword, String category, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw BusinessException.invalid("page", "페이지는 0 이상, 페이지 크기는 1~100이어야 합니다.");
        }
        FormCategory filter = category == null || category.isBlank() ? null : FormCategory.parse(category);
        return PageResponse.of(posts.search(keyword == null ? "" : keyword.strip(), filter, PageRequest.of(page, size)), PostItem::of);
    }

    /** 살아 있는 게시글과 첨부만 조회한다. */
    public PostDetail get(Long id) {
        return PostDetail.of(posts.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> BusinessException.notFound("양식 게시글")));
    }

    /** 게시글과 여러 파일을 함께 저장한다. 기존 파일 ID를 임의로 연결하지 않는다. */
    @Transactional
    public PostDetail create(PostRequest req, List<MultipartFile> uploads) {
        if (req.removeFileIds() != null && !req.removeFileIds().isEmpty()) {
            throw BusinessException.invalid("removeFileIds", "등록할 때는 기존 첨부를 삭제할 수 없습니다.");
        }
        var post = posts.save(new FormPost(req.title(), req.description(), FormCategory.parse(req.category())));
        upload(post, uploads);
        posts.flush();
        return PostDetail.of(post);
    }

    /** 지정한 기존 첨부만 삭제하고 새 파일을 추가한다. 실패하면 제목·첨부 변경 모두 롤백된다. */
    @Transactional
    public PostDetail update(Long id, PostRequest req, List<MultipartFile> uploads, String actor) {
        var post = locked(id);
        var removeIds = req.removeFileIds() == null ? List.<Long>of() : req.removeFileIds();
        var currentIds = post.getFiles().stream().filter(f -> !f.isDeleted()).map(f -> f.getId()).toList();
        if (new HashSet<>(removeIds).size() != removeIds.size() || !currentIds.containsAll(removeIds)) {
            throw BusinessException.invalid("removeFileIds", "이 게시글의 현재 첨부만 한 번씩 삭제할 수 있습니다.");
        }
        post.update(req.title(), req.description(), FormCategory.parse(req.category()));
        upload(post, uploads);
        post.getFiles().removeIf(f -> removeIds.contains(f.getId()));
        // 공통 파일 삭제는 영속성 컨텍스트를 비우므로 게시글·연결 변경을 먼저 반영한다.
        posts.flush();
        for (Long fileId : removeIds) fileService.delete(fileId, actor);
        return get(id);
    }

    /** 게시글과 남은 첨부를 논리 삭제하고 최초 삭제자·시각을 남긴다. */
    @Transactional
    public void delete(Long id, String actor) {
        var post = locked(id);
        var ids = post.getFiles().stream().filter(f -> !f.isDeleted()).map(f -> f.getId()).toList();
        post.delete(actor, clock.now());
        posts.flush();
        for (Long fileId : ids) fileService.delete(fileId, actor);
    }

    /** 삭제 여부를 검사하면서 게시글을 잠근다. */
    private FormPost locked(Long id) {
        return posts.findForUpdate(id).orElseThrow(() -> BusinessException.notFound("양식 게시글"));
    }

    /** 검증된 새 파일만 연결한다. 모든 업로드는 호출자의 트랜잭션에 참여한다. */
    private void upload(FormPost post, List<MultipartFile> uploads) {
        if (uploads == null) return;
        for (MultipartFile file : uploads) {
            var info = fileService.upload(file);
            post.getFiles().add(files.findById(info.id()).orElseThrow());
        }
    }
}
