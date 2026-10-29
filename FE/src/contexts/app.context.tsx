import { createContext, useEffect, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { LocalStorageEventTarget, getAccessTokenFromLS, getProfileFromLS } from '../utils/auth'
import { isPublicQuery } from '../utils/queryCache'
import { User } from '../types/user.type'

interface AppContextInterface {
  isAuthenticated: boolean
  setIsAuthenticated: React.Dispatch<React.SetStateAction<boolean>>
  profile: User | null
  setProfile: React.Dispatch<React.SetStateAction<User | null>>
}

const initialAppContext: AppContextInterface = {
  isAuthenticated: Boolean(getAccessTokenFromLS()),
  setIsAuthenticated: () => null,
  profile: getProfileFromLS(),
  setProfile: () => null
}

export const AppContext = createContext<AppContextInterface>(initialAppContext)

export const AppProvider = ({ children }: { children: React.ReactNode }) => {
  const queryClient = useQueryClient()
  const [isAuthenticated, setIsAuthenticated] = useState<boolean>(initialAppContext.isAuthenticated)
  const [profile, setProfile] = useState<User | null>(initialAppContext.profile)

  // Phiên bị xoá (đăng xuất, hoặc refresh token hỏng) thì đưa giao diện về trạng thái chưa đăng nhập; ProtectedRoute
  // tự chuyển về /login.
  // - Xoá dữ liệu riêng (giỏ hàng, đơn, hồ sơ) để người đăng nhập sau không thấy dữ liệu của người trước.
  // - KHÔNG xoá dữ liệu công khai (utils/queryCache.ts): trang đang mở (danh sách sản phẩm) vẫn giữ query đó, mà query bị xoá khỏi cache thì
  //   trang không tự tải lại, hiện trắng tới khi người dùng F5.
  useEffect(() => {
    const reset = () => {
      setIsAuthenticated(false)
      setProfile(null)
      queryClient.removeQueries({ predicate: (query) => !isPublicQuery(query.queryKey) })
    }
    LocalStorageEventTarget.addEventListener('clearLS', reset)
    return () => LocalStorageEventTarget.removeEventListener('clearLS', reset)
  }, [queryClient])

  return (
    <AppContext.Provider
      value={{
        isAuthenticated,
        setIsAuthenticated,
        profile,
        setProfile
      }}
    >
      {children}
    </AppContext.Provider>
  )
}
