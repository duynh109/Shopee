import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import orderApi from '../../apis/order.api'
import CancelOrderDialog from '../../components/CancelOrderDialog'
import OrderStatusBadge from '../../components/OrderStatusBadge'
import { cancellableStatuses, paymentMethodLabel, paymentStatusLabel } from '../../constants/order'
import path from '../../constants/path'
import { formatCurrency, formatDateTime, generateNameId } from '../../utils/utils'

export default function PurchaseDetail() {
  // :id lấy từ đường dẫn /user/purchase/:id (useParams luôn trả string | undefined)
  const { id } = useParams()
  const [isCancelling, setIsCancelling] = useState(false)

  const { data: orderData, isError } = useQuery({
    queryKey: ['order', id],
    queryFn: () => orderApi.getOrder(id as string),
    enabled: Boolean(id),
    // Mặc định React Query thử lại 3 lần khi lỗi (cách nhau 1s, 2s, 4s). Với 404 thử lại vô ích: đơn của người
    // khác hay không tồn tại thì lần nào cũng 404, màn hình chờ cả chục giây và hiện bốn toast liền nhau.
    retry: false
  })
  const order = orderData?.data

  if (isError) {
    return (
      <div className='bg-neutral-100 py-20 text-center'>
        <div className='text-gray-500'>Không tìm thấy đơn hàng</div>
        <Link to={path.purchase} className='mt-4 inline-block rounded-sm bg-orange px-6 py-2 text-white'>
          Về danh sách đơn mua
        </Link>
      </div>
    )
  }
  if (!order) return null

  return (
    <div className='bg-neutral-100 py-10'>
      <div className='container'>
        <div className='flex items-center justify-between'>
          <Link to={path.purchase} className='text-sm text-gray-500 hover:text-orange'>
            &lt; Quay lại
          </Link>
          <div className='flex items-center gap-3 text-sm'>
            <span className='text-gray-500'>Mã đơn: {order.orderCode}</span>
            <OrderStatusBadge status={order.status} />
          </div>
        </div>

        {order.cancelledReason && (
          <div className='mt-4 rounded-sm border border-gray-200 bg-white p-4 text-sm shadow'>
            <span className='text-gray-500'>Lý do huỷ: </span>
            {order.cancelledReason}
          </div>
        )}

        <div className='mt-4 rounded-sm bg-white p-6 text-sm shadow'>
          <div className='font-medium text-orange'>Thông tin nhận hàng</div>
          <div className='mt-3'>{order.recipientName}</div>
          <div className='mt-1 text-gray-500'>{order.recipientPhone}</div>
          <div className='mt-1 text-gray-500'>{order.shippingAddress}</div>
          {order.note && <div className='mt-3 text-gray-500'>Ghi chú: {order.note}</div>}
        </div>

        <div className='mt-4 rounded-sm bg-white p-6 shadow'>
          <div className='font-medium text-orange'>Sản phẩm</div>
          {order.items.map((item) => (
            <div key={item.id} className='flex items-center border-b border-gray-100 py-3 text-sm last:border-b-0'>
              <Link to={`${path.home}${generateNameId({ name: item.productName, id: item.productId })}`}>
                <img src={item.productImage} alt={item.productName} className='size-20 flex-shrink-0 object-cover' />
              </Link>
              <div className='ml-3 flex-grow'>
                <div className='line-clamp-2'>{item.productName}</div>
                <div className='mt-1 text-gray-500'>x{item.quantity}</div>
              </div>
              <div className='ml-3 flex-shrink-0 text-right'>
                <span className='mr-2 text-gray-300 line-through'>₫{formatCurrency(item.priceBeforeDiscount)}</span>
                <span className='text-orange'>₫{formatCurrency(item.price)}</span>
              </div>
            </div>
          ))}
        </div>

        <div className='mt-4 rounded-sm bg-white p-6 text-sm shadow'>
          <div className='flex justify-between'>
            <span className='text-gray-500'>Tổng tiền hàng</span>
            <span>₫{formatCurrency(order.subtotal)}</span>
          </div>
          <div className='mt-3 flex justify-between'>
            <span className='text-gray-500'>Phí vận chuyển</span>
            <span>₫{formatCurrency(order.shippingFee)}</span>
          </div>
          {order.discount > 0 && (
            <div className='mt-3 flex justify-between'>
              <span className='text-gray-500'>Giảm giá</span>
              <span>-₫{formatCurrency(order.discount)}</span>
            </div>
          )}
          <div className='mt-3 flex items-center justify-between border-t border-gray-100 pt-3'>
            <span className='text-gray-500'>Thành tiền</span>
            <span className='text-xl text-orange'>₫{formatCurrency(order.totalAmount)}</span>
          </div>
          <div className='mt-3 flex justify-between'>
            <span className='text-gray-500'>Phương thức thanh toán</span>
            <span>{paymentMethodLabel[order.paymentMethod]}</span>
          </div>
          <div className='mt-3 flex justify-between'>
            <span className='text-gray-500'>Trạng thái thanh toán</span>
            <span>{paymentStatusLabel[order.paymentStatus]}</span>
          </div>
          <div className='mt-3 flex justify-between'>
            <span className='text-gray-500'>Đặt lúc</span>
            <span>{formatDateTime(order.createdAt)}</span>
          </div>
          {cancellableStatuses.includes(order.status) && (
            <div className='mt-5 flex justify-end'>
              <button
                className='rounded-sm border border-gray-300 px-5 py-2 hover:bg-gray-50'
                onClick={() => setIsCancelling(true)}
              >
                Huỷ đơn
              </button>
            </div>
          )}
        </div>
      </div>

      {isCancelling && <CancelOrderDialog order={order} onClose={() => setIsCancelling(false)} />}
    </div>
  )
}
