import { useMemo, type ReactNode } from 'react'
import { Card, Col, Flex, Progress, Row, Skeleton, Tag, Typography } from 'antd'
import { AuditOutlined, FileTextOutlined, RightOutlined, ShopOutlined, UserOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import type { ColDef } from 'ag-grid-community'
import { useNavigate } from 'react-router'
import { fetchDashboard } from '../../api/dashboard'
import type { ExpiryItem, ExpiryPeriod } from '../../api/types'
import { useAuth } from '../../app/AuthContext'
import { BRAND } from '../../app/theme'
import { DataGrid } from '../../components/DataGrid'
import { DdayBadge, URGENCY_COLOR } from '../../components/DdayBadge'
import { RenewalStatusTag } from '../../components/RenewalStatusTag'
import { useCodes } from '../../hooks/useCodes'

/** 클릭하면 관련 화면으로 이동하는 숫자 카드 */
function KpiCard({ title, value, sub, icon, onClick }: {
  title: string
  value: number
  sub?: ReactNode
  icon: ReactNode
  onClick: () => void
}) {
  return (
    <Card hoverable size="small" onClick={onClick} styles={{ body: { padding: 16 } }}>
      <Flex justify="space-between" align="flex-start">
        <div>
          <Typography.Text type="secondary">{title}</Typography.Text>
          <div className="num" style={{ fontSize: 28, fontWeight: 700, lineHeight: 1.3 }}>{value.toLocaleString()}</div>
          {sub && <Typography.Text type="secondary" style={{ fontSize: 12 }}>{sub}</Typography.Text>}
        </div>
        <span style={{ fontSize: 22, color: BRAND, opacity: 0.8 }}>{icon}</span>
      </Flex>
    </Card>
  )
}

/** 만료 현황의 구간 칸 하나: 색 막대 + 숫자. 클릭하면 해당 구간의 만료 예정 목록으로 간다. */
function BucketTile({ label, value, color, hint, onClick }: {
  label: string
  value: number
  color: string
  hint: string
  onClick: () => void
}) {
  return (
    <div onClick={onClick} role="button" tabIndex={0} onKeyDown={(e) => e.key === 'Enter' && onClick()}
      style={{ cursor: 'pointer', borderLeft: `4px solid ${color}`, padding: '8px 14px', background: '#fafafa', borderRadius: 6 }}>
      <Typography.Text type="secondary" style={{ fontSize: 13 }}>{label}</Typography.Text>
      <div className="num" style={{ fontSize: 26, fontWeight: 700, color: value > 0 ? color : '#bfbfbf' }}>{value}</div>
      <Typography.Text type="secondary" style={{ fontSize: 11 }}>{hint}</Typography.Text>
    </div>
  )
}

/**
 * 대시보드 (FR-009).
 * 로그인하면 가장 먼저 보는 화면으로, "오늘 무엇을 처리해야 하는지"를 위에서 아래로 보여준다.
 * 1) 인사말과 내 담당 조치 건수 → 2) 전체 현황 카드 → 3) 만료 구간별 건수 → 4) 가장 급한 10건
 * 모든 숫자는 클릭하면 해당 목록으로 이동한다.
 */
export function DashboardPage() {
  const navigate = useNavigate()
  const { me } = useAuth()
  const { label } = useCodes()
  const { data } = useQuery({ queryKey: ['dashboard'], queryFn: fetchDashboard })

  /** 만료 예정 목록을 지정한 구간으로 연다. mine이면 내 담당만 본다. */
  const openExpiries = (period: ExpiryPeriod, mine = false) => navigate(`/expiries?period=${period}${mine ? '&mine=1' : ''}`)

  /** 급한 항목 표의 컬럼 정의 */
  const columns = useMemo<ColDef<ExpiryItem>[]>(() => [
    { field: 'daysLeft', headerName: 'D-day', width: 140, cellRenderer: (p: { data: ExpiryItem }) => <DdayBadge daysLeft={p.data.daysLeft} status={p.data.expiryStatus} /> },
    { field: 'targetType', headerName: '유형', width: 80, cellRenderer: (p: { data: ExpiryItem }) => <Tag color={p.data.targetType === 'CONTRACT' ? 'blue' : 'cyan'}>{label('targetType', p.data.targetType)}</Tag> },
    { field: 'supplierName', headerName: '업체명', width: 150, cellRenderer: (p: { data: ExpiryItem }) => <Typography.Link onClick={() => navigate(`/suppliers/${p.data.supplierId}`)}>{p.data.supplierName}</Typography.Link> },
    { field: 'name', headerName: '대상명', flex: 1, minWidth: 160 },
    { field: 'expiryDate', headerName: '만료일', width: 115, cellClass: 'num' },
    { field: 'ownerName', headerName: '담당자', width: 100 },
    { field: 'renewalStatus', headerName: '갱신 처리', width: 110, cellRenderer: (p: { data: ExpiryItem }) => <RenewalStatusTag status={p.data.renewalStatus} /> },
  ], [label, navigate])

  if (!data) {
    return <Card><Skeleton active paragraph={{ rows: 8 }} /></Card>
  }
  const actionable = data.expired + data.within90
  const progressPct = actionable === 0 ? 100 : Math.round((data.inProgress / actionable) * 100)

  return (
    <Flex vertical gap={12}>
      <Card size="small" styles={{ body: { padding: '14px 18px' } }}>
        <Flex justify="space-between" align="center" wrap gap={8}>
          <Typography.Text style={{ fontSize: 15 }}>
            안녕하세요, <b>{me?.name}</b>님. {data.today} 기준{' '}
            {data.mine > 0
              ? <>내 담당 중 조치가 필요한 항목이 <Typography.Link strong onClick={() => openExpiries('D90', true)}>{data.mine}건</Typography.Link> 있습니다.</>
              : <>내 담당 중 조치가 필요한 항목은 없습니다.</>}
          </Typography.Text>
          <Typography.Link onClick={() => openExpiries('D30')}>만료 예정 목록 보기 <RightOutlined /></Typography.Link>
        </Flex>
      </Card>

      <Row gutter={[12, 12]}>
        <Col xs={12} lg={6}>
          <KpiCard title="협력업체" value={data.supplierTotal} sub={`거래중 ${data.supplierActive}곳`} icon={<ShopOutlined />} onClick={() => navigate('/suppliers')} />
        </Col>
        <Col xs={12} lg={6}>
          <KpiCard title="유효 계약" value={data.activeContracts} icon={<FileTextOutlined />} onClick={() => navigate('/contracts')} />
        </Col>
        <Col xs={12} lg={6}>
          <KpiCard title="유효 인증" value={data.activeCertifications} icon={<AuditOutlined />} onClick={() => navigate('/certifications')} />
        </Col>
        <Col xs={12} lg={6}>
          <KpiCard title="내 담당 조치 대상" value={data.mine} sub="만료 + 90일 이내" icon={<UserOutlined />} onClick={() => openExpiries('D90', true)} />
        </Col>
      </Row>

      <Card size="small" title="만료 현황" extra={<Typography.Text type="secondary" style={{ fontSize: 12 }}>숫자를 누르면 해당 목록이 열립니다</Typography.Text>}>
        <Row gutter={[12, 12]}>
          <Col xs={12} md={8} xl={4}><BucketTile label="만료됨" value={data.expired} color={URGENCY_COLOR.expired} hint="즉시 확인" onClick={() => openExpiries('EXPIRED')} /></Col>
          <Col xs={12} md={8} xl={5}><BucketTile label="7일 이내" value={data.within7} color={URGENCY_COLOR.d7} hint="오늘 포함" onClick={() => openExpiries('D7')} /></Col>
          <Col xs={12} md={8} xl={5}><BucketTile label="30일 이내" value={data.within30} color={URGENCY_COLOR.d30} hint="7일 이내 포함" onClick={() => openExpiries('D30')} /></Col>
          <Col xs={12} md={8} xl={5}><BucketTile label="90일 이내" value={data.within90} color={URGENCY_COLOR.d90} hint="30일 이내 포함" onClick={() => openExpiries('D90')} /></Col>
          <Col xs={12} md={8} xl={5}><BucketTile label="확인 필요" value={data.unknown} color={URGENCY_COLOR.unknown} hint="만료일 미등록 인증" onClick={() => openExpiries('UNKNOWN')} /></Col>
        </Row>
        <Flex align="center" gap={12} style={{ marginTop: 14 }}>
          <Typography.Text type="secondary" style={{ whiteSpace: 'nowrap', fontSize: 13 }}>
            조치 대상 {actionable}건 중 갱신진행 <b>{data.inProgress}</b> · 미착수 <b>{data.notStarted}</b>
          </Typography.Text>
          <Progress percent={progressPct} showInfo={false} strokeColor={BRAND} style={{ flex: 1, margin: 0 }} />
        </Flex>
      </Card>

      <Card size="small" title="가장 급한 항목" extra={<Typography.Link onClick={() => openExpiries('D30')}>전체 보기 <RightOutlined /></Typography.Link>}>
        <DataGrid rows={data.urgent} columns={columns} rowId={(r) => `${r.targetType}-${r.targetId}`} paginate={false} height={400} />
      </Card>
    </Flex>
  )
}
