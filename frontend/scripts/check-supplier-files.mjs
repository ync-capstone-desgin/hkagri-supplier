/** 로컬 서버에서 가상 업체로 자료 탭의 다중 업로드·연결·원본 다운로드·삭제 확인·조회자 권한을 검증한다. */
import assert from 'node:assert/strict'
import { mkdirSync, readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright-core'

const base = process.env.BASE_URL ?? 'http://127.0.0.1:5174'
assert(['localhost', '127.0.0.1'].includes(new URL(base).hostname), '로컬 서버에서만 실행하세요.')
const password = process.env.SEED_PASSWORD ?? readFileSync(new URL('../../.env.example', import.meta.url), 'utf8').match(/^SEED_PASSWORD=(.*)$/m)[1].trim()
const out = new URL('../../docs/images/supplier-files/', import.meta.url)
mkdirSync(out, { recursive: true })
const pdf = Buffer.from('%PDF-1.7\n가상 업체 자료 검증\n%%EOF')
const browser = await chromium.launch({ channel: 'chrome', headless: true })
const manager = await browser.newContext({ viewport: { width: 1440, height: 1000 }, locale: 'ko-KR' })
const viewer = await browser.newContext({ viewport: { width: 1440, height: 1000 }, locale: 'ko-KR' })
const page = await manager.newPage()

/** 로그인 세션 쿠키를 유지하며 API로 검증용 자료를 만든다. */
async function post(context, path, data, expected = 201) {
  const response = await context.request.post(`${base}/api${path}`, { data })
  assert.equal(response.status(), expected, `${path}: ${await response.text()}`)
  return response.json()
}

/** 실제 목록의 파일 행을 찾는다(업로드 진행 내역과 구분). */
function row(name, targetPage = page) {
  return targetPage.locator('tr.ant-table-row').filter({ hasText: name })
}

/** 화면이 안정된 뒤 문서용 캡처를 남긴다. */
async function capture(name, targetPage = page) {
  await targetPage.waitForFunction(() => document.querySelectorAll('.ant-message-notice').length === 0)
  await targetPage.screenshot({ path: fileURLToPath(new URL(name, out)), fullPage: true })
}

try {
  await post(manager, '/auth/login', { username: 'manager1', password }, 200)
  await post(viewer, '/auth/login', { username: 'viewer', password }, 200)
  const supplier = await post(manager, '/suppliers', { name: '자료 탭 검증 업체', status: 'ACTIVE', memo: '자동 검증용 가상 데이터' })
  const another = await post(manager, '/suppliers', { name: '자료 격리 검증 업체', status: 'ACTIVE' })
  const contract = (await post(manager, '/contracts', { supplierId: supplier.id, contractNumber: '검증-계약-01',
    title: '농산물 공급 계약 (가상)', startDate: '2026-11-01', endDate: '2027-10-31' })).item
  const cert = (await post(manager, '/certifications', { supplierId: supplier.id, certTypeCode: 'HACCP',
    certificateNumber: '검증-인증-01', issuedDate: '2026-11-01', expiryDate: '2027-10-31' })).item
  await page.goto(`${base}/suppliers/${supplier.id}`)
  await page.getByRole('tab', { name: '자료', exact: true }).click()
  await page.getByText('등록된 자료가 없습니다.', { exact: true }).waitFor()
  await page.locator('input[type=file]').setInputFiles([
    { name: '회사 소개.pdf', mimeType: 'application/pdf', buffer: pdf },
    { name: '거래 안내.pdf', mimeType: 'application/pdf', buffer: pdf },
  ])
  await row('회사 소개.pdf').waitFor()
  await row('거래 안내.pdf').waitFor()
  assert.equal(await page.locator('tr.ant-table-row').count(), 2)

  // 계약을 선택하고 실제 drop 이벤트로 파일을 올린다.
  await page.getByLabel('연결할 계약·인증 (선택)').click()
  await page.getByText('계약 · 검증-계약-01 · 농산물 공급 계약 (가상)', { exact: true }).click()
  const transfer = await page.evaluateHandle((bytes) => {
    const data = new DataTransfer()
    data.items.add(new File([new Uint8Array(bytes)], '공급 계약서.pdf', { type: 'application/pdf' }))
    return data
  }, [...pdf])
  await page.locator('.ant-upload-drag .ant-upload-btn').dispatchEvent('drop', { dataTransfer: transfer })
  await transfer.dispose()
  await row('공급 계약서.pdf').getByText(/검증-계약-01/).waitFor()

  await page.getByLabel('연결할 계약·인증 (선택)').click()
  await page.getByText('인증 · HACCP(식품안전관리인증) · 검증-인증-01', { exact: true }).click()
  await page.locator('input[type=file]').setInputFiles({ name: 'HACCP 인증서.pdf', mimeType: 'application/pdf', buffer: pdf })
  await row('HACCP 인증서.pdf').getByText(/검증-인증-01/).waitFor()
  const list = await (await manager.request.get(`${base}/api/suppliers/${supplier.id}/files`)).json()
  assert.equal(list.totalElements, 4)
  assert.equal(list.content.find((x) => x.file.originalName === '공급 계약서.pdf').contractId, contract.id)
  assert.equal(list.content.find((x) => x.file.originalName === 'HACCP 인증서.pdf').certificationId, cert.id)
  assert.equal((await (await manager.request.get(`${base}/api/suppliers/${another.id}/files`)).json()).totalElements, 0)
  await capture('manager.png')

  const downloadEvent = page.waitForEvent('download')
  await row('회사 소개.pdf').getByRole('button', { name: '내려받기' }).click()
  const download = await downloadEvent
  assert.equal(download.suggestedFilename(), '회사 소개.pdf')
  assert.deepEqual(readFileSync(await download.path()), pdf)

  // 확장자는 맞지만 내용이 잘못된 파일과 정상 파일을 같이 올려 부분 실패를 검증한다.
  await page.locator('input[type=file]').setInputFiles([
    { name: '잘못된 문서.pdf', mimeType: 'application/pdf', buffer: Buffer.from('invalid file') },
    { name: '정상 문서.pdf', mimeType: 'application/pdf', buffer: pdf },
  ])
  await row('정상 문서.pdf').waitFor()
  await page.locator('.ant-upload-list-item-error').waitFor()
  assert.equal(await row('잘못된 문서.pdf').count(), 0)

  await row('거래 안내.pdf').getByRole('button', { name: /삭제/ }).click()
  await page.getByRole('dialog', { name: '자료를 삭제하시겠습니까?', exact: true }).waitFor()
  await capture('delete-confirm.png')
  await page.getByRole('dialog').getByRole('button', { name: '취소', exact: true }).click()
  await row('거래 안내.pdf').waitFor()
  await row('거래 안내.pdf').getByRole('button', { name: /삭제/ }).click()
  await page.getByRole('dialog').getByRole('button', { name: '삭제', exact: true }).click()
  await row('거래 안내.pdf').waitFor({ state: 'detached' })

  const viewerPage = await viewer.newPage()
  await viewerPage.goto(`${base}/suppliers/${supplier.id}`)
  await viewerPage.getByRole('tab', { name: '자료', exact: true }).click()
  await row('회사 소개.pdf', viewerPage).waitFor()
  assert.equal(await viewerPage.locator('input[type=file]').count(), 0)
  assert.equal(await viewerPage.getByRole('button', { name: /삭제/ }).count(), 0)
  const viewerDownload = viewerPage.waitForEvent('download')
  await row('회사 소개.pdf', viewerPage).getByRole('button', { name: '내려받기' }).click()
  assert.deepEqual(readFileSync(await (await viewerDownload).path()), pdf)
  const fileId = list.content[0].file.id
  assert.equal((await viewer.request.delete(`${base}/api/files/${fileId}`)).status(), 403)
  assert.equal((await viewer.request.post(`${base}/api/suppliers/${supplier.id}/files`, {
    multipart: { file: { name: '차단.pdf', mimeType: 'application/pdf', buffer: pdf } },
  })).status(), 403)
  await capture('viewer.png', viewerPage)
  console.log('PASS: 다중 업로드·드롭·계약/인증 연결·부분 실패·원본 다운로드·삭제 확인/취소·업체 격리·조회자 화면/API 권한')
} finally {
  await browser.close()
}
