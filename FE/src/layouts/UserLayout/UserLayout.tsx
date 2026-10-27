import { Outlet } from 'react-router-dom'
import UserSideNav from '../../components/UserSideNav'

// Khung chung của các trang trong khu tài khoản: menu bên trái, nội dung từng trang bên phải.
// Dùng làm "layout route" trong useRouteElements: các route con (hồ sơ, đổi mật khẩu, đơn mua) hiện ở chỗ <Outlet />.
export default function UserLayout() {
  return (
    <div className='bg-neutral-100 py-10 text-sm text-gray-600'>
      <div className='container'>
        <div className='grid grid-cols-1 gap-6 md:grid-cols-12'>
          <div className='md:col-span-3 lg:col-span-2'>
            <UserSideNav />
          </div>
          <div className='min-w-0 md:col-span-9 lg:col-span-10'>
            <Outlet />
          </div>
        </div>
      </div>
    </div>
  )
}
