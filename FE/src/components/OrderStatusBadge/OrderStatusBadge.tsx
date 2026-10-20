import classNames from 'classnames'
import { orderStatus, orderStatusLabel } from '../../constants/order'
import { OrderStatus } from '../../types/order.type'

// Màu của từng trạng thái. Phải viết nguyên chuỗi class ở đây: Tailwind chỉ giữ lại những class nó đọc thấy
// trong mã nguồn, nên ghép chuỗi kiểu `text-${color}-700` sẽ không có CSS nào được sinh ra.
const statusClassName: Record<OrderStatus, string> = {
  [orderStatus.pending]: 'bg-yellow-100 text-yellow-700',
  [orderStatus.confirmed]: 'bg-blue-100 text-blue-700',
  [orderStatus.shipping]: 'bg-indigo-100 text-indigo-700',
  [orderStatus.delivered]: 'bg-green-100 text-green-700',
  [orderStatus.cancelled]: 'bg-gray-100 text-gray-500'
}

interface Props {
  status: OrderStatus
}

export default function OrderStatusBadge({ status }: Props) {
  return (
    <span className={classNames('rounded-sm px-2 py-1 text-xs font-medium uppercase', statusClassName[status])}>
      {orderStatusLabel[status]}
    </span>
  )
}
