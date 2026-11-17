import { api, downloadFile } from './client'
import type { PageResponse } from './types'

/** 첨부파일 정보. */
export interface FileInfo {
  id: number
  originalName: string
  contentType: string
  size: number
  createdAt: string
  createdBy: string | null
}

/** 파일 정보와 연결된 계약·인증. 연결이 없으면 업체 일반 자료다. */
export interface SupplierFile {
  file: FileInfo
  contractId: number | null
  contractName: string | null
  certificationId: number | null
  certificationName: string | null
  /** 자료 종류. 붙이지 않았으면 null */
  categoryId: number | null
  categoryName: string | null
}

/** 전체 자료 검색 결과의 한 행. 파일이 있는 곳과, 그곳으로 이동할 때 쓸 ID를 담는다. */
export interface FileSearchItem {
  file: FileInfo
  /** "업체 자료" | "계약 첨부" | "인증 첨부" | "양식 자료실" */
  location: string
  supplierId: number | null
  supplierName: string | null
  contractName: string | null
  certificationName: string | null
  categoryName: string | null
  formId: number | null
  formTitle: string | null
  folderId: number | null
}

/** 업체 자료·계약·인증 첨부·양식 자료실 파일을 검색어와 자료 종류로 한 번에 찾는다. 조건이 없으면 최신순 전체다. */
export async function searchFiles(keyword: string, categoryId: number | undefined, page: number): Promise<PageResponse<FileSearchItem>> {
  return (await api.get('/files/search', { params: { keyword: keyword.trim() || undefined, categoryId, page, size: 20 } })).data
}

/** 업체 자료를 최신순으로 한 페이지 조회한다. 자료 종류를 주면 그 종류만 조회한다. */
export async function fetchSupplierFiles(supplierId: number, page: number, categoryId?: number): Promise<PageResponse<SupplierFile>> {
  return (await api.get(`/suppliers/${supplierId}/files`, { params: { page, size: 20, categoryId } })).data
}

/** 파일 하나를 업체에 올린다. 선택한 계약·인증이 있으면 해당 건에 연결한다. */
export async function uploadSupplierFile(supplierId: number, file: File, target?: string, categoryId?: number): Promise<SupplierFile> {
  const form = new FormData()
  form.append('file', file)
  if (categoryId) form.append('categoryId', String(categoryId))
  if (target) {
    const [kind, id] = target.split(':')
    form.append(kind === 'contract' ? 'contractId' : 'certificationId', id)
  }
  return (await api.post(`/suppliers/${supplierId}/files`, form)).data
}

/** 원래 파일 이름으로 다운로드한다. */
export async function downloadStoredFile(id: number): Promise<void> {
  await downloadFile(`/files/${id}/download`)
}

/** 파일을 삭제한다. 서버에는 삭제자·삭제일시 이력이 남는다. */
export async function deleteStoredFile(id: number): Promise<void> {
  await api.delete(`/files/${id}`)
}

/** 계약 한 건에 연결된 첨부파일 목록(최신순)을 조회한다. */
export async function fetchContractFiles(contractId: number): Promise<FileInfo[]> {
  return (await api.get(`/contracts/${contractId}/files`)).data
}

/** 인증 한 건에 연결된 첨부파일 목록(최신순)을 조회한다. */
export async function fetchCertificationFiles(certificationId: number): Promise<FileInfo[]> {
  return (await api.get(`/certifications/${certificationId}/files`)).data
}

/** 목록 화면의 첨부 아이콘 표시용으로, 여러 계약의 첨부 개수를 한 번에 조회한다. 자료가 없는 id는 응답에 없다(0으로 본다). */
export async function fetchContractFileCounts(ids: number[]): Promise<Record<number, number>> {
  if (ids.length === 0) return {}
  return (await api.get('/contracts/files/counts', { params: { ids: ids.join(',') } })).data
}

/** 목록 화면의 첨부 아이콘 표시용으로, 여러 인증의 첨부 개수를 한 번에 조회한다. */
export async function fetchCertificationFileCounts(ids: number[]): Promise<Record<number, number>> {
  if (ids.length === 0) return {}
  return (await api.get('/certifications/files/counts', { params: { ids: ids.join(',') } })).data
}

/** 자료 종류 한 줄. 사용 중지한 종류는 새로 고를 수 없지만 이미 붙인 파일에는 이름이 보인다. */
export interface FileCategory {
  id: number
  name: string
  active: boolean
}

/** 전체 자료 종류를 표시 순서대로 조회한다. */
export async function fetchFileCategories(): Promise<FileCategory[]> {
  return (await api.get('/file-categories')).data
}

/** 자료 종류를 추가한다. (관리자 전용) */
export async function createFileCategory(name: string): Promise<FileCategory> {
  return (await api.post('/admin/file-categories', { name })).data
}

/** 자료 종류의 이름을 바꾼다. (관리자 전용) */
export async function renameFileCategory(id: number, name: string): Promise<FileCategory> {
  return (await api.put(`/admin/file-categories/${id}`, { name })).data
}

/** 자료 종류를 사용 중지하거나 다시 사용한다. (관리자 전용) */
export async function changeFileCategoryActive(id: number, active: boolean): Promise<FileCategory> {
  return (await api.patch(`/admin/file-categories/${id}/active`, { active })).data
}

/** 이미 올린 파일의 자료 종류를 바꾼다. null이면 종류를 지운다. */
export async function changeFileCategory(fileId: number, categoryId: number | null): Promise<SupplierFile> {
  return (await api.patch(`/files/${fileId}/category`, { categoryId })).data
}
