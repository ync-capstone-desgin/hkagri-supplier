import type { ThemeConfig } from 'antd'

/** 브랜드 기본색(짙은 녹색). 농산물 기업 이미지에 맞추고 흰 배경에서 대비가 충분한 색을 쓴다. */
export const BRAND = '#1f7a4d'

/**
 * Ant Design 전역 테마.
 * 업무 화면은 정보가 많으므로 글자 14px, 여백을 조금 줄인 밀도로 맞춘다.
 */
export const theme: ThemeConfig = {
  token: {
    colorPrimary: BRAND,
    colorLink: BRAND,
    fontFamily: "Pretendard, -apple-system, BlinkMacSystemFont, 'Apple SD Gothic Neo', 'Malgun Gothic', sans-serif",
    fontSize: 14,
    borderRadius: 6,
    colorBgLayout: '#f3f5f4',
  },
  components: {
    Layout: { headerBg: '#ffffff', siderBg: '#ffffff', headerHeight: 56, headerPadding: '0 20px' },
    Menu: { itemHeight: 42, itemSelectedBg: '#e8f3ec', itemSelectedColor: BRAND },
  },
}
