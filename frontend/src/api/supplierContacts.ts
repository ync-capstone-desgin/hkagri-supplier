import { api } from './client'
import type { PageResponse } from './types'

/** 협력업체의 추가 담당자 한 명 */
export interface SupplierContact {
  id: number
  name: string
  title: string | null
  phone: string | null
  email: string | null
}

/** 담당자 등록·수정 요청. 이름만 필수다. */
export interface SupplierContactRequest {
  name: string
  title?: string
  phone?: string
  email?: string
}

/** 소통 메모 한 건. deletable은 지금 로그인한 사람이 지울 수 있는지(작성자 본인 또는 관리자)다. */
export interface SupplierNote {
  id: number
  contactDate: string
  content: string
  author: string
  authorName: string
  createdAt: string
  deletable: boolean
}

/** 업체의 추가 담당자를 등록한 순서대로 조회한다. */
export async function fetchSupplierContacts(supplierId: number): Promise<SupplierContact[]> {
  return (await api.get(`/suppliers/${supplierId}/contacts`)).data
}

/** 업체에 담당자를 추가한다. */
export async function createSupplierContact(supplierId: number, req: SupplierContactRequest): Promise<SupplierContact> {
  return (await api.post(`/suppliers/${supplierId}/contacts`, req)).data
}

/** 담당자 정보를 수정한다. */
export async function updateSupplierContact(supplierId: number, contactId: number, req: SupplierContactRequest): Promise<SupplierContact> {
  return (await api.put(`/suppliers/${supplierId}/contacts/${contactId}`, req)).data
}

/** 담당자를 삭제한다. */
export async function deleteSupplierContact(supplierId: number, contactId: number): Promise<void> {
  await api.delete(`/suppliers/${supplierId}/contacts/${contactId}`)
}

/** 업체의 소통 메모를 소통한 날짜가 최근인 순으로 조회한다. */
export async function fetchSupplierNotes(supplierId: number, page: number): Promise<PageResponse<SupplierNote>> {
  return (await api.get(`/suppliers/${supplierId}/notes`, { params: { page, size: 20 } })).data
}

/** 소통 메모를 남긴다. contactDate는 실제로 소통한 날짜(YYYY-MM-DD)다. */
export async function createSupplierNote(supplierId: number, contactDate: string, content: string): Promise<SupplierNote> {
  return (await api.post(`/suppliers/${supplierId}/notes`, { contactDate, content })).data
}

/** 소통 메모의 날짜와 내용을 고친다. 작성자 본인과 관리자만 할 수 있다. */
export async function updateSupplierNote(supplierId: number, noteId: number, contactDate: string, content: string): Promise<SupplierNote> {
  return (await api.put(`/suppliers/${supplierId}/notes/${noteId}`, { contactDate, content })).data
}

/** 소통 메모를 삭제한다. 작성자 본인과 관리자만 할 수 있다. */
export async function deleteSupplierNote(supplierId: number, noteId: number): Promise<void> {
  await api.delete(`/suppliers/${supplierId}/notes/${noteId}`)
}
