import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { App as AntApp, ConfigProvider } from 'antd'
import viVN from 'antd/locale/vi_VN'
import { AuthProvider } from './auth/Auth'
import AppRoutes from './routes'
import './index.css'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: 1, refetchOnWindowFocus: false, staleTime: 30_000 },
  },
})

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <ConfigProvider
      locale={viVN}
      theme={{
        token: {
          colorPrimary: '#166534',
          colorSuccess: '#166534',
          colorWarning: '#b45309',
          colorError: '#b91c1c',
          colorInfo: '#1d4ed8',
          colorTextBase: '#0f172a',
          colorTextSecondary: '#334155',
          colorTextTertiary: '#64748b',
          colorBorder: '#e2e8f0',
          colorBorderSecondary: '#f1f5f9',
          colorBgBase: '#ffffff',
          colorBgLayout: '#f8fafc',
          borderRadius: 4,
          borderRadiusSM: 2,
          borderRadiusLG: 6,
          fontFamily: "'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif",
          fontSize: 13,
          controlHeight: 32,
        },
        components: {
          Table: {
            headerBg: '#f1f5f9',
            headerColor: '#1e293b',
            borderColor: '#e2e8f0',
            headerBorderRadius: 0,
            cellPaddingBlock: 8,
            cellPaddingInline: 10,
            cellPaddingBlockSM: 6,
            cellPaddingInlineSM: 8,
            rowHoverBg: '#f8fafc',
            fontSize: 13,
          },
          Card: {
            headerHeight: 42,
            headerFontSize: 14,
            paddingLG: 16,
            padding: 14,
            borderRadiusLG: 4,
          },
          Button: {
            controlHeight: 32,
            controlHeightSM: 26,
            borderRadius: 4,
            paddingInline: 14,
          },
          Menu: {
            darkItemBg: 'transparent',
            darkSubMenuItemBg: '#0f172a',
            darkItemSelectedBg: '#1e293b',
            darkItemHoverBg: 'rgba(255, 255, 255, 0.06)',
            darkItemSelectedColor: '#ffffff',
            itemHeight: 40,
            itemMarginInline: 8,
            itemMarginBlock: 2,
            borderRadius: 4,
          },
          Layout: {
            siderBg: '#0f172a',
            headerBg: '#ffffff',
            bodyBg: '#f8fafc',
          },
          Tag: {
            borderRadiusSM: 2,
            fontSize: 12,
          },
          Statistic: {
            titleFontSize: 12,
            contentFontSize: 20,
          },
        },
      }}
    >
      <AntApp>
        <QueryClientProvider client={queryClient}>
          <BrowserRouter>
            <AuthProvider>
              <AppRoutes />
            </AuthProvider>
          </BrowserRouter>
        </QueryClientProvider>
      </AntApp>
    </ConfigProvider>
  </StrictMode>,
)
