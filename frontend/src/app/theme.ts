import type { ThemeConfig } from 'antd'

/** 한국농산의 흑백 화면에서 링크와 선택 상태를 표시하는 베리색. */
export const BRAND = '#8a3048'

/**
 * Ant Design 전역 테마.
 * 업무 화면은 정보가 많으므로 글자 14px, 여백을 조금 줄인 밀도로 맞춘다.
 */
export const theme: ThemeConfig = {
  token: {
    colorPrimary: BRAND,
    colorLink: BRAND,
    colorText: '#222222',
    fontFamily: 'var(--app-font)',
    fontSize: 14,
    borderRadius: 6,
    colorBgLayout: '#f7f7f7',
  },
  components: {
    Layout: { headerBg: '#ffffff', siderBg: '#ffffff', headerHeight: 56, headerPadding: '0 20px' },
    Menu: { itemHeight: 42, itemSelectedBg: '#f8eef1', itemSelectedColor: BRAND },
  },
}
