import { useContext } from 'react'
import classNames from 'classnames'
import { Link, NavLink } from 'react-router-dom'
import Avatar from '../Avatar'
import { AppContext } from '../../contexts/app.context'
import path from '../../constants/path'

// NavLink cho className một hàm nhận { isActive }: đường dẫn hiện tại khớp với `to` thì isActive = true
const linkClassName = ({ isActive }: { isActive: boolean }) =>
  classNames('block py-2 transition-colors', {
    'text-orange': isActive,
    'text-gray-700 hover:text-orange': !isActive
  })

export default function UserSideNav() {
  const { profile } = useContext(AppContext)

  return (
    <div>
      <div className='flex items-center border-b border-b-gray-200 py-4'>
        <Link to={path.profile} className='size-12 flex-shrink-0 overflow-hidden rounded-full border border-black/10'>
          <Avatar src={profile?.avatar} className='size-full' />
        </Link>
        <div className='min-w-0 flex-grow pl-4'>
          <div className='truncate font-semibold text-gray-600'>{profile?.name || profile?.email}</div>
          <Link to={path.profile} className='text-gray-500 hover:text-orange'>
            Sửa hồ sơ
          </Link>
        </div>
      </div>
      <div className='mt-6'>
        <div className='font-medium text-gray-800'>Tài khoản của tôi</div>
        <div className='pl-4'>
          <NavLink to={path.profile} className={linkClassName}>
            Hồ sơ
          </NavLink>
          <NavLink to={path.password} className={linkClassName}>
            Đổi mật khẩu
          </NavLink>
        </div>
        <NavLink to={path.purchase} className={(state) => classNames(linkClassName(state), 'mt-2 font-medium')}>
          Đơn mua
        </NavLink>
      </div>
    </div>
  )
}
