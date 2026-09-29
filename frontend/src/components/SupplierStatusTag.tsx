import { Tag } from 'antd'
import type { SupplierStatus } from '../api/types'
import { useCodes } from '../hooks/useCodes'

/** 거래상태별 태그 색. 색만으로 구분하지 않도록 항상 글자와 함께 보여준다. */
const COLOR: Record<SupplierStatus, string> = {
  ACTIVE: 'green',
  SUSPENDED: 'orange',
  TERMINATED: 'default',
}

/** 협력업체 거래상태를 색상 태그로 보여준다. */
export function SupplierStatusTag({ status }: { status: SupplierStatus }) {
  const { label } = useCodes()
  return <Tag color={COLOR[status]}>{label('supplierStatus', status)}</Tag>
}
