package com.hankooknongsan.supplier.forms;

import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/** 양식 게시글 조회와 같은 게시글의 동시 변경을 위한 잠금. */
public interface FormPostRepository extends JpaRepository<FormPost, Long> {
    /** 논리 삭제된 게시글을 제외한다. */
    Optional<FormPost> findByIdAndDeletedAtIsNull(Long id);

    /** 파일이 없어도 살아 있는 양식이 있으면 폴더를 비어 있지 않은 것으로 본다. */
    boolean existsByFolderIdAndDeletedAtIsNull(Long folderId);

    /** 제목과 분류로 검색하며 최신 게시글부터 반환한다. */
    @Query("""
            select p from FormPost p where p.deletedAt is null
              and (:category is null or p.category = :category)
              and (:folderId is null or p.folderId = :folderId)
              and lower(p.title) like lower(concat('%', :keyword, '%'))
            order by p.createdAt desc, p.id desc""")
    Page<FormPost> search(String keyword, FormCategory category, Long folderId, Pageable pageable);

    /** 수정·삭제 요청이 같은 게시글의 첨부를 동시에 바꾸지 않도록 먼저 행을 잠근다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from FormPost p where p.id = :id and p.deletedAt is null")
    Optional<FormPost> findForUpdate(Long id);
}
