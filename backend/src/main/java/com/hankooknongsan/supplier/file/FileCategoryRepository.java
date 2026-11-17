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

    /** 한 업체의 필수 서류 점검 결과 한 줄 (조회 결과 형식) */
    interface RequiredRow {
        Long getCategoryId();
        String getName();
        long getFileCount();
    }

    /**
     * 사용 중인 필수 종류마다, 그 업체에 올라온 파일 수를 센다 (#47). 계약·인증에 붙인 파일도 그 업체의 자료로 센다.
     * 파일이 없는 종류도 0건으로 나와야 하므로 종류를 기준으로 조회한다.
     */
    @Query("""
            select fc.id as categoryId, fc.name as name,
                   (select count(f) from StoredFile f left join f.contract fct left join f.certification fx
                     where f.category = fc and f.deletedAt is null
                       and (f.supplier.id = :supplierId or fct.supplier.id = :supplierId or fx.supplier.id = :supplierId)) as fileCount
            from FileCategory fc where fc.required = true and fc.active = true
            order by fc.sortOrder, fc.id
            """)
    List<RequiredRow> requiredDocuments(Long supplierId);

    /** 마지막 표시 순서 다음 값을 구한다. */
    @Query("select coalesce(max(c.sortOrder), 0) + 1 from FileCategory c")
    int nextSortOrder();
}
