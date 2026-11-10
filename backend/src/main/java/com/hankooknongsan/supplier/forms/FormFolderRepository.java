package com.hankooknongsan.supplier.forms;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;

/** 폴더 조회와 부모 행 잠금으로 동시 생성·삭제를 직렬화한다. */
public interface FormFolderRepository extends JpaRepository<FormFolder, Long> {
    /** 삭제되지 않은 폴더를 조회한다. */
    Optional<FormFolder> findByIdAndDeletedAtIsNull(Long id);

    /** 잠금 전에 오래된 엔티티를 읽지 않고 부모 식별자만 확인한다. */
    @Query("select f.parentId from FormFolder f where f.id = :id and f.deletedAt is null")
    Optional<Long> findParentId(Long id);

    /** 바로 아래의 폴더만 이름순으로 조회한다. */
    List<FormFolder> findByParentIdAndDeletedAtIsNullOrderByNameAscIdAsc(Long parentId);

    /** 살아 있는 하위 폴더의 존재 여부를 확인한다. */
    boolean existsByParentIdAndDeletedAtIsNull(Long parentId);

    /** 이름을 바꾸는 자기 자신은 제외하고 같은 부모 아래 이름 충돌을 확인한다. */
    @Query("select count(f) > 0 from FormFolder f where f.parentId = :parentId and f.deletedAt is null and lower(f.name) = lower(:name) and f.id <> :exceptId")
    boolean nameExists(Long parentId, String name, Long exceptId);

    /** 폴더 변경이나 내용 추가 전에 삭제되지 않은 행을 잠근다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from FormFolder f where f.id = :id and f.deletedAt is null")
    Optional<FormFolder> findForUpdate(Long id);
}
