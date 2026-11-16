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
}

/** 업체 자료를 최신순으로 한 페이지 조회한다. */
export async function fetchSupplierFiles(supplierId: number, page: number): Promise<PageResponse<SupplierFile>> {
  return (await api.get(`/suppliers/${supplierId}/files`, { params: { page, size: 20 } })).data
}

/** 파일 하나를 업체에 올린다. 선택한 계약·인증이 있으면 해당 건에 연결한다. */
export async function uploadSupplierFile(supplierId: number, file: File, target?: string): Promise<SupplierFile> {
  const form = new FormData()
  form.append('file', file)
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
