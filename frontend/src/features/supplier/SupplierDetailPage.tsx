import { useState, type ReactNode } from 'react'
import { App, Button, Card, Checkbox, Descriptions, Flex, Grid, Result, Skeleton, Statistic, Tabs, Tag, Typography } from 'antd'
import { DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate, useParams, useSearchParams } from 'react-router'
import { searchCertifications } from '../../api/certifications'
import { searchContracts } from '../../api/contracts'
import { toProblem } from '../../api/client'
import { fetchSupplierFiles } from '../../api/files'
import { deleteSupplier, fetchSupplier } from '../../api/suppliers'
import { useAuth } from '../../app/AuthContext'
import { ChipTabs } from '../../components/ChipTabs'
import { useTabs, useTabTitle } from '../../app/TabsContext'
import { formatDateTime } from '../../components/format'
import { SupplierStatusTag } from '../../components/SupplierStatusTag'
import { CertificationFormModal } from '../certification/CertificationFormModal'
import { CertificationGrid } from '../certification/CertificationGrid'
import { ContractFormModal } from '../contract/ContractFormModal'
import { ContractGrid } from '../contract/ContractGrid'
import { SupplierFormModal } from './SupplierFormModal'
import { SupplierContactsTab } from './SupplierContactsTab'
import { SupplierFilesTab } from './SupplierFilesTab'
import { SupplierNotesTab } from './SupplierNotesTab'
import { SupplierChangesTab } from '../history/SupplierChangesTab'

/** 탭 안 목록 위의 도구 줄: 왼쪽에 "이전 이력 포함", 오른쪽에 등록 버튼 */
function TabToolbar({ includeInactive, onToggle, addLabel, onAdd }: {
  includeInactive: boolean
  onToggle: (value: boolean) => void
  addLabel?: string
  onAdd?: () => void
}): ReactNode {
  return (
    <Flex justify="space-between" align="center" wrap gap={8} style={{ marginBottom: 8 }}>
      <Checkbox checked={includeInactive} onChange={(e) => onToggle(e.target.checked)}>이전 이력 포함</Checkbox>
      {onAdd && <Button type="primary" icon={<PlusOutlined />} onClick={onAdd}>{addLabel}</Button>}
    </Flex>
  )
}

/**
 * 협력업체 상세 화면 (FR-003, FR-004).
 * 상단에 업체 요약과 [수정] 버튼, 아래 탭에 기본정보·계약·인증·담당자·메모·자료·변경 이력을 둔다.
 * 계약·인증 탭에서는 이 업체의 자료만 보이며(VT-007), 업체가 미리 선택된 상태로 등록할 수 있다.
 */
export function SupplierDetailPage() {
  const id = Number(useParams().id)
  const { canEdit } = useAuth()
  const { message, modal } = App.useApp()
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const { closeTab } = useTabs()
  // 알림함 등에서 ?tab=contracts 처럼 특정 탭을 바로 열 수 있게 주소에 둔다.
  const [searchParams, setSearchParams] = useSearchParams()
  const mobile = Grid.useBreakpoint().md === false
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

  /**
   * 업체 삭제를 확인받는다. 함께 삭제되는 자료 수를 미리 보여 주고, 계약·인증이 남아 있으면 서버 안내 문구를 그대로 보여 준다.
   * 삭제되면 이 업체의 탭을 닫고 협력업체 목록으로 돌아간다.
   */
  const confirmDelete = async () => {
    const fileCount = (await fetchSupplierFiles(id, 0)).totalElements
    modal.confirm({
      title: '이 협력업체를 삭제하시겠습니까?',
      content: fileCount > 0 ? `업체에 올린 자료 ${fileCount}개도 함께 삭제됩니다.` : '삭제하면 목록에서 사라집니다.',
      okText: '삭제', okButtonProps: { danger: true }, cancelText: '취소',
      onOk: async () => {
        try {
          await deleteSupplier(id)
          await Promise.all(['suppliers', 'dashboard'].map((k) => queryClient.invalidateQueries({ queryKey: [k] })))
          message.success('협력업체를 삭제했습니다.')
          closeTab(`/suppliers/${id}`)
          navigate('/suppliers')
        } catch (e) {
          message.error(toProblem(e).detail)
        }
      },
    })
  }

  if (isLoading) {
    return <Card><Skeleton active /></Card>
  }
  if (isError || !data) {
    return <Result status="404" title="협력업체를 찾을 수 없습니다." subTitle="삭제되었거나 주소가 잘못되었습니다." />
  }

  /** 탭 목록. 휴대폰용 탭 단추(여러 줄)와 PC용 탭 줄이 함께 쓴다. */
  const tabItems = [
      {
        key: 'info',
        label: '기본정보',
        children: (
          <>
            <Descriptions bordered size="small" column={{ xs: 1, md: 2 }} styles={{ label: { whiteSpace: 'nowrap' } }}>
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
      { key: 'contacts', label: '담당자', children: <SupplierContactsTab key={id} supplierId={id} /> },
      { key: 'notes', label: '메모', children: <SupplierNotesTab key={id} supplierId={id} /> },
      { key: 'files', label: '자료', children: <SupplierFilesTab key={id} supplierId={id} /> },
      { key: 'changes', label: '변경 이력', children: <SupplierChangesTab key={id} supplierId={id} /> },
  ]

  return (
    <>
      <Card style={{ marginBottom: 12 }}>
        <Flex justify="space-between" align="center" wrap gap={16}>
          <div>
            <Flex align="center" gap={8} wrap>
              <Typography.Title level={4} style={{ margin: 0 }}>{data.name}</Typography.Title>
              <SupplierStatusTag status={data.status} />
              {data.expiredRequiredCertCount > 0 && <Tag color="red">인증 만료 {data.expiredRequiredCertCount}건</Tag>}
            </Flex>
            <Typography.Text type="secondary" className="num">
              {data.code} · 내부 담당자 {data.ownerName ?? '미지정'}
            </Typography.Text>
          </div>
          <Flex gap="12px 32px" align="center" wrap>
            <Flex gap="12px 32px" align="center" wrap>
              <Statistic title="유효 계약" value={data.contractCount} suffix="건" />
              <Statistic title="유효 인증" value={data.certCount} suffix="건" />
              <Statistic title="만료·임박(90일)" value={data.expiringCount} suffix="건"
                styles={{ content: { color: data.expiringCount > 0 ? '#cf1322' : undefined } }} />
              {data.missingDocCount > 0 && (
                <Statistic title="필수 서류 누락" value={data.missingDocCount} suffix="건" styles={{ content: { color: '#cf1322' } }} />
              )}
            </Flex>
            {/* 버튼 둘은 한 묶음으로 둔다. 숫자와 섞어 두면 스크롤 막대가 생기는 탭에서만 삭제 버튼이 다음 줄로 밀려 높이가 달라진다. */}
            {canEdit && (
              <Flex gap={8}>
                <Button icon={<EditOutlined />} onClick={() => setEditOpen(true)}>기본정보 수정</Button>
                <Button danger icon={<DeleteOutlined />} onClick={confirmDelete}>삭제</Button>
              </Flex>
            )}
          </Flex>
        </Flex>
      </Card>

      <Card>
        <Tabs
          activeKey={searchParams.get('tab') ?? 'info'}
          onChange={(key) => setSearchParams({ tab: key }, { replace: true })}
          // 휴대폰에서는 탭 7개가 한 줄에 들어가지 않으므로, 옆으로 밀지 않아도 모두 보이게 여러 줄로 쌓는다.
          renderTabBar={mobile ? (bar) => (
            <div style={{ marginBottom: 12 }}>
              <ChipTabs value={bar.activeKey} onChange={(key) => setSearchParams({ tab: key }, { replace: true })}
                options={tabItems.map((item) => ({ value: item.key, label: item.label }))} />
            </div>
          ) : undefined}
          items={tabItems}
        />
      </Card>

      <SupplierFormModal open={editOpen} supplier={data} onClose={() => setEditOpen(false)} />
      <ContractFormModal open={contractOpen} mode="create" supplierId={id} onClose={() => setContractOpen(false)} />
      <CertificationFormModal open={certOpen} mode="create" supplierId={id} onClose={() => setCertOpen(false)} />
    </>
  )
}
