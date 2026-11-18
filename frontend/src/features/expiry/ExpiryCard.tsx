import { Button, Flex, Tag, Typography } from 'antd'
import { SyncOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router'
import type { ExpiryItem, RenewalStatus } from '../../api/types'
import { DdayBadge } from '../../components/DdayBadge'
import { RenewalStatusSelect, RenewalStatusTag } from '../../components/RenewalStatusTag'
import { useCodes } from '../../hooks/useCodes'

/**
 * 만료 항목(계약·인증) 한 건을 카드 내용으로 보여준다.
 * 만료 캘린더의 날짜별 목록과, 휴대폰의 만료 예정 목록이 함께 쓴다.
 * 첫 줄에 D-day·유형·업체명, 둘째 줄에 대상명·번호, 셋째 줄에 담당자와 갱신 처리를 둔다.
 */
export function ExpiryCard({ item, canEdit, showDate, onChangeStatus, onRenew }: {
  item: ExpiryItem
  /** 처리상태 변경·갱신 등록을 할 수 있는 사용자인지 */
  canEdit: boolean
  /** 만료일을 함께 보여줄지 (캘린더는 날짜가 제목에 있어 생략) */
  showDate?: boolean
  /** canEdit일 때만 쓴다. 조회만 하는 곳(대시보드)에서는 생략한다. */
  onChangeStatus?: (item: ExpiryItem, status: RenewalStatus) => void
  onRenew?: (item: ExpiryItem) => void
}) {
  const navigate = useNavigate()
  const { label } = useCodes()
  return (
    <>
      <Flex align="center" gap={6} wrap>
        <DdayBadge daysLeft={item.daysLeft} status={item.expiryStatus} />
        <Tag color={item.targetType === 'CONTRACT' ? 'blue' : 'cyan'}>{label('targetType', item.targetType)}</Tag>
        <Typography.Link strong onClick={() => navigate(`/suppliers/${item.supplierId}`)}>{item.supplierName}</Typography.Link>
      </Flex>
      <div style={{ margin: '6px 0' }}>
        {item.name}
        <Typography.Text type="secondary" className="num" style={{ marginInlineStart: 6, fontSize: 12 }}>{item.number}</Typography.Text>
      </div>
      <Flex align="center" justify="space-between" gap={8} wrap>
        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
          {showDate && <span className="num">만료일 {item.expiryDate ?? '없음'} · </span>}
          담당 {item.ownerName ?? '미지정'}
        </Typography.Text>
        {canEdit ? (
          <Flex align="center" gap={4}>
            <RenewalStatusSelect value={item.renewalStatus} onChange={(s) => onChangeStatus?.(item, s)} />
            <Button size="small" type="primary" ghost icon={<SyncOutlined />} onClick={() => onRenew?.(item)}>갱신 등록</Button>
          </Flex>
        ) : (
          <RenewalStatusTag status={item.renewalStatus} />
        )}
      </Flex>
    </>
  )
}
