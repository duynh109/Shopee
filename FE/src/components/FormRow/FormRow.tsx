interface Props {
  label: string
  children: React.ReactNode
}

// Một hàng "nhãn bên trái, ô nhập bên phải" của các form trong khu tài khoản. Phải là component viết ở ngoài
// các trang dùng nó: khai báo component bên trong một component khác thì mỗi lần render React coi đó là kiểu mới,
// dựng lại từ đầu và ô nhập mất focus giữa lúc đang gõ.
export default function FormRow({ label, children }: Props) {
  return (
    // Cột nhãn rộng cố định (không theo %): theo % thì khung hẹp sẽ làm nhãn bị cắt. Nhãn dài thì xuống dòng.
    <div className='mt-6 flex flex-col sm:flex-row sm:items-start'>
      <div className='pt-3 text-gray-500 sm:w-40 sm:flex-shrink-0 sm:pr-5 sm:text-right'>{label}</div>
      <div className='min-w-0 flex-grow'>{children}</div>
    </div>
  )
}
