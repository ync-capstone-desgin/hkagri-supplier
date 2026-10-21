import { useEffect, useState } from 'react'
import { App, Col, Form, Input, Modal, Row, Select } from 'antd'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { fetchUsers } from '../../api/auth'
import { toProblem } from '../../api/client'
import { checkBusinessNumber, createSupplier, updateSupplier } from '../../api/suppliers'
import type { SupplierDetail, SupplierRequest } from '../../api/types'
import { useCodes } from '../../hooks/useCodes'
import { useSaveShortcut } from '../../hooks/useSaveShortcut'
import { isValidBusinessNumber } from './businessNumber'

/**
 * 사업자번호 입력값을 000-00-00000 형식으로 맞춘다.
 * 숫자만 남긴 뒤 자릿수에 따라 하이픈을 넣어, 사용자가 하이픈을 직접 치지 않아도 되게 한다.
 */
function formatBusinessNumber(value: string): string {
  const d = value.replace(/\D/g, '').slice(0, 10)
  if (d.length <= 3) return d
  if (d.length <= 5) return `${d.slice(0, 3)}-${d.slice(3)}`
  return `${d.slice(0, 3)}-${d.slice(3, 5)}-${d.slice(5)}`
}

/**
 * 협력업체 등록·수정 모달.
 * supplier가 있으면 수정, 없으면 등록 모드로 동작한다.
 * 서버 검증 오류는 해당 입력칸 아래에 표시하고, Ctrl(⌘)+S로도 저장할 수 있다.
 */
export function SupplierFormModal({ open, supplier, onClose }: {
  open: boolean
  supplier?: SupplierDetail
  onClose: () => void
}) {
  const { message } = App.useApp()
  const queryClient = useQueryClient()
  const { options } = useCodes()
  const [form] = Form.useForm<SupplierRequest>()
  const [saving, setSaving] = useState(false)
  const { data: users } = useQuery({ queryKey: ['users'], queryFn: fetchUsers, enabled: open })
  const isEdit = !!supplier

  // 모달을 열 때마다 수정 대상 값(또는 등록 기본값)으로 입력칸을 채운다.
  useEffect(() => {
    if (!open) return
    form.resetFields()
    form.setFieldsValue(
      supplier
        ? {
            name: supplier.name,
            businessNumber: supplier.businessNumber ?? undefined,
            status: supplier.status,
            contactName: supplier.contactName ?? undefined,
            contactPhone: supplier.contactPhone ?? undefined,
            contactEmail: supplier.contactEmail ?? undefined,
            ownerId: supplier.ownerId ?? undefined,
            memo: supplier.memo ?? undefined,
          }
        : { status: 'ACTIVE' },
    )
  }, [open, supplier, form])

  // 모달이 열려 있는 동안 Ctrl(⌘)+S를 저장 단축키로 쓴다.
  useSaveShortcut(open, () => form.submit())

  /**
   * 사업자번호 검증번호 확인 규칙. 10자리를 다 입력했을 때만 검사한다.
   * 수정 화면에서 예전에 저장된 번호를 그대로 두면 검사하지 않는다 (서버 규칙과 같음).
   */
  const checkDigitRule = {
    validator: async (_: unknown, value?: string) => {
      if (!value || !/^\d{3}-\d{2}-\d{5}$/.test(value) || value === supplier?.businessNumber) return
      if (!isValidBusinessNumber(value)) throw new Error('올바른 사업자번호가 아닙니다. 숫자를 다시 확인해 주세요.')
    },
  }

  /**
   * 같은 사업자번호로 이미 등록된 업체가 있으면 노란 경고로 알려준다. (warningOnly: 저장은 막지 않음)
   * 지점·본점처럼 같은 번호를 쓰는 경우가 있을 수 있어 막지는 않는다.
   */
  const duplicateRule = {
    warningOnly: true,
    validator: async (_: unknown, value?: string) => {
      if (!value || !isValidBusinessNumber(value)) return
      const result = await checkBusinessNumber(value, supplier?.id)
      if (result.duplicates.length > 0) {
        const names = result.duplicates.map((d) => `${d.name}(${d.code})`).join(', ')
        throw new Error(`이미 등록된 번호: ${names}`)
      }
    },
  }

  /** 입력값을 서버에 저장하고, 성공하면 목록·상세 데이터를 새로 고친 뒤 모달을 닫는다. */
  const handleSubmit = async (values: SupplierRequest) => {
    setSaving(true)
    try {
      const saved = isEdit ? await updateSupplier(supplier.id, values) : await createSupplier(values)
      await queryClient.invalidateQueries({ queryKey: ['suppliers'] })
      queryClient.setQueryData(['supplier', saved.id], saved)
      message.success(isEdit ? '수정했습니다.' : `${saved.name}(${saved.code})을(를) 등록했습니다.`)
      onClose()
    } catch (e) {
      const problem = toProblem(e)
      if (problem.errors?.length) {
        form.setFields(problem.errors.map((fe) => ({ name: fe.field as keyof SupplierRequest, errors: [fe.message] })))
      } else {
        message.error(problem.detail)
      }
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal
      open={open}
      title={isEdit ? `협력업체 수정 · ${supplier.code}` : '협력업체 등록'}
      okText="저장"
      cancelText="취소"
      width={640}
      confirmLoading={saving}
      onOk={() => form.submit()}
      onCancel={onClose}
      destroyOnHidden
    >
      <Form form={form} layout="vertical" onFinish={handleSubmit} style={{ marginTop: 16 }}>
        <Row gutter={16}>
          <Col span={16}>
            <Form.Item name="name" label="업체명" rules={[{ required: true, message: '업체명을 입력해 주세요.' }]}>
              <Input maxLength={100} autoFocus />
            </Form.Item>
          </Col>
          <Col span={8}>
            <Form.Item name="status" label="거래상태" rules={[{ required: true, message: '거래상태를 선택해 주세요.' }]}>
              <Select options={options('supplierStatus')} />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item
              name="businessNumber"
              label="사업자번호"
              normalize={formatBusinessNumber}
              rules={[{ pattern: /^\d{3}-\d{2}-\d{5}$/, message: '사업자번호 10자리를 입력해 주세요.' }, checkDigitRule, duplicateRule]}
              validateDebounce={300}
              hasFeedback
            >
              <Input placeholder="숫자만 입력" inputMode="numeric" className="num" />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item name="ownerId" label="내부 담당자">
              <Select
                allowClear
                placeholder="선택"
                options={users?.filter((u) => u.role !== 'VIEWER').map((u) => ({ value: u.id, label: u.name }))}
              />
            </Form.Item>
          </Col>
          <Col span={8}>
            <Form.Item name="contactName" label="업체 담당자">
              <Input maxLength={50} />
            </Form.Item>
          </Col>
          <Col span={8}>
            <Form.Item name="contactPhone" label="연락처">
              <Input maxLength={30} className="num" />
            </Form.Item>
          </Col>
          <Col span={8}>
            <Form.Item name="contactEmail" label="이메일" rules={[{ type: 'email', message: '이메일 형식이 올바르지 않습니다.' }]}>
              <Input maxLength={100} />
            </Form.Item>
          </Col>
          <Col span={24}>
            <Form.Item name="memo" label="비고">
              <Input.TextArea rows={3} maxLength={1000} showCount />
            </Form.Item>
          </Col>
        </Row>
      </Form>
    </Modal>
  )
}
