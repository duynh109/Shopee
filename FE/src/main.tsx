import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App'
import './index.css'
import { BrowserRouter } from 'react-router-dom'
import { ReactQueryDevtools } from '@tanstack/react-query-devtools'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AppProvider } from './contexts/app.context'
import { isAxiosError } from './utils/utils'

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: false,
      // Mặc định React Query thử lại 3 lần (cách nhau 1s, 2s, 4s) với MỌI lỗi. Lỗi 4xx là lỗi của request (không tìm thấy,
      // không có quyền, dữ liệu sai) nên lần nào cũng như nhau: thử lại chỉ làm trang chờ ~7 giây và hiện thêm toast.
      // Chỉ thử lại khi lỗi có thể tự hết: mất mạng, hoặc BE lỗi 5xx.
      retry: (failureCount, error) => {
        const status = isAxiosError(error) ? error.response?.status : undefined
        if (status !== undefined && status >= 400 && status < 500) return false
        return failureCount < 3
      }
    }
  }
})

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <QueryClientProvider client={queryClient}>
        <AppProvider>
          <App />
        </AppProvider>
        <ReactQueryDevtools initialIsOpen={false} />
      </QueryClientProvider>
    </BrowserRouter>
  </StrictMode>
)
