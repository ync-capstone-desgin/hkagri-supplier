/** 로컬 실제 API로 폴더형 자료실의 계층·편집·다중 첨부·권한·오류 복구를 검증한다. 실자료는 사용하지 않는다. */
import assert from 'node:assert/strict'
import { mkdirSync, readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright-core'

const base = process.env.BASE_URL ?? 'http://127.0.0.1:5174'
assert(['localhost', '127.0.0.1'].includes(new URL(base).hostname), '로컬 서버에서만 실행하세요.')
const password = process.env.SEED_PASSWORD ?? readFileSync(new URL('../../.env.example', import.meta.url), 'utf8').match(/^SEED_PASSWORD=(.*)$/m)[1].trim()
const out = new URL('../../docs/images/forms/', import.meta.url)
mkdirSync(out, { recursive: true })
const pdf = Buffer.from('%PDF-1.7\n가상 양식 검증\n%%EOF')
const suffix = Date.now()
const browser = await chromium.launch({ channel: 'chrome', headless: true })
const manager = await browser.newContext({ viewport: { width: 1440, height: 1000 }, locale: 'ko-KR' })
const viewer = await browser.newContext({ viewport: { width: 1440, height: 1000 }, locale: 'ko-KR' })
const page = await manager.newPage()
page.setDefaultTimeout(8000)
const folderIds = []
const postIds = []

/** 세션을 만든다. 테스트 실패 시 본문 대신 상태만 출력한다. */
async function login(context, username) {
  const response = await context.request.post(`${base}/api/auth/login`, { data: { username, password } })
  assert.equal(response.status(), 200)
}

/** 특정 메서드와 API 경로의 응답을 기다린다. */
function responseFor(path, method) {
  const pending = page.waitForResponse((response) => new URL(response.url()).pathname === `/api${path}` && response.request().method() === method)
  // 클릭 자체가 실패해도 대기 중인 응답 Promise가 프로세스를 먼저 종료하지 않게 한다.
  void pending.catch(() => {})
  return pending
}

/** 화면에서 현재 폴더 아래 새 폴더를 생성한다. */
async function createFolder(name) {
  await page.getByRole('button', { name: /새 폴더$/ }).click()
  const dialog = page.getByRole('dialog', { name: '새 폴더', exact: true })
  await dialog.getByLabel('폴더 이름', { exact: true }).fill(name)
  const pending = responseFor('/form-folders', 'POST')
  await dialog.getByRole('button', { name: '저장', exact: true }).click()
  const response = await pending
  assert.equal(response.status(), 201)
  const data = await response.json()
  folderIds.push(data.id)
  await dialog.waitFor({ state: 'hidden' })
  return data.id
}

/** 임시 알림이 사라진 화면을 저장한다. */
async function capture(name, target = page) {
  await target.waitForFunction(() => document.querySelectorAll('.ant-message-notice').length === 0)
  await target.screenshot({ path: fileURLToPath(new URL(name, out)), fullPage: true })
}

try {
  await login(manager, 'manager1')
  await login(viewer, 'viewer')
  await page.goto(`${base}/forms`)
  await page.getByRole('menuitem', { name: /양식 자료실$/ }).waitFor()
  const parentName = `검증 양식 ${suffix}`
  await createFolder(parentName)
  await page.getByRole('button', { name: `폴더 열기: ${parentName}`, exact: true }).click()
  const child = await createFolder('월별 점검표')
  await page.getByRole('button', { name: '폴더 이름 변경: 월별 점검표', exact: true }).click()
  const rename = page.getByRole('dialog', { name: '폴더 이름 변경', exact: true })
  await rename.getByLabel('폴더 이름', { exact: true }).fill('정기 점검표')
  await rename.getByRole('button', { name: '저장', exact: true }).click()
  await page.getByRole('button', { name: '폴더 열기: 정기 점검표', exact: true }).waitFor()
  await capture('folders.png')
  await page.getByRole('button', { name: '폴더 열기: 정기 점검표', exact: true }).click()
  await page.getByRole('navigation', { name: '폴더 경로' }).getByText(parentName, { exact: true }).waitFor()

  await page.getByRole('button', { name: /양식 등록$/ }).click()
  let editor = page.getByRole('dialog', { name: '양식 등록', exact: true })
  await editor.getByLabel('양식 이름', { exact: true }).fill(`가상 점검표 ${suffix}`)
  await editor.getByLabel('설명', { exact: true }).fill('매월 작성하는 가상 점검표입니다.')
  await editor.locator('input[type=file]').setInputFiles([
    { name: '점검표.pdf', mimeType: 'application/pdf', buffer: pdf },
    { name: '작성 안내.pdf', mimeType: 'application/pdf', buffer: pdf },
  ])
  await capture('editor.png')
  const creating = responseFor('/forms', 'POST')
  await editor.getByRole('button', { name: '저장', exact: true }).click()
  const createdResponse = await creating
  assert.equal(createdResponse.status(), 201)
  const created = await createdResponse.json()
  postIds.push(created.id)
  assert.equal(created.folderId, child)
  assert.equal(created.files.length, 2)
  let detail = page.getByRole('dialog', { name: '양식 상세', exact: true })
  await detail.getByText('매월 작성하는 가상 점검표입니다.', { exact: true }).waitFor()
  const downloadEvent = page.waitForEvent('download')
  await detail.getByRole('button', { name: '내려받기: 점검표.pdf', exact: true }).click()
  const download = await downloadEvent
  assert.equal(download.suggestedFilename(), '점검표.pdf')
  assert.deepEqual(readFileSync(await download.path()), pdf)
  await capture('detail.png')

  // 삭제 예약 후 취소하면 서버의 첨부는 그대로 남는다.
  await detail.getByRole('button', { name: '수정', exact: true }).click()
  editor = page.getByRole('dialog', { name: '양식 수정', exact: true })
  await editor.getByRole('button', { name: '첨부 삭제: 점검표.pdf', exact: true }).click()
  await editor.getByRole('button', { name: '취소', exact: true }).click()
  assert.equal((await (await manager.request.get(`${base}/api/forms/${created.id}`)).json()).files.length, 2)

  // 실제 UI에서 파일 교체를 준비하고, 저장 실패 후 입력·파일이 유지되는지 확인한다.
  detail = page.getByRole('dialog', { name: '양식 상세', exact: true })
  await detail.getByRole('button', { name: '수정', exact: true }).click()
  editor = page.getByRole('dialog', { name: '양식 수정', exact: true })
  await editor.getByLabel('양식 이름', { exact: true }).fill(`개정 점검표 ${suffix}`)
  await editor.getByRole('button', { name: '첨부 삭제: 점검표.pdf', exact: true }).click()
  const transfer = await page.evaluateHandle((bytes) => {
    const data = new DataTransfer()
    data.items.add(new File([new Uint8Array(bytes)], '개정 점검표.pdf', { type: 'application/pdf' }))
    return data
  }, [...pdf])
  await editor.locator('.ant-upload-drag .ant-upload-btn').dispatchEvent('drop', { dataTransfer: transfer })
  await transfer.dispose()
  await page.route(`**/api/forms/${created.id}`, (route) => route.request().method() === 'PUT'
    ? route.fulfill({ status: 503, json: { status: 503, detail: '검증용 저장 실패' } }) : route.continue())
  await editor.getByRole('button', { name: '저장', exact: true }).click()
  await editor.getByText('검증용 저장 실패', { exact: true }).waitFor()
  assert.equal(await editor.getByLabel('양식 이름', { exact: true }).inputValue(), `개정 점검표 ${suffix}`)
  await editor.getByText('개정 점검표.pdf', { exact: true }).waitFor()
  await page.unroute(`**/api/forms/${created.id}`)
  const updating = responseFor(`/forms/${created.id}`, 'PUT')
  await editor.getByRole('button', { name: '저장', exact: true }).click()
  assert.equal((await updating).status(), 200)
  const updated = await (await manager.request.get(`${base}/api/forms/${created.id}`)).json()
  assert.deepEqual(updated.files.map((file) => file.originalName), ['작성 안내.pdf', '개정 점검표.pdf'])
  assert.equal((await manager.request.get(`${base}/api/files/${created.files[0].id}/download`)).status(), 404)
  await page.getByRole('dialog', { name: '양식 상세', exact: true }).getByRole('button', { name: '닫기', exact: true }).last().click()

  await page.getByRole('searchbox', { name: '양식 이름 검색', exact: true }).fill('없는 양식')
  await page.getByRole('searchbox', { name: '양식 이름 검색', exact: true }).press('Enter')
  await page.getByText('검색 결과가 없습니다.', { exact: true }).waitFor()
  await page.getByRole('searchbox', { name: '양식 이름 검색', exact: true }).fill('개정 점검표')
  await page.getByRole('searchbox', { name: '양식 이름 검색', exact: true }).press('Enter')
  await page.getByRole('button', { name: `양식 열기: 개정 점검표 ${suffix}`, exact: true }).waitFor()
  await page.getByRole('tab', { name: '계약 양식', exact: true }).click()
  await page.getByText('검색 결과가 없습니다.', { exact: true }).waitFor()
  await page.getByRole('tab', { name: '전체', exact: true }).click()
  await page.getByRole('button', { name: `양식 열기: 개정 점검표 ${suffix}`, exact: true }).waitFor()
  await capture('manager.png')

  // 조회자의 탐색·다운로드 및 서버 편집 차단을 확인한다.
  const viewerPage = await viewer.newPage()
  await viewerPage.goto(`${base}/forms/folders/${child}`)
  await viewerPage.getByRole('button', { name: `양식 열기: 개정 점검표 ${suffix}`, exact: true }).click()
  const viewerDetail = viewerPage.getByRole('dialog', { name: '양식 상세', exact: true })
  assert.equal(await viewerPage.getByRole('button', { name: /새 폴더$/ }).count(), 0)
  assert.equal(await viewerDetail.getByRole('button', { name: '수정', exact: true }).count(), 0)
  const viewerDownload = viewerPage.waitForEvent('download')
  await viewerDetail.getByRole('button', { name: '내려받기: 개정 점검표.pdf', exact: true }).click()
  assert.deepEqual(readFileSync(await (await viewerDownload).path()), pdf)
  assert.equal((await viewer.request.delete(`${base}/api/forms/${created.id}`)).status(), 403)
  assert.equal((await viewer.request.delete(`${base}/api/form-folders/${child}`)).status(), 403)
  await capture('viewer.png', viewerPage)

  // 비어 있지 않은 폴더 삭제는 거절하고, 양식 삭제 후 빈 폴더만 지운다.
  await page.getByRole('navigation', { name: '폴더 경로' }).getByRole('link', { name: parentName, exact: true }).click()
  await page.getByRole('button', { name: '폴더 삭제: 정기 점검표', exact: true }).click()
  const confirmFolder = page.getByRole('dialog', { name: '폴더를 삭제하시겠습니까?', exact: true })
  const denied = responseFor(`/form-folders/${child}`, 'DELETE')
  await confirmFolder.getByRole('button', { name: '삭제', exact: true }).click()
  assert.equal((await denied).status(), 400)
  await page.getByText('하위 폴더와 양식을 먼저 삭제한 뒤 빈 폴더를 삭제해 주세요.', { exact: true }).waitFor()
  await confirmFolder.getByRole('button', { name: '취소', exact: true }).click()
  await page.getByRole('button', { name: '폴더 열기: 정기 점검표', exact: true }).click()
  await page.getByRole('button', { name: `양식 열기: 개정 점검표 ${suffix}`, exact: true }).click()
  await page.getByRole('dialog', { name: '양식 상세', exact: true }).getByRole('button', { name: '삭제', exact: true }).click()
  const confirming = page.getByRole('dialog', { name: '양식을 삭제하시겠습니까?', exact: true })
  await confirming.getByRole('button', { name: '취소', exact: true }).click()
  assert.equal((await manager.request.get(`${base}/api/forms/${created.id}`)).status(), 200)
  await page.getByRole('dialog', { name: '양식 상세', exact: true }).getByRole('button', { name: '삭제', exact: true }).click()
  const deleted = responseFor(`/forms/${created.id}`, 'DELETE')
  await page.getByRole('dialog', { name: '양식을 삭제하시겠습니까?', exact: true }).getByRole('button', { name: '삭제', exact: true }).click()
  assert.equal((await deleted).status(), 204)
  await page.getByText('등록된 양식이 없습니다.', { exact: true }).waitFor()
  await page.getByRole('navigation', { name: '폴더 경로' }).getByRole('link', { name: parentName, exact: true }).click()
  await page.getByRole('button', { name: '폴더 삭제: 정기 점검표', exact: true }).click()
  const folderDeleted = responseFor(`/form-folders/${child}`, 'DELETE')
  await page.getByRole('dialog', { name: '폴더를 삭제하시겠습니까?', exact: true }).getByRole('button', { name: '삭제', exact: true }).click()
  assert.equal((await folderDeleted).status(), 204)
  await page.getByRole('button', { name: '폴더 열기: 정기 점검표', exact: true }).waitFor({ state: 'hidden' })
  console.log('PASS: 하위 폴더·경로·이름 변경·검색·분류·다중 첨부·드롭·원본 다운로드·취소/실패 보존·교체·조회자 권한·빈 폴더만 삭제')
} catch (error) {
  console.error('실패 화면:', page.url(), await page.locator('[role="menuitem"]').allTextContents())
  console.error('열린 대화상자:', await page.getByRole('dialog').allTextContents())
  await page.screenshot({ path: '/tmp/hkagri-forms-failure.png', fullPage: true })
  throw error
} finally {
  for (const id of postIds) await manager.request.delete(`${base}/api/forms/${id}`).catch(() => {})
  for (const id of folderIds.reverse()) await manager.request.delete(`${base}/api/form-folders/${id}`).catch(() => {})
  await browser.close()
}
