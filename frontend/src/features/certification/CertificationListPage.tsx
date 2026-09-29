import { useState } from 'react'
import { App, Button, Checkbox, Flex, Input, Select, Typography } from 'antd'
import { DownloadOutlined, PlusOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { downloadCertificationExcel, searchCertifications } from '../../api/certifications'
import type { CertificationSearch } from '../../api/types'
import { useAuth } from '../../app/AuthContext'
import { SearchField, SearchPanel } from '../../components/SearchPanel'
import { useCodes } from '../../hooks/useCodes'
import { useCertTypeOptions, useOwnerOptions } from '../../hooks/useOptions'
import { usePersistedState } from '../../hooks/usePersistedState'
import { CertificationFormModal } from './CertificationFormModal'
import { CertificationGrid } from './CertificationGrid'

/**
 * 인증 관리 화면 (FR-006).
 * 전체 업체의 인증을 만료일이 가까운 순으로 보여주고, 인증 종류·담당자·처리상태로 좁혀 볼 수 있다.
 */
export function CertificationListPage() {
  const { message } = App.useApp()
  const { canEdit, me } = useAuth()
  const { options } = useCodes()
  const ownerOptions = useOwnerOptions()
  const certTypeOptions = useCertTypeOptions()
  const [draft, setDraft] = usePersistedState<CertificationSearch>('certification.search.draft', {})
  const [search, setSearch] = usePersistedState<CertificationSearch>('certification.search', {})
  const [createOpen, setCreateOpen] = useState(false)
  const [downloading, setDownloading] = useState(false)
  const { data, isFetching } = useQuery({ queryKey: ['certifications', search], queryFn: () => searchCertifications(search) })

  /** 마지막으로 조회한 조건의 목록을 엑셀로 내려받는다. */
  const handleExcel = async () => {
    setDownloading(true)
    try {
      await downloadCertificationExcel(search)
    } catch {
      message.error('엑셀 파일을 만들지 못했습니다.')
    } finally {
      setDownloading(false)
    }
  }

  return (
    <>
      <SearchPanel onSearch={() => setSearch({ ...draft })} onReset={() => { setDraft({}); setSearch({}) }}>
        <SearchField label="검색어">
          <Input allowClear style={{ width: 200 }} placeholder="인증번호·업체명" value={draft.keyword}
            onChange={(e) => setDraft({ ...draft, keyword: e.target.value })} />
        </SearchField>
        <SearchField label="인증 종류">
          <Select allowClear style={{ width: 200 }} placeholder="전체" options={certTypeOptions} value={draft.certType}
            onChange={(certType) => setDraft({ ...draft, certType })} />
        </SearchField>
        <SearchField label="담당자">
          <Select allowClear style={{ width: 120 }} placeholder="전체" options={ownerOptions} value={draft.ownerId}
            onChange={(ownerId) => setDraft({ ...draft, ownerId })} />
        </SearchField>
        <SearchField label="처리상태">
          <Select allowClear style={{ width: 120 }} placeholder="전체" options={options('renewalStatus')}
            value={draft.renewalStatus} onChange={(renewalStatus) => setDraft({ ...draft, renewalStatus })} />
        </SearchField>
        <Checkbox checked={!!draft.includeInactive} onChange={(e) => setDraft({ ...draft, includeInactive: e.target.checked })}>
          이전 이력 포함
        </Checkbox>
        {me && me.role !== 'VIEWER' && (
          <Button type="link" size="small" onClick={() => { const s = { ownerId: me.id }; setDraft(s); setSearch(s) }}>
            내 담당만
          </Button>
        )}
      </SearchPanel>

      <Flex justify="space-between" align="center" style={{ marginBottom: 8 }}>
        <Typography.Text>
          총 <b className="num">{data?.totalElements ?? 0}</b>건
          <Typography.Text type="secondary" style={{ marginInlineStart: 12, fontSize: 12 }}>
            만료일이 가까운 순 · 행을 더블클릭하면 수정할 수 있습니다.
          </Typography.Text>
        </Typography.Text>
        <Flex gap={8}>
          <Button icon={<DownloadOutlined />} onClick={handleExcel} loading={downloading}>엑셀 다운로드</Button>
          {canEdit && <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>인증 등록</Button>}
        </Flex>
      </Flex>

      <CertificationGrid rows={data?.content} loading={isFetching && !data} />
      <CertificationFormModal open={createOpen} mode="create" onClose={() => setCreateOpen(false)} />
    </>
  )
}
