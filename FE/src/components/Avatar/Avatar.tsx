import classNames from 'classnames'

// Ảnh mặc định cho tài khoản chưa tải avatar (users.avatar = null)
const DEFAULT_AVATAR = 'https://down-vn.img.susercontent.com/file/7c7e682bcb00bf2687fea549e050bf35_tn'

interface Props {
  src?: string | null
  className?: string
}

export default function Avatar({ src, className }: Props) {
  // `||` chứ không phải `??`: chuỗi rỗng cũng phải rơi về ảnh mặc định
  return (
    <img
      src={src || DEFAULT_AVATAR}
      alt='Ảnh đại diện'
      className={classNames('rounded-full object-cover', className)}
    />
  )
}
