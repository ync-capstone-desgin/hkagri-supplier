/** 가상 응답으로 자료실 조회 오류·페이지 전환·파일 사전 검증을 확인한다. DB는 변경하지 않는다. */
import assert from 'node:assert/strict'
import { chromium } from 'playwright-core'

const base = process.env.BASE_URL ?? 'http://127.0.0.1:5174'
assert(['localhost', '127.0.0.1'].includes(new URL(base).hostname), '로컬 서버에서만 실행하세요.')
const browser = await chromium.launch({ channel: 'chrome', headless: true })
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 }, locale: 'ko-KR' })
page.setDefaultTimeout(10000)
const folder = { id: 1, parentId: null, name: '양식 자료실' }
const post = { id: 1, title: '검증 양식', description: '가상 설명', category: 'OTHER', folderId: 1,
  createdAt: '2026-11-06T10:00:00', updatedAt: '2026-11-06T10:00:00', createdBy: 'test', updatedBy: 'test',
  files: [{ id: 1, originalName: '검증.pdf', size: 32 }] }
let folderFailed = true
let listFailed = true
let detailFailed = true
let saves = 0
let downloads = 0
let lastList
page.on('download', () => downloads++)

/** 상태와 안내 문구를 포함한 실패 응답을 보낸다. */
const failure = (route, detail, status = 503) => route.fulfill({ status, json: { status, detail } })

try {
  await page.route('**/api/**', async (route) => {
    const url = new URL(route.request().url())
    const path = url.pathname.slice(5)
    if (!url.pathname.startsWith('/api/')) return route.continue()
    if (route.request().method() !== 'GET') { saves++; return failure(route, '예상하지 않은 저장 요청') }
    let data = { content: [], page: 0, size: 20, totalElements: 0 }
    if (path === 'auth/me') data = { id: 1, username: 'test', name: '검증', role: 'MANAGER' }
    else if (path === 'codes') data = {}
    else if (path === 'notifications/unread-count') data = { count: 0 }
    else if (path.startsWith('form-folders/')) {
      if (path !== 'form-folders/1') return failure(route, '폴더를 찾을 수 없습니다.', 404)
      if (folderFailed) return failure(route, '폴더 조회 실패')
      data = { folder, path: [folder], children: [] }
    } else if (path === 'forms') {
      lastList = url.searchParams
      if (listFailed) return failure(route, '양식 목록 조회 실패')
      const current = Number(url.searchParams.get('page'))
      data = { page: current, size: 20, totalElements: 21,
        content: current === 0 ? Array.from({ length: 20 }, (_, i) => ({ ...post, id: i + 1, title: i === 0 ? post.title : `가상 양식 ${i + 1}` }))
          : [{ ...post, id: 21, title: '마지막 양식' }] }
    } else if (path === 'forms/1') {
      if (detailFailed) return failure(route, '양식 상세 조회 실패')
      data = post
    } else if (path === 'files/1/download') return failure(route, '파일을 찾을 수 없습니다.', 404)
    await route.fulfill({ json: data })
  })

  await page.goto(`${base}/forms`)
  await page.getByText('폴더 조회 실패', { exact: true }).waitFor()
  folderFailed = false
  await page.getByRole('button', { name: /새로고침$/ }).click()
  await page.getByText('양식 목록 조회 실패', { exact: true }).waitFor()
  listFailed = false
  await page.getByRole('button', { name: '다시 시도', exact: true }).click()
  await page.getByRole('button', { name: '양식 열기: 검증 양식', exact: true }).waitFor()

  await page.locator('.ant-pagination-item-2').click()
  await page.getByRole('button', { name: '양식 열기: 마지막 양식', exact: true }).waitFor()
  assert.equal(lastList.get('page'), '1')
  await page.getByRole('searchbox', { name: '양식 이름 검색', exact: true }).fill('검증')
  await page.getByRole('searchbox', { name: '양식 이름 검색', exact: true }).press('Enter')
  await page.getByRole('button', { name: '양식 열기: 검증 양식', exact: true }).waitFor()
  assert.equal(lastList.get('page'), '0')
  assert.equal(lastList.get('keyword'), '검증')
  await page.locator('.ant-pagination-item-2').click()
  await page.getByRole('button', { name: '양식 열기: 마지막 양식', exact: true }).waitFor()
  await page.getByRole('tab', { name: '계약 양식', exact: true }).click()
  await page.getByRole('button', { name: '양식 열기: 검증 양식', exact: true }).waitFor()
  assert.equal(lastList.get('page'), '0')
  assert.equal(lastList.get('category'), 'CONTRACT')

  await page.getByRole('button', { name: '양식 열기: 검증 양식', exact: true }).click()
  const detail = page.getByRole('dialog', { name: '양식 상세', exact: true })
  await detail.getByText('양식 상세 조회 실패', { exact: true }).waitFor()
  detailFailed = false
  await detail.getByRole('button', { name: '다시 시도', exact: true }).click()
  await detail.getByRole('button', { name: '내려받기: 검증.pdf', exact: true }).click()
  await page.getByText('파일을 찾을 수 없습니다.', { exact: true }).waitFor()
  assert.equal(downloads, 0)
  await detail.getByRole('button', { name: '닫기', exact: true }).last().click()

  const cases = [
    { files: [{ name: '금지.exe', mimeType: 'application/octet-stream', buffer: Buffer.from('test') }], text: '금지.exe: PDF·이미지·한글·오피스 문서만 올릴 수 있습니다.' },
    { files: [{ name: '빈 파일.pdf', mimeType: 'application/pdf', buffer: Buffer.alloc(0) }], text: '빈 파일.pdf: 빈 파일은 올릴 수 없습니다.' },
    { files: [{ name: '초과.pdf', mimeType: 'application/pdf', buffer: Buffer.alloc(21 * 1024 * 1024) }], text: '초과.pdf: 파일 하나는 20MB 이하여야 합니다.' },
    { files: ['첫째.pdf', '둘째.pdf'].map((name) => ({ name, mimeType: 'application/pdf', buffer: Buffer.alloc(11 * 1024 * 1024) })), text: '한 번에 새로 올리는 파일의 합계는 20MB 이하여야 합니다.' },
  ]
  for (const item of cases) {
    await page.getByRole('button', { name: /양식 등록$/ }).click()
    const editor = page.getByRole('dialog', { name: '양식 등록', exact: true })
    await editor.getByLabel('양식 이름', { exact: true }).fill('제한 검증')
    await editor.locator('input[type=file]').setInputFiles(item.files)
    await editor.getByText(item.text, { exact: true }).waitFor()
    assert(await editor.getByRole('button', { name: '저장', exact: true }).isDisabled())
    await editor.getByRole('button', { name: '취소', exact: true }).click()
  }
  assert.equal(saves, 0)
  await page.goto(`${base}/forms/folders/9999`)
  await page.getByText('폴더를 찾을 수 없습니다.', { exact: true }).waitFor()
  assert.equal(await page.getByRole('button', { name: /새 폴더$/ }).count(), 0)
  await page.getByRole('link', { name: '최상위로 이동', exact: true }).click()
  await page.getByRole('button', { name: '양식 열기: 검증 양식', exact: true }).waitFor()
  console.log('PASS: 폴더·목록·상세 실패 후 재시도, 페이지/필터 초기화, 다운로드 404, 형식·빈 파일·개별/합산 용량 차단, 삭제된 폴더 진입')
} catch (error) {
  console.error('실패 화면:', page.url(), await page.locator('[role="alert"]').allTextContents())
  await page.screenshot({ path: '/tmp/hkagri-form-errors-failure.png', fullPage: true })
  throw error
} finally {
  await browser.close()
}
