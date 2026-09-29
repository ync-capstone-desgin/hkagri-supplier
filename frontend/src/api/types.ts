/** 백엔드 API 응답·요청 타입 모음. 백엔드 DTO(record)와 이름을 맞춘다. */

/** 사용자 역할 */
export type Role = 'ADMIN' | 'MANAGER' | 'VIEWER'

/** 협력업체 거래상태 */
export type SupplierStatus = 'ACTIVE' | 'SUSPENDED' | 'TERMINATED'

/** 계약·인증 갱신 처리상태 */
export type RenewalStatus = 'NONE' | 'IN_PROGRESS' | 'DONE' | 'NOT_RENEWING'

/** 날짜로 계산한 만료상태 */
export type ExpiryStatus = 'EXPIRED' | 'IMMINENT' | 'NORMAL' | 'UNKNOWN'

/** 만료 관리 대상 종류 */
export type TargetType = 'CONTRACT' | 'CERTIFICATION'

/** 만료 예정 목록 조회 구간 */
export type ExpiryPeriod = 'EXPIRED' | 'D7' | 'D30' | 'D90' | 'UNKNOWN'

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
  /** 종료일까지 남은 일수 (지나면 음수, 무기한이면 null) */
  daysLeft: number | null
  expiryStatus: ExpiryStatus
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
  /** 만료일까지 남은 일수 (지나면 음수, 없으면 null) */
  daysLeft: number | null
  expiryStatus: ExpiryStatus
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

/** 만료 예정 목록의 한 행 (계약·인증 공통) */
export interface ExpiryItem {
  targetType: TargetType
  targetId: number
  supplierId: number
  supplierName: string
  supplierStatus: SupplierStatus
  /** 계약명 또는 인증 종류 이름 */
  name: string
  /** 계약번호 또는 인증번호 */
  number: string
  expiryDate: string | null
  daysLeft: number | null
  expiryStatus: ExpiryStatus
  renewalStatus: RenewalStatus
  ownerId: number | null
  ownerName: string | null
}

/** 만료 예정 목록 검색 조건 */
export interface ExpirySearch {
  period: ExpiryPeriod
  targetType?: TargetType
  ownerId?: number
  renewalStatus?: RenewalStatus
}

/** 대시보드 요약 */
export interface DashboardSummary {
  today: string
  supplierTotal: number
  supplierActive: number
  activeContracts: number
  activeCertifications: number
  expired: number
  within7: number
  within30: number
  within90: number
  unknown: number
  inProgress: number
  notStarted: number
  /** 조치 대상 중 로그인 사용자 담당 건수 */
  mine: number
  urgent: ExpiryItem[]
}

/** 기준일 정보 */
export interface ClockInfo {
  today: string
  /** 시연용으로 실제 날짜에서 옮겨져 있는지 */
  shifted: boolean
  /** 기준일 변경 기능 사용 가능 여부 (dev 환경) */
  demoClock: boolean
  horizonDays: number
}
