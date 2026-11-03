package com.hankooknongsan.supplier.file;

import com.hankooknongsan.supplier.common.BusinessException;
import com.hankooknongsan.supplier.common.PageResponse;
import com.hankooknongsan.supplier.contract.ContractRepository;
import com.hankooknongsan.supplier.certification.CertificationRepository;
import com.hankooknongsan.supplier.file.FileDtos.SupplierFile;
import com.hankooknongsan.supplier.supplier.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** 업체 자료 연결과 목록 조회. 파일 저장·검증·롤백 정리는 기존 공통 서비스를 사용한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SupplierFileService {
    private final FileService fileService;
    private final StoredFileRepository files;
    private final SupplierRepository suppliers;
    private final ContractRepository contracts;
    private final CertificationRepository certifications;

    /** 업체 존재와 페이지 범위를 검사하고 최신 자료부터 반환한다. */
    public PageResponse<SupplierFile> list(Long supplierId, int page, int size) {
        if (!suppliers.existsById(supplierId)) throw BusinessException.notFound("협력업체");
        if (page < 0 || size < 1 || size > 100) {
            throw BusinessException.invalid("page", "페이지는 0 이상, 페이지 크기는 1~100이어야 합니다.");
        }
        return PageResponse.of(files.findForSupplier(supplierId, PageRequest.of(page, size)), SupplierFile::of);
    }

    /** 업체에 파일을 올린다. 계약·인증 연결은 같은 업체의 건 하나만 허용하며 저장과 연결은 함께 커밋한다. */
    @Transactional
    public SupplierFile upload(Long supplierId, MultipartFile file, Long contractId, Long certificationId) {
        var supplier = suppliers.findById(supplierId).orElseThrow(() -> BusinessException.notFound("협력업체"));
        if (contractId != null && certificationId != null) {
            throw BusinessException.invalid("contractId", "계약과 인증 중 하나만 연결해 주세요.");
        }
        var contract = contractId == null ? null : contracts.findById(contractId)
                .orElseThrow(() -> BusinessException.notFound("계약"));
        var certification = certificationId == null ? null : certifications.findById(certificationId)
                .orElseThrow(() -> BusinessException.notFound("인증"));
        if (contract != null && !contract.getSupplier().getId().equals(supplierId)) {
            throw BusinessException.invalid("contractId", "이 업체의 계약만 연결할 수 있습니다.");
        }
        if (certification != null && !certification.getSupplier().getId().equals(supplierId)) {
            throw BusinessException.invalid("certificationId", "이 업체의 인증만 연결할 수 있습니다.");
        }
        var info = fileService.upload(file);
        var saved = files.findById(info.id()).orElseThrow();
        saved.attachTo(supplier, contract, certification);
        files.flush();
        return SupplierFile.of(saved);
    }
}
