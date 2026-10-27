import { useState } from 'react'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import classNames from 'classnames'
import { isUndefined, omitBy } from 'lodash'
import { Link, createSearchParams, generatePath } from 'react-router-dom'
import orderApi from '../../apis/order.api'
import CancelOrderDialog from '../../components/CancelOrderDialog'
import OrderStatusBadge from '../../components/OrderStatusBadge'
import Pagination from '../../components/Pagination'
import { cancellableStatuses, orderStatus, purchaseTabs } from '../../constants/order'
import path from '../../constants/path'
import useQueryParams from '../../hooks/useQueryParams'
import { Order, OrderStatus } from '../../types/order.type'
import { formatCurrency, generateNameId, hasDiscount } from '../../utils/utils'

// Giá trị trên URL là string bất kỳ người dùng gõ vào. Chỉ chấp nhận 5 trạng thái hợp lệ, còn lại coi như
// "tất cả": gửi `?status=abc` lên BE sẽ bị 400 và hiện toast lỗi.
const isOrderStatus = (value: string | undefined): value is OrderStatus =>
  Object.values(orderStatus).includes(value as OrderStatus)

export default function Purchase() {
  // Tab đang chọn và trang hiện tại nằm trên URL (?status=PENDING&page=2), không phải state: tải lại trang,
  // gửi link cho người khác, bấm nút Back đều giữ nguyên chỗ đang xem.
  const params = useQueryParams()
  const status = isOrderStatus(params.status) ? params.status : undefined
  // Giá trị lấy từ URL luôn là string, mà OrderListConfig khai là number nên phải đổi kiểu
  const page = Number(params.page) || 1
  const limit = Number(params.limit) || 10

  const { data: ordersData } = useQuery({
    // Khoá chứa mọi thứ queryFn phụ thuộc: đổi tab hoặc sang trang khác là một mục cache khác, tự tải lại
    queryKey: ['orders', { status, page, limit }],
    queryFn: () => orderApi.getOrders({ status, page, limit }),
    // Giữ danh sách cũ trên màn hình trong lúc tải trang/tab mới, đỡ nháy trắng
    placeholderData: keepPreviousData
  })
  const orders = ordersData?.data.items

  // Đơn đang được hỏi lý do huỷ. null = không hiện hộp thoại
  const [cancelling, setCancelling] = useState<Order | null>(null)

  // Nền xám và khung `container` do UserLayout lo, trang này chỉ vẽ phần nội dung
  return (
    <>
      <div>
        <div className='flex overflow-x-auto rounded-sm bg-white text-center shadow'>
          {purchaseTabs.map((tab) => (
            <Link
              key={tab.label}
              // Đổi tab thì bỏ `page`, để luôn về trang 1 của tab mới
              to={{
                pathname: path.purchase,
                search: createSearchParams(tab.status ? { status: tab.status } : {}).toString()
              }}
              className={classNames('flex-1 whitespace-nowrap border-b-2 px-4 py-4 text-sm', {
                'border-orange text-orange': tab.status === status,
                'border-b-black/10 text-gray-700 hover:text-orange': tab.status !== status
              })}
            >
              {tab.label}
            </Link>
          ))}
        </div>

        {orders && orders.length === 0 && (
          <div className='mt-4 rounded-sm bg-white py-20 text-center text-gray-400 shadow'>Chưa có đơn hàng</div>
        )}

        {orders?.map((order) => (
          <div key={order.id} className='mt-4 rounded-sm bg-white p-5 shadow'>
            <div className='flex items-center justify-between border-b border-gray-100 pb-3 text-sm'>
              <div className='text-gray-500'>Mã đơn: {order.orderCode}</div>
              <OrderStatusBadge status={order.status} />
            </div>

            {order.items.map((item) => (
              <div key={item.id} className='flex items-center border-b border-gray-100 py-3 text-sm'>
                <Link to={`${path.home}${generateNameId({ name: item.productName, id: item.productId })}`}>
                  <img src={item.productImage} alt={item.productName} className='size-20 flex-shrink-0 object-cover' />
                </Link>
                <div className='ml-3 flex-grow'>
                  <div className='line-clamp-2'>{item.productName}</div>
                  <div className='mt-1 text-gray-500'>x{item.quantity}</div>
                </div>
                <div className='ml-3 flex-shrink-0 text-right'>
                  {hasDiscount(item.priceBeforeDiscount, item.price) && (
                    <span className='mr-2 text-gray-300 line-through'>₫{formatCurrency(item.priceBeforeDiscount)}</span>
                  )}
                  <span className='text-orange'>₫{formatCurrency(item.price)}</span>
                </div>
              </div>
            ))}

            <div className='mt-4 flex items-center justify-end'>
              <div className='text-sm text-gray-500'>Thành tiền:</div>
              <div className='ml-2 text-xl text-orange'>₫{formatCurrency(order.totalAmount)}</div>
            </div>
            <div className='mt-4 flex justify-end gap-3'>
              {cancellableStatuses.includes(order.status) && (
                <button
                  className='rounded-sm border border-gray-300 px-5 py-2 text-sm hover:bg-gray-50'
                  onClick={() => setCancelling(order)}
                >
                  Huỷ đơn
                </button>
              )}
              <Link
                to={generatePath(path.purchaseDetail, { id: order.id })}
                className='rounded-sm bg-orange px-5 py-2 text-sm text-white hover:bg-orange/90'
              >
                Xem chi tiết
              </Link>
            </div>
          </div>
        ))}

        {ordersData && ordersData.data.totalPages > 1 && (
          <Pagination
            pathname={path.purchase}
            pageSize={ordersData.data.totalPages}
            // Giữ nguyên tab (và limit nếu có trên URL), Pagination chỉ đổi số trang
            queryConfig={omitBy({ status, page: String(page), limit: params.limit }, isUndefined)}
          />
        )}
      </div>

      {cancelling && <CancelOrderDialog order={cancelling} onClose={() => setCancelling(null)} />}
    </>
  )
}
