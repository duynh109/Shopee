import type { PaymentMethod, PaymentStatus } from '../types/order.type'

export const orderStatus = {
  pending: 'PENDING',
  confirmed: 'CONFIRMED',
  shipping: 'SHIPPING',
  delivered: 'DELIVERED',
  cancelled: 'CANCELLED'
} as const

type OrderStatusValue = (typeof orderStatus)[keyof typeof orderStatus]

// Record<..., string> bắt buộc phải đủ mọi trạng thái: thêm trạng thái mới ở orderStatus mà quên ở đây thì TypeScript báo lỗi
export const orderStatusLabel: Record<OrderStatusValue, string> = {
  [orderStatus.pending]: 'Chờ xác nhận',
  [orderStatus.confirmed]: 'Đã xác nhận',
  [orderStatus.shipping]: 'Đang giao',
  [orderStatus.delivered]: 'Đã giao',
  [orderStatus.cancelled]: 'Đã huỷ'
}

// Các tab ở trang "Đơn mua". status = undefined nghĩa là không lọc (lấy tất cả)
export const purchaseTabs: { status?: OrderStatusValue; label: string }[] = [
  { label: 'Tất cả' },
  ...Object.values(orderStatus).map((status) => ({ status, label: orderStatusLabel[status] }))
]

// Chỉ đơn chưa giao đi mới huỷ được (API_SPEC §8.4). BE vẫn là nơi quyết định, FE chỉ ẩn nút cho đỡ nhầm
export const cancellableStatuses: OrderStatusValue[] = [orderStatus.pending, orderStatus.confirmed]

export const paymentMethodLabel: Record<PaymentMethod, string> = {
  COD: 'Thanh toán khi nhận hàng (COD)',
  BANK_TRANSFER: 'Chuyển khoản ngân hàng'
}

export const paymentStatusLabel: Record<PaymentStatus, string> = {
  UNPAID: 'Chưa thanh toán',
  PAID: 'Đã thanh toán',
  REFUNDED: 'Đã hoàn tiền'
}
