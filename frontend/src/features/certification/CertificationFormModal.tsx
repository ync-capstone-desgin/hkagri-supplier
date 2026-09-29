import { useEffect, useState } from 'react'
import { Alert, App, Col, DatePicker, Form, Input, Modal, Row, Select } from 'antd'
import { useQueryClient } from '@tanstack/react-query'
import dayjs, { type Dayjs } from 'dayjs'
import { createCertification, renewCertification, updateCertification } from '../../api/certifications'
import { toProblem } from '../../api/client'
import type { CertificationItem, CertificationRequest } from '../../api/types'
import { fromDayjs, toDayjs } from '../../components/format'
import { useCertTypeOptions, useOwnerOptions, useSupplierOptions } from '../../hooks/useOptions'
import { useSaveShortcut } from '../../hooks/useSaveShortcut'

/** 모달 동작 방식: 신규 등록 / 수정 / 갱신(새 인증 등록 + 기존 인증을 이전 이력으로) */
export type CertificationFormMode = 'create' | 'edit' | 'renew'

/** 입력 폼 값. 날짜는 날짜 선택기(dayjs) 값으로 다룬다. */
interface FormValues extends Omit<CertificationRequest, 'issuedDate' | 'expiryDate'> {
  issuedDate: Dayjs
  expiryDate?: Dayjs | null
}

/**
 * 모드에 맞는 입력 초기값을 만든다.
 * 갱신은 업체·인증 종류를 그대로 두고, 인증번호·날짜·파일 위치는 새로 입력하도록 비운다(발급일은 오늘로 제안).
 */
function initialValues(mode: CertificationFormMode, cert?: CertificationItem, supplierId?: number): Partial<FormValues> {
  if (!cert) {
    return { supplierId }
  }
  const base: Partial<FormValues> = {
    supplierId: cert.supplierId,
    certTypeCode: cert.certTypeCode,
    certificateNumber: cert.certificateNumber,
    issuedDate: toDayjs(cert.issuedDate),
    expiryDate: toDayjs(cert.expiryDate),
    ownerId: cert.ownerId,
    documentUrl: cert.documentUrl ?? undefined,
    memo: cert.memo ?? undefined,
  }
  if (mode === 'renew') {
    return { ...base, certificateNumber: undefined, documentUrl: undefined, issuedDate: dayjs(), expiryDate: undefined }
  }
  return base
}

/**
 * 인증 등록·수정·갱신 모달 (FR-006, FR-012).
 * - 담당자를 비워두면 업체의 내부 담당자가 자동 지정된다.
 * - 만료일을 비워두면 "만료일 없음"으로 저장되며, 만료 점검에서 확인 필요 대상으로 분류된다.
 */
export function CertificationFormModal({ open, mode, certification, supplierId, onClose }: {
  open: boolean
  mode: CertificationFormMode
  certification?: CertificationItem
  /** 업체 상세 화면에서 열 때 업체를 미리 선택해 둔다. */
  supplierId?: number
  onClose: () => void
}) {
  const { message } = App.useApp()
  const queryClient = useQueryClient()
  const [form] = Form.useForm<FormValues>()
  const [saving, setSaving] = useState(false)
  const supplierOptions = useSupplierOptions(open)
  const ownerOptions = useOwnerOptions(open)
  const certTypeOptions = useCertTypeOptions()
  useSaveShortcut(open, () => form.submit())

  // 모달을 열 때마다 모드에 맞는 초기값으로 채운다.
  useEffect(() => {
    if (!open) return
    form.resetFields()
    form.setFieldsValue(initialValues(mode, certification, supplierId))
  }, [open, mode, certification, supplierId, form])

  /** 입력값을 서버 요청 형식으로 바꿔 저장하고, 관련 목록을 새로 고친 뒤 모달을 닫는다. */
  const handleSubmit = async (values: FormValues) => {
    const req: CertificationRequest = { ...values, issuedDate: fromDayjs(values.issuedDate)!, expiryDate: fromDayjs(values.expiryDate) }
    setSaving(true)
    try {
      const saved =
        mode === 'create' ? await createCertification(req)
          : mode === 'edit' ? await updateCertification(certification!.id, req)
            : await renewCertification(certification!.id, req)
      await Promise.all(['certifications', 'suppliers', 'supplier'].map((k) => queryClient.invalidateQueries({ queryKey: [k] })))
      message.success(mode === 'renew' ? '갱신 인증을 등록했습니다. 기존 인증은 이전 이력으로 옮겨졌습니다.' : '저장했습니다.')
      saved.warnings.forEach((w) => message.warning(w, 6))
      onClose()
    } catch (e) {
      const problem = toProblem(e)
      if (problem.errors?.length) {
        form.setFields(problem.errors.map((fe) => ({ name: fe.field as keyof FormValues, errors: [fe.message] })))
      } else {
        message.error(problem.detail)
      }
    } finally {
      setSaving(false)
    }
  }

  const title = mode === 'create' ? '인증 등록'
    : mode === 'edit' ? `인증 수정 · ${certification?.certificateNumber}` : `인증 갱신 · ${certification?.certTypeName}`

  return (
    <Modal open={open} title={title} okText="저장" cancelText="취소" width={640} confirmLoading={saving}
      onOk={() => form.submit()} onCancel={onClose} destroyOnHidden>
      {mode === 'renew' && (
        <Alert type="info" showIcon style={{ margin: '12px 0' }}
          title="새로 발급받은 인증 정보를 입력해 주세요. 저장하면 기존 인증은 '갱신완료' 이력으로 남습니다." />
      )}
      <Form form={form} layout="vertical" onFinish={handleSubmit} style={{ marginTop: 16 }}>
        <Row gutter={16}>
          <Col span={24}>
            <Form.Item name="supplierId" label="협력업체" rules={[{ required: true, message: '협력업체를 선택해 주세요.' }]}>
              <Select showSearch={{ optionFilterProp: 'label' }} placeholder="업체명으로 검색" options={supplierOptions}
                disabled={mode !== 'create' || !!supplierId} />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item name="certTypeCode" label="인증 종류" rules={[{ required: true, message: '인증 종류를 선택해 주세요.' }]}>
              <Select options={certTypeOptions} placeholder="선택" disabled={mode === 'renew'} />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item name="certificateNumber" label="인증번호" rules={[{ required: true, message: '인증번호를 입력해 주세요.' }]}>
              <Input maxLength={50} className="num" autoFocus={mode !== 'edit'} />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item name="issuedDate" label="발급일" rules={[{ required: true, message: '발급일을 입력해 주세요.' }]}>
              <DatePicker style={{ width: '100%' }} />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item name="expiryDate" label="만료일" extra="비워두면 '만료일 없음(확인 필요)'"
              dependencies={['issuedDate']}
              rules={[({ getFieldValue }) => ({
                validator: (_, v?: Dayjs | null) => {
                  const issued: Dayjs | undefined = getFieldValue('issuedDate')
                  return v && issued && v.isBefore(issued, 'day')
                    ? Promise.reject(new Error('만료일은 발급일보다 빠를 수 없습니다.'))
                    : Promise.resolve()
                },
              })]}>
              <DatePicker style={{ width: '100%' }} />
            </Form.Item>
          </Col>
          <Col span={10}>
            <Form.Item name="ownerId" label="내부 담당자" extra="비워두면 업체 담당자로 지정">
              <Select allowClear placeholder="업체 담당자와 같음" options={ownerOptions} />
            </Form.Item>
          </Col>
          <Col span={14}>
            <Form.Item name="documentUrl" label="인증서 위치(링크)" rules={[{ type: 'url', message: 'http:// 또는 https://로 시작하는 주소를 입력해 주세요.' }]}>
              <Input placeholder="https://" maxLength={500} />
            </Form.Item>
          </Col>
          <Col span={24}>
            <Form.Item name="memo" label="비고">
              <Input.TextArea rows={2} maxLength={1000} showCount />
            </Form.Item>
          </Col>
        </Row>
      </Form>
    </Modal>
  )
}
