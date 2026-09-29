import { useState, type ReactNode } from 'react'
import { Button, Card, Checkbox, Descriptions, Flex, Result, Skeleton, Statistic, Tabs, Typography } from 'antd'
import { EditOutlined, PlusOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { useParams } from 'react-router'
import { searchCertifications } from '../../api/certifications'
import { searchContracts } from '../../api/contracts'
import { fetchSupplier } from '../../api/suppliers'
import { useAuth } from '../../app/AuthContext'
import { useTabTitle } from '../../app/TabsContext'
import { formatDateTime } from '../../components/format'
import { SupplierStatusTag } from '../../components/SupplierStatusTag'
import { CertificationFormModal } from '../certification/CertificationFormModal'
import { CertificationGrid } from '../certification/CertificationGrid'
import { ContractFormModal } from '../contract/ContractFormModal'
import { ContractGrid } from '../contract/ContractGrid'
import { SupplierFormModal } from './SupplierFormModal'

/** 탭 안 목록 위의 도구 줄: 왼쪽에 "이전 이력 포함", 오른쪽에 등록 버튼 */
function TabToolbar({ includeInactive, onToggle, addLabel, onAdd }: {
  includeInactive: boolean
  onToggle: (value: boolean) => void
  addLabel?: string
  onAdd?: () => void
}): ReactNode {
  return (
    <Flex justify="space-between" align="center" style={{ marginBottom: 8 }}>
      <Checkbox checked={includeInactive} onChange={(e) => onToggle(e.target.checked)}>이전 이력 포함</Checkbox>
      {onAdd && <Button type="primary" icon={<PlusOutlined />} onClick={onAdd}>{addLabel}</Button>}
    </Flex>
  )
}

/**
 * 협력업체 상세 화면 (FR-003, FR-004).
 * 상단에 업체 요약(계약·인증·만료 건수)과 [수정] 버튼, 아래 탭에 기본정보·계약·인증을 둔다.
 * 계약·인증 탭에서는 이 업체의 자료만 보이며(VT-007), 업체가 미리 선택된 상태로 등록할 수 있다.
 */
export function SupplierDetailPage() {
  const id = Number(useParams().id)
  const { canEdit } = useAuth()
  const [editOpen, setEditOpen] = useState(false)
  const [contractOpen, setContractOpen] = useState(false)
  const [certOpen, setCertOpen] = useState(false)
  const [contractHistory, setContractHistory] = useState(false)
  const [certHistory, setCertHistory] = useState(false)

  const { data, isLoading, isError } = useQuery({ queryKey: ['supplier', id], queryFn: () => fetchSupplier(id) })
  const contracts = useQuery({
    queryKey: ['contracts', { supplierId: id, includeInactive: contractHistory }],
    queryFn: () => searchContracts({ supplierId: id, includeInactive: contractHistory }),
  })
  const certs = useQuery({
    queryKey: ['certifications', { supplierId: id, includeInactive: certHistory }],
    queryFn: () => searchCertifications({ supplierId: id, includeInactive: certHistory }),
  })
  useTabTitle(data?.name)

  if (isLoading) {
    return <Card><Skeleton active /></Card>
  }
  if (isError || !data) {
    return <Result status="404" title="협력업체를 찾을 수 없습니다." subTitle="삭제되었거나 주소가 잘못되었습니다." />
  }

  return (
    <>
      <Card style={{ marginBottom: 12 }}>
        <Flex justify="space-between" align="center" wrap gap={16}>
          <div>
            <Flex align="center" gap={8}>
              <Typography.Title level={4} style={{ margin: 0 }}>{data.name}</Typography.Title>
              <SupplierStatusTag status={data.status} />
            </Flex>
            <Typography.Text type="secondary" className="num">
              {data.code} · 내부 담당자 {data.ownerName ?? '미지정'}
            </Typography.Text>
          </div>
          <Flex gap={32} align="center">
            <Statistic title="유효 계약" value={data.contractCount} suffix="건" />
            <Statistic title="유효 인증" value={data.certCount} suffix="건" />
            <Statistic title="만료·임박(90일)" value={data.expiringCount} suffix="건"
              styles={{ content: { color: data.expiringCount > 0 ? '#cf1322' : undefined } }} />
            {canEdit && <Button icon={<EditOutlined />} onClick={() => setEditOpen(true)}>기본정보 수정</Button>}
          </Flex>
        </Flex>
      </Card>

      <Card>
        <Tabs
          items={[
            {
              key: 'info',
              label: '기본정보',
              children: (
                <>
                  <Descriptions bordered size="small" column={{ xs: 1, md: 2 }}>
                    <Descriptions.Item label="업체명">{data.name}</Descriptions.Item>
                    <Descriptions.Item label="업체코드"><span className="num">{data.code}</span></Descriptions.Item>
                    <Descriptions.Item label="사업자번호"><span className="num">{data.businessNumber ?? '-'}</span></Descriptions.Item>
                    <Descriptions.Item label="거래상태"><SupplierStatusTag status={data.status} /></Descriptions.Item>
                    <Descriptions.Item label="업체 담당자">{data.contactName ?? '-'}</Descriptions.Item>
                    <Descriptions.Item label="연락처"><span className="num">{data.contactPhone ?? '-'}</span></Descriptions.Item>
                    <Descriptions.Item label="이메일">{data.contactEmail ?? '-'}</Descriptions.Item>
                    <Descriptions.Item label="내부 담당자">{data.ownerName ?? '미지정'}</Descriptions.Item>
                    <Descriptions.Item label="비고" span="filled">
                      <span style={{ whiteSpace: 'pre-wrap' }}>{data.memo ?? '-'}</span>
                    </Descriptions.Item>
                  </Descriptions>
                  <Typography.Paragraph type="secondary" style={{ marginTop: 12, marginBottom: 0, fontSize: 12 }}>
                    등록 {data.createdBy} · {formatDateTime(data.createdAt)} &nbsp;|&nbsp; 최종 수정 {data.updatedBy} ·{' '}
                    {formatDateTime(data.updatedAt)}
                  </Typography.Paragraph>
                </>
              ),
            },
            {
              key: 'contracts',
              label: `계약 ${data.contractCount}`,
              children: (
                <>
                  <TabToolbar includeInactive={contractHistory} onToggle={setContractHistory}
                    addLabel="계약 등록" onAdd={canEdit ? () => setContractOpen(true) : undefined} />
                  <ContractGrid rows={contracts.data?.content} loading={contracts.isLoading} showSupplier={false} height={380} />
                </>
              ),
            },
            {
              key: 'certifications',
              label: `인증 ${data.certCount}`,
              children: (
                <>
                  <TabToolbar includeInactive={certHistory} onToggle={setCertHistory}
                    addLabel="인증 등록" onAdd={canEdit ? () => setCertOpen(true) : undefined} />
                  <CertificationGrid rows={certs.data?.content} loading={certs.isLoading} showSupplier={false} height={380} />
                </>
              ),
            },
          ]}
        />
      </Card>

      <SupplierFormModal open={editOpen} supplier={data} onClose={() => setEditOpen(false)} />
      <ContractFormModal open={contractOpen} mode="create" supplierId={id} onClose={() => setContractOpen(false)} />
      <CertificationFormModal open={certOpen} mode="create" supplierId={id} onClose={() => setCertOpen(false)} />
    </>
  )
}
