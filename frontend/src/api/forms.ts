import { api } from './client'
import type { PageResponse } from './types'
import type { SupplierFile } from './files'

/** 기존 양식 API에서 사용하는 분류. 폴더와 별도로 검색에 사용한다. */
export type FormCategory = 'CONTRACT' | 'CHECKLIST' | 'OTHER'
export const formCategories: { value: FormCategory; label: string }[] = [
  { value: 'CONTRACT', label: '계약 양식' }, { value: 'CHECKLIST', label: '점검표' }, { value: 'OTHER', label: '기타' },
]

/** 양식 목록에 표시하는 메타데이터. */
export interface FormPostItem {
  id: number
  title: string
  description: string | null
  category: FormCategory
  folderId: number
  createdAt: string
  createdBy: string | null
  updatedAt: string
  updatedBy: string | null
}

/** 상세 화면은 살아 있는 첨부파일을 함께 받는다. */
export interface FormDetail extends FormPostItem { files: SupplierFile['file'][] }

/** 파일 변경과 함께 보내는 양식 정보. */
export interface FormInput {
  title: string
  description?: string
  category: FormCategory
  folderId: number
  removeFileIds: number[]
}

/** 폴더 계층의 항목과 변경 정보. */
export interface FormFolder {
  id: number
  parentId: number | null
  name: string
  updatedAt: string
  updatedBy: string | null
}

/** 탐색할 폴더와 최상위부터의 경로, 직접 하위 폴더. */
export interface FormFolderView { folder: FormFolder; path: FormFolder[]; children: FormFolder[] }

/** 현재 폴더의 양식을 제목·분류로 검색한다. */
export async function fetchForms(folderId: number, keyword: string, category: FormCategory | undefined, page: number): Promise<PageResponse<FormPostItem>> {
  return (await api.get('/forms', { params: { folderId, keyword, category, page, size: 20 } })).data
}

/** 양식 설명과 첨부파일을 조회한다. */
export async function fetchForm(id: number): Promise<FormDetail> {
  return (await api.get(`/forms/${id}`)).data
}

/** JSON 메타데이터와 새 파일을 하나의 multipart 요청으로 저장한다. */
export async function saveForm(id: number | undefined, post: FormInput, files: File[]): Promise<FormDetail> {
  const data = new FormData()
  data.append('post', new Blob([JSON.stringify(post)], { type: 'application/json' }))
  for (const file of files) data.append('files', file)
  return (await api.request({ method: id === undefined ? 'POST' : 'PUT', url: id === undefined ? '/forms' : `/forms/${id}`, data })).data
}

/** 양식과 연결된 첨부파일을 함께 논리 삭제한다. */
export async function deleteForm(id: number): Promise<void> { await api.delete(`/forms/${id}`) }

/** 현재 폴더의 이름·경로와 직접 하위 폴더를 조회한다. */
export async function fetchFormFolder(id: number): Promise<FormFolderView> { return (await api.get(`/form-folders/${id}`)).data }

/** 현재 폴더 아래에 새 폴더를 만든다. */
export async function createFormFolder(parentId: number, name: string): Promise<FormFolder> {
  return (await api.post('/form-folders', { parentId, name })).data
}

/** 폴더 위치는 유지하고 이름만 변경한다. */
export async function renameFormFolder(id: number, name: string): Promise<FormFolder> {
  return (await api.put(`/form-folders/${id}`, { name })).data
}

/** 빈 폴더만 삭제한다. */
export async function deleteFormFolder(id: number): Promise<void> { await api.delete(`/form-folders/${id}`) }
