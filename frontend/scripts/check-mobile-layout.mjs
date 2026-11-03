/** 로컬 화면과 가상 API 응답으로 모바일 메뉴·대시보드·PC 배치를 검증하고 캡처한다. 실제 휴대폰 검증은 별도다. */
import assert from 'node:assert/strict'
import { mkdirSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { chromium, webkit } from 'playwright-core'

const base = process.env.BASE_URL ?? 'http://127.0.0.1:5174'
assert(['localhost', '127.0.0.1'].includes(new URL(base).hostname), '로컬 서버에서만 실행하세요.')
const engine = process.env.BROWSER ?? 'chromium'
assert(['chromium', 'webkit'].includes(engine))
const out = new URL('../../docs/images/mobile-layout/', import.meta.url)
mkdirSync(out, { recursive: true })
const browser = await (engine === 'webkit' ? webkit.launch() : chromium.launch({ channel: 'chrome' }))
const context = await browser.newContext({ viewport: { width: 360, height: 800 }, isMobile: true, hasTouch: true, locale: 'ko-KR' })
const page = await context.newPage()
const errors = []
page.on('pageerror', (error) => errors.push(error.message))
let role = 'ADMIN'
const list = { content: [], page: 0, size: 20, totalElements: 0 }

/** 문서 및 본문에 불필요한 가로 스크롤이 생기지 않는지 검사한다(표 내부 스크롤은 제외). */
async function checkWidth() {
  const widths = await page.evaluate(() => [document.documentElement, document.querySelector('.app-page')]
    .map((el) => ({ client: el.clientWidth, scroll: el.scrollWidth })))
  for (const { client, scroll } of widths) {
    assert(client <= page.viewportSize().width, '레이아웃이 기기 화면 폭보다 큽니다.')
    assert(scroll <= client + 1, `가로 넘침: ${scroll} > ${client}`)
  }
}

/** 애니메이션이 끝난 화면을 정해진 파일에 저장한다. */
async function capture(name) {
  await page.evaluate(() => Promise.all(document.getAnimations().map((animation) => animation.finished.catch(() => {}))))
  await page.screenshot({ path: fileURLToPath(new URL(`${engine}-${name}.png`, out)) })
}

try {
  /** 모바일과 PC 검증에 같은 가상 API 응답을 제공한다. */
  const mockApi = async (route) => {
    const path = new URL(route.request().url()).pathname.replace('/api/', '')
    let json = list
    if (path === 'auth/me') json = { id: 1, username: 'demo', name: '모바일검증담당자', role }
    else if (path === 'auth/logout') return route.fulfill({ status: 204 })
    else if (path === 'codes') json = { role: [{ code: 'ADMIN', label: '관리자' }, { code: 'VIEWER', label: '조회자' }] }
    else if (path === 'users' || path === 'cert-types') json = []
    else if (path === 'admin/users') json = { users: [], pendingCount: 2 }
    else if (path === 'system/clock') json = { today: '2026-11-03', shifted: true, demoClock: true }
    else if (path === 'notifications/unread-count') json = { count: 1 }
    // 이 검사는 화면 이동이 대상이다. 204로 SSE 재접속을 중지해 종료 중 요청이 남지 않게 한다.
    else if (path === 'notifications/stream') return route.fulfill({ status: 204 })
    else if (path === 'notifications') json = { ...list, content: [{ id: 1, daysLeft: 3, title: '[계약] 가상 업체의 공급 계약 만료 안내' }] }
    else if (path === 'dashboard/summary') json = { today: '2026-11-03', supplierTotal: 10, supplierActive: 9,
      activeContracts: 12, activeCertifications: 8, mine: 3, expired: 2, within7: 1, within30: 3, within90: 5,
      unknown: 1, inProgress: 2, notStarted: 5, urgent: [{ targetType: 'CONTRACT', targetId: 1, supplierId: 1,
        supplierName: '가상 농산', name: '가상 공급 계약', daysLeft: 3, expiryStatus: 'IMMINENT', expiryDate: '2026-11-06', ownerName: '가상 담당자', renewalStatus: 'NONE' }] }
    await route.fulfill({ json })
  }
  await page.route(`${base}/api/**`, mockApi)
  await page.goto(`${base}/dashboard`)
  await page.getByText('거래중 9곳', { exact: true }).waitFor()
  assert.equal(await page.locator('.ant-layout-sider').count(), 0)
  assert.equal(await page.getByRole('tablist').count(), 0)
  await checkWidth()
  const cards = await page.locator('.dashboard > .ant-row > .ant-col').evaluateAll((els) => els.map((el) => {
    const { x, y, width } = el.getBoundingClientRect(); return { x, y, width }
  }))
  assert.equal(cards.length, 4)
  for (let i = 1; i < cards.length; i++) assert(cards[i].y > cards[i - 1].y && cards[i].x === cards[0].x)
  await capture('360-dashboard')
  await page.locator('.dashboard-progress').scrollIntoViewIfNeeded()
  await checkWidth()
  await capture('360-expiry')
  await page.locator('.app-page').evaluate((el) => { el.scrollTop = 0 })
  await page.getByRole('button', { name: '알림 1건', exact: true }).click()
  await page.getByText('미확인 알림 1건', { exact: true }).waitFor()
  await capture('360-notifications')
  assert(await page.getByText('미확인 알림 1건', { exact: true }).isVisible())
  const popup = await page.locator('.ant-popover').boundingBox()
  assert(popup.x >= 0 && popup.x + popup.width <= 361)
  await page.getByRole('button', { name: '알림 1건', exact: true }).click()
  const menuButton = page.getByRole('button', { name: '메뉴 열기', exact: true })
  await menuButton.focus()
  await page.keyboard.press('Enter')
  const drawer = page.getByRole('dialog', { name: '업무 메뉴', exact: true })
  await drawer.waitFor()
  await drawer.getByRole('menuitem', { name: /사용자 관리/ }).waitFor()
  await capture('360-menu')
  await page.keyboard.press('Escape')
  await drawer.waitFor({ state: 'hidden' })
  assert(await menuButton.evaluate((el) => el === document.activeElement))
  // 낮은 휴대폰 화면에서도 아래쪽 메뉴까지 스크롤하고 바깥 영역을 터치해 닫을 수 있다.
  await page.setViewportSize({ width: 360, height: 480 })
  await menuButton.tap()
  const lastMenu = drawer.getByRole('menuitem', { name: /사용자 관리/ })
  await lastMenu.scrollIntoViewIfNeeded()
  const lastMenuBox = await lastMenu.boundingBox()
  assert(lastMenuBox.y >= 0 && lastMenuBox.y + lastMenuBox.height <= 480)
  await page.touchscreen.tap(330, 200)
  await drawer.waitFor({ state: 'hidden' })
  await page.setViewportSize({ width: 360, height: 800 })
  await menuButton.tap()
  await drawer.getByRole('menuitem', { name: /협력업체/ }).tap()
  await page.waitForURL('**/suppliers')
  await drawer.waitFor({ state: 'hidden' })
  await checkWidth()
  await page.goBack()
  await page.waitForURL('**/dashboard')
  await menuButton.click()
  await drawer.getByRole('menuitem', { name: /대시보드/ }).click()
  await drawer.waitFor({ state: 'hidden' })
  // PC로 넓힐 때 서랍은 닫히고 좌측 메뉴와 작업 탭이 복구되어야 한다.
  await menuButton.click()
  await page.setViewportSize({ width: 1280, height: 900 })
  await page.locator('.ant-layout-sider').waitFor()
  await drawer.waitFor({ state: 'hidden' })
  assert.equal(await page.locator('.ant-layout-sider').evaluate((el) => el.getBoundingClientRect().width), 200)
  await page.getByRole('tab', { name: '대시보드', exact: true }).waitFor()
  await checkWidth()
  const tops = await page.locator('.dashboard > .ant-row > .ant-col').evaluateAll((els) => els.map((el) => el.getBoundingClientRect().y))
  assert(tops.every((top) => top === tops[0]))
  await capture('1280-dashboard')
  for (const width of [767, 768, 1440]) {
    await page.setViewportSize({ width, height: 900 })
    if (width < 768) await menuButton.waitFor()
    else await page.locator('.ant-layout-sider').waitFor()
    await checkWidth()
  }
  await page.setViewportSize({ width: 360, height: 800 })
  await menuButton.waitFor()
  assert.equal(await menuButton.getAttribute('aria-expanded'), 'false')
  // 조회자는 관리자 메뉴를 볼 수 없고 사용자 메뉴는 터치로 열 수 있어야 한다.
  role = 'VIEWER'
  await page.reload()
  await menuButton.click()
  await drawer.waitFor()
  assert.equal(await drawer.getByRole('menuitem', { name: /사용자 관리/ }).count(), 0)
  await page.keyboard.press('Escape')
  await drawer.waitFor({ state: 'hidden' })
  await page.getByRole('button', { name: /사용자 메뉴:/ }).click()
  await page.getByRole('menuitem', { name: /로그아웃/ }).click()
  await page.waitForURL('**/login')
  // 모바일 에뮬레이션과 분리한 PC 입력 환경에서 기존 호버 메뉴와 작업 탭을 확인한다.
  role = 'ADMIN'
  const desktop = await browser.newPage({ viewport: { width: 1280, height: 900 }, isMobile: false, hasTouch: false })
  desktop.on('pageerror', (error) => errors.push(error.message))
  await desktop.route(`${base}/api/**`, mockApi)
  await desktop.goto(`${base}/dashboard`)
  await desktop.getByText('거래중 9곳', { exact: true }).waitFor()
  assert.equal(await desktop.getByRole('button', { name: '메뉴 열기', exact: true }).count(), 0)
  await desktop.getByRole('button', { name: /사용자 메뉴:/ }).hover()
  await desktop.getByRole('menuitem', { name: /로그아웃/ }).waitFor()
  await desktop.locator('.ant-layout-sider').getByRole('menuitem', { name: /협력업체/ }).click()
  await desktop.waitForURL('**/suppliers')
  await desktop.getByRole('tab', { name: '협력업체', exact: true }).and(desktop.locator('[aria-selected="true"]')).waitFor()
  await desktop.getByRole('tab', { name: '대시보드', exact: true }).click()
  await desktop.waitForURL('**/dashboard')
  await desktop.close()
  assert.deepEqual(errors, [])
  console.log(`PASS (${engine}): 360px 배치·메뉴 이동/닫기/뒤로 가기·역할별 메뉴·알림·로그아웃·1280px PC 배치`)
} finally {
  await browser.close()
}
