/** 백엔드 API 응답·요청 타입 모음. 백엔드 DTO(record)와 이름을 맞춘다. */

/** 사용자 역할 */
export type Role = 'ADMIN' | 'MANAGER' | 'VIEWER'

/** 협력업체 거래상태 */
export type SupplierStatus = 'ACTIVE' | 'SUSPENDED' | 'TERMINATED'

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
}

/** 서버 오류 응답(RFC 7807 ProblemDetail) */
export interface Problem {
  status: number
  detail: string
  errors?: { field: string; message: string }[]
}
