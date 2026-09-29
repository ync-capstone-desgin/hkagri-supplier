import { useEffect, useState } from 'react'
import { Alert, App, Col, DatePicker, Form, Input, Modal, Row, Select, Switch } from 'antd'
import { useQueryClient } from '@tanstack/react-query'
import type { Dayjs } from 'dayjs'
import { toProblem } from '../../api/client'
import { createContract, renewContract, updateContract } from '../../api/contracts'
import type { ContractItem, ContractRequest } from '../../api/types'
import { fromDayjs, toDayjs } from '../../components/format'
import { useOwnerOptions, useSupplierOptions } from '../../hooks/useOptions'
import { useSaveShortcut } from '../../hooks/useSaveShortcut'

/** 모달 동작 방식: 신규 등록 / 수정 / 갱신(새 계약 등록 + 기존 계약을 이전 이력으로) */
export type ContractFormMode = 'create' | 'edit' | 'renew'

/** 입력 폼 값. 날짜는 날짜 선택기(dayjs) 값으로 다룬다. */
interface FormValues extends Omit<ContractRequest, 'startDate' | 'endDate'> {
  startDate: Dayjs
  endDate?: Dayjs | null
}

/**
 * 모드에 맞는 입력 초기값을 만든다.
 * 갱신은 기존 계약 내용을 가져오되, 기간은 기존 종료일 다음날부터 1년으로 제안하고 계약번호는 비운다.
 */
function initialValues(mode: ContractFormMode, contract?: ContractItem, supplierId?: number): Partial<FormValues> {
  if (!contract) {
    return { supplierId, autoRenew: false }
  }
  const base: Partial<FormValues> = {
    supplierId: contract.supplierId,
    contractNumber: contract.contractNumber,
    title: contract.title,
    startDate: toDayjs(contract.startDate),
    endDate: toDayjs(contract.endDate),
    autoRenew: contract.autoRenew,
    ownerId: contract.ownerId,
    documentUrl: contract.documentUrl ?? undefined,
    memo: contract.memo ?? undefined,
  }
  if (mode === 'renew') {
    const start = contract.endDate ? toDayjs(contract.endDate)!.add(1, 'day') : undefined
    return { ...base, contractNumber: undefined, documentUrl: undefined, startDate: start, endDate: start?.add(1, 'year').subtract(1, 'day') }
  }
  return base
}

/**
 * 계약 등록·수정·갱신 모달 (FR-005, FR-012).
 * - 담당자를 비워두면 업체의 내부 담당자가 자동 지정된다.
 * - 종료일을 비워두면 무기한 계약이 된다.
 * - 저장은 됐지만 계약번호 중복 등 확인할 내용이 있으면 경고 알림을 띄운다.
 */
export function ContractFormModal({ open, mode, contract, supplierId, onClose }: {
  open: boolean
  mode: ContractFormMode
  contract?: ContractItem
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
  useSaveShortcut(open, () => form.submit())

  // 모달을 열 때마다 모드에 맞는 초기값으로 채운다.
  useEffect(() => {
    if (!open) return
    form.resetFields()
    form.setFieldsValue(initialValues(mode, contract, supplierId))
  }, [open, mode, contract, supplierId, form])

  /** 입력값을 서버 요청 형식으로 바꿔 저장하고, 관련 목록을 새로 고친 뒤 모달을 닫는다. */
  const handleSubmit = async (values: FormValues) => {
    const req: ContractRequest = { ...values, startDate: fromDayjs(values.startDate)!, endDate: fromDayjs(values.endDate) }
    setSaving(true)
    try {
      const saved =
        mode === 'create' ? await createContract(req)
          : mode === 'edit' ? await updateContract(contract!.id, req)
            : await renewContract(contract!.id, req)
      await Promise.all(['contracts', 'suppliers', 'supplier', 'expiries', 'dashboard'].map((k) => queryClient.invalidateQueries({ queryKey: [k] })))
      message.success(mode === 'renew' ? '갱신 계약을 등록했습니다. 기존 계약은 이전 이력으로 옮겨졌습니다.' : '저장했습니다.')
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

  const title = mode === 'create' ? '계약 등록' : mode === 'edit' ? `계약 수정 · ${contract?.contractNumber}` : `계약 갱신 · ${contract?.contractNumber}`

  return (
    <Modal open={open} title={title} okText="저장" cancelText="취소" width={680} confirmLoading={saving}
      onOk={() => form.submit()} onCancel={onClose} destroyOnHidden>
      {mode === 'renew' && (
        <Alert type="info" showIcon style={{ margin: '12px 0' }}
          title="새 계약 정보를 입력해 주세요. 저장하면 기존 계약은 '갱신완료' 이력으로 남습니다." />
      )}
      <Form form={form} layout="vertical" onFinish={handleSubmit} style={{ marginTop: 16 }}>
        <Row gutter={16}>
          <Col span={24}>
            <Form.Item name="supplierId" label="협력업체" rules={[{ required: true, message: '협력업체를 선택해 주세요.' }]}>
              <Select showSearch={{ optionFilterProp: 'label' }} placeholder="업체명으로 검색" options={supplierOptions}
                disabled={mode !== 'create' || !!supplierId} />
            </Form.Item>
          </Col>
          <Col span={10}>
            <Form.Item name="contractNumber" label="계약번호" rules={[{ required: true, message: '계약번호를 입력해 주세요.' }]}>
              <Input maxLength={50} className="num" autoFocus={mode !== 'edit'} />
            </Form.Item>
          </Col>
          <Col span={14}>
            <Form.Item name="title" label="계약명" rules={[{ required: true, message: '계약명을 입력해 주세요.' }]}>
              <Input maxLength={200} />
            </Form.Item>
          </Col>
          <Col span={8}>
            <Form.Item name="startDate" label="시작일" rules={[{ required: true, message: '시작일을 입력해 주세요.' }]}>
              <DatePicker style={{ width: '100%' }} />
            </Form.Item>
          </Col>
          <Col span={8}>
            <Form.Item name="endDate" label="종료일" extra="비워두면 무기한 계약"
              dependencies={['startDate']}
              rules={[({ getFieldValue }) => ({
                validator: (_, v?: Dayjs | null) => {
                  const start: Dayjs | undefined = getFieldValue('startDate')
                  return v && start && v.isBefore(start, 'day')
                    ? Promise.reject(new Error('종료일은 시작일보다 빠를 수 없습니다.'))
                    : Promise.resolve()
                },
              })]}>
              <DatePicker style={{ width: '100%' }} />
            </Form.Item>
          </Col>
          <Col span={8}>
            <Form.Item name="autoRenew" label="자동연장" valuePropName="checked">
              <Switch checkedChildren="예" unCheckedChildren="아니오" />
            </Form.Item>
          </Col>
          <Col span={10}>
            <Form.Item name="ownerId" label="내부 담당자" extra="비워두면 업체 담당자로 지정">
              <Select allowClear placeholder="업체 담당자와 같음" options={ownerOptions} />
            </Form.Item>
          </Col>
          <Col span={14}>
            <Form.Item name="documentUrl" label="계약서 위치(링크)" rules={[{ type: 'url', message: 'http:// 또는 https://로 시작하는 주소를 입력해 주세요.' }]}>
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
