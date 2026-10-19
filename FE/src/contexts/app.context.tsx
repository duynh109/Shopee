import { createContext, useEffect, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { LocalStorageEventTarget, getAccessTokenFromLS, getProfileFromLS } from '../utils/auth'
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
  // tự chuyển về /login. Xoá cả dữ liệu đã tải (giỏ hàng...) để người đăng nhập sau không thấy dữ liệu của người trước.
  useEffect(() => {
    const reset = () => {
      setIsAuthenticated(false)
      setProfile(null)
      queryClient.removeQueries()
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
