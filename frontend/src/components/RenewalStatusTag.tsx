import { Select, Tag } from 'antd'
import type { RenewalStatus } from '../api/types'
import { useCodes } from '../hooks/useCodes'

/** 갱신 처리상태별 태그 색 */
const COLOR: Record<RenewalStatus, string> = {
  NONE: 'default',
  IN_PROGRESS: 'processing',
  DONE: 'success',
  NOT_RENEWING: 'warning',
}

/** 갱신 처리상태를 색상 태그로 보여준다. */
export function RenewalStatusTag({ status }: { status: RenewalStatus }) {
  const { label } = useCodes()
  return <Tag color={COLOR[status]}>{label('renewalStatus', status)}</Tag>
}

/**
 * 목록에서 바로 갱신 처리상태를 바꾸는 작은 선택상자.
 * "갱신완료"는 [갱신] 등록으로만 처리하도록 선택지에서 뺀다 (새 계약·인증 없이 완료되는 것을 막기 위함).
 */
export function RenewalStatusSelect({ value, onChange, disabled }: {
  value: RenewalStatus
  onChange: (status: RenewalStatus) => void
  disabled?: boolean
}) {
  const { options } = useCodes()
  return (
    <Select<RenewalStatus>
      size="small"
      variant="borderless"
      value={value}
      disabled={disabled}
      style={{ width: 124 }}
      onChange={onChange}
      options={options('renewalStatus').map((o) => ({ ...o, disabled: o.value === 'DONE' }))}
      labelRender={({ value: v }) => <RenewalStatusTag status={v as RenewalStatus} />}
      onClick={(e) => e.stopPropagation()}
    />
  )
}
