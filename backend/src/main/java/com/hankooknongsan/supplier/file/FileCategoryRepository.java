package com.hankooknongsan.supplier.file;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** 자료 종류 저장소 */
public interface FileCategoryRepository extends JpaRepository<FileCategory, Long> {

    /** 표시 순서대로 전체 종류를 조회한다. 사용 중지한 종류도 포함한다. */
    List<FileCategory> findAllByOrderBySortOrderAscIdAsc();

    /** 같은 이름(공백·대소문자 무시)이 이미 있는지 확인한다. 사용 중지한 종류도 대상이다. */
    boolean existsByNameKey(String nameKey);

    /** 자기 자신을 뺀 같은 이름이 있는지 확인한다. (이름 수정용) */
    boolean existsByNameKeyAndIdNot(String nameKey, Long id);

    /** 마지막 표시 순서 다음 값을 구한다. */
    @Query("select coalesce(max(c.sortOrder), 0) + 1 from FileCategory c")
    int nextSortOrder();
}
