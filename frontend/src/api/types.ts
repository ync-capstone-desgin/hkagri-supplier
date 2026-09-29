/** 백엔드 API 응답·요청 타입 모음. 백엔드 DTO(record)와 이름을 맞춘다. */

/** 사용자 역할 */
export type Role = 'ADMIN' | 'MANAGER' | 'VIEWER'

/** 협력업체 거래상태 */
export type SupplierStatus = 'ACTIVE' | 'SUSPENDED' | 'TERMINATED'

/** 계약·인증 갱신 처리상태 */
export type RenewalStatus = 'NONE' | 'IN_PROGRESS' | 'DONE' | 'NOT_RENEWING'

/** 로그인한 사용자 정보 */
export interface Me {
  id: number
  username: string
  name: string
  role: Role
}

/** 담당자 선택상자용 사용자 요약 */
export interface UserOption {
  id: number
  name: string
  role: Role
  email: string | null
}

/** 공통 코드 하나 (저장값 + 한글 이름) */
export interface Code {
  code: string
  label: string
}

/** 코드 그룹 이름 → 코드 목록 */
export type Codes = Record<string, Code[]>

/** 목록 API 공통 응답 */
export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
}

/** 협력업체 목록 그리드의 한 행 */
export interface SupplierListItem {
  id: number
  code: string
  name: string
  status: SupplierStatus
  contactName: string | null
  ownerId: number | null
  ownerName: string | null
  /** 유효 계약 수 */
  contractCount: number
  /** 유효 인증 수 */
  certCount: number
  /** 만료됐거나 90일 안에 만료되는 계약·인증 수 */
  expiringCount: number
}

/** 협력업체 상세 */
export interface SupplierDetail {
  id: number
  code: string
  name: string
  businessNumber: string | null
  status: SupplierStatus
  contactName: string | null
  contactPhone: string | null
  contactEmail: string | null
  ownerId: number | null
  ownerName: string | null
  memo: string | null
  contractCount: number
  certCount: number
  expiringCount: number
  createdAt: string
  createdBy: string
  updatedAt: string
  updatedBy: string
}

/** 협력업체 등록·수정 요청 */
export interface SupplierRequest {
  name: string
  businessNumber?: string
  status: SupplierStatus
  contactName?: string
  contactPhone?: string
  contactEmail?: string
  ownerId?: number
  memo?: string
}

/** 협력업체 검색 조건 */
export interface SupplierSearch {
  keyword?: string
  status?: SupplierStatus
  /** 이 인증 종류를 보유한 업체만 */
  certType?: string
}

/** 서버 오류 응답(RFC 7807 ProblemDetail) */
export interface Problem {
  status: number
  detail: string
  errors?: { field: string; message: string }[]
}

/** 등록·수정·갱신 결과. 저장은 됐지만 확인이 필요한 내용(번호 중복 등)은 warnings에 온다. */
export interface Saved<T> {
  item: T
  warnings: string[]
}

/** 계약 (목록·상세 공통) */
export interface ContractItem {
  id: number
  supplierId: number
  supplierName: string
  contractNumber: string
  title: string
  startDate: string
  /** null이면 무기한 */
  endDate: string | null
  autoRenew: boolean
  renewalStatus: RenewalStatus
  ownerId: number | null
  ownerName: string | null
  /** 갱신되어 이전 이력이 되면 false */
  active: boolean
  prevId: number | null
  documentUrl: string | null
  memo: string | null
  createdAt: string
  createdBy: string
  updatedAt: string
  updatedBy: string
}

/** 계약 등록·수정·갱신 요청 */
export interface ContractRequest {
  supplierId: number
  contractNumber: string
  title: string
  startDate: string
  endDate?: string | null
  autoRenew?: boolean
  ownerId?: number | null
  documentUrl?: string
  memo?: string
}

/** 계약 검색 조건 */
export interface ContractSearch {
  supplierId?: number
  keyword?: string
  ownerId?: number
  renewalStatus?: RenewalStatus
  includeInactive?: boolean
}

/** 인증 종류 */
export interface CertType {
  code: string
  name: string
}

/** 인증 (목록·상세 공통) */
export interface CertificationItem {
  id: number
  supplierId: number
  supplierName: string
  certTypeCode: string
  certTypeName: string
  certificateNumber: string
  issuedDate: string
  /** null이면 만료일 없음(확인 필요) */
  expiryDate: string | null
  renewalStatus: RenewalStatus
  ownerId: number | null
  ownerName: string | null
  active: boolean
  prevId: number | null
  documentUrl: string | null
  memo: string | null
  createdAt: string
  createdBy: string
  updatedAt: string
  updatedBy: string
}

/** 인증 등록·수정·갱신 요청 */
export interface CertificationRequest {
  supplierId: number
  certTypeCode: string
  certificateNumber: string
  issuedDate: string
  expiryDate?: string | null
  ownerId?: number | null
  documentUrl?: string
  memo?: string
}

/** 인증 검색 조건 */
export interface CertificationSearch {
  supplierId?: number
  keyword?: string
  certType?: string
  ownerId?: number
  renewalStatus?: RenewalStatus
  includeInactive?: boolean
}
