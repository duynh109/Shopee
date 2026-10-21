import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate } from 'react-router-dom'
import { toast } from 'react-toastify'
import { omit } from 'lodash'
import cartApi from '../../apis/cart.api'
import Button from '../../components/Button'
import QuantityController from '../../components/QuantityController'
import path from '../../constants/path'
import noproduct from '../../assets/images/no-product.png'
import { CartItem } from '../../types/cart.type'
import { ProblemDetail } from '../../types/utils.type'
import { formatCurrency, generateNameId, hasDiscount, isAxiosUnprocessableEntityError } from '../../utils/utils'

export default function Cart() {
  const queryClient = useQueryClient()
  const navigate = useNavigate()

  // Dòng nào đang được tick. Chỉ giữ id, dữ liệu dòng luôn đọc từ server (cartItems)
  const [checkedIds, setCheckedIds] = useState<string[]>([])
  // Số lượng người dùng đang gõ trong ô nhập nhưng chưa gửi lên server. Gõ xong (rời ô) mới gửi một lần,
  // chứ gửi mỗi phím thì gõ "12" là hai request, mà request đầu có thể đã bị từ chối vì quá tồn kho.
  const [draftQuantities, setDraftQuantities] = useState<Record<string, number>>({})

  const { data: cartData } = useQuery({
    queryKey: ['cart'],
    queryFn: cartApi.getCart
  })
  const cartItems = cartData?.data

  const refreshCart = () => queryClient.invalidateQueries({ queryKey: ['cart'] })
  const clearDraft = (id: string) => setDraftQuantities((prev) => omit(prev, id))

  const updateMutation = useMutation({
    mutationFn: (body: { id: string; quantity: number }) =>
      cartApi.updateCartItem(body.id, { quantity: body.quantity }),
    // Chờ giỏ tải lại xong mới chạy onSettled, để ô số lượng không nháy về số cũ rồi mới nhảy sang số mới
    onSuccess: refreshCart,
    onError: (error) => {
      // Interceptor không hiện toast cho 422 (để form tự hiện lỗi từng field), nên ở đây phải tự hiện.
      // Vượt tồn kho là 422 với errors.quantity (API_SPEC §7.3)
      if (isAxiosUnprocessableEntityError<ProblemDetail>(error)) {
        toast.error(error.response?.data.errors?.quantity ?? error.response?.data.title)
      }
    },
    // Thành công hay thất bại đều bỏ số đang gõ: thành công thì server đã giữ số đó, thất bại thì ô quay về số thật
    onSettled: (_data, _error, body) => clearDraft(body.id)
  })

  const deleteMutation = useMutation({
    // Xoá một dòng dùng endpoint riêng (404 nếu dòng không còn), xoá nhiều dùng `?ids=` (bỏ qua id lạ) — §7.4, §7.5
    mutationFn: (ids: string[]) => (ids.length === 1 ? cartApi.deleteCartItem(ids[0]) : cartApi.deleteCartItems(ids)),
    onSuccess: () => toast.success('Đã xoá khỏi giỏ hàng', { autoClose: 1000 }),
    // Lỗi cũng tải lại: dòng có thể đã bị xoá ở tab khác, tải lại để màn hình khớp với server
    onSettled: refreshCart
  })

  const items = cartItems ?? []
  // Lọc theo cartItems chứ không tin thẳng checkedIds: dòng đã biến mất khỏi giỏ thì không còn được tính tiền
  const checkedItems = items.filter((item) => checkedIds.includes(item.id))
  const isAllChecked = items.length > 0 && checkedItems.length === items.length
  const totalPrice = checkedItems.reduce((sum, item) => sum + item.product.price * item.quantity, 0)
  // Sản phẩm không giảm giá (giá gốc null) thì không tiết kiệm được đồng nào, chứ không phải 0 - giá = số âm
  const totalSaved = checkedItems.reduce((sum, item) => {
    const { priceBeforeDiscount, price } = item.product
    return sum + (hasDiscount(priceBeforeDiscount, price) ? priceBeforeDiscount - price : 0) * item.quantity
  }, 0)

  const handleCheck = (id: string) => {
    setCheckedIds((prev) => (prev.includes(id) ? prev.filter((checkedId) => checkedId !== id) : [...prev, id]))
  }

  const handleCheckAll = () => {
    setCheckedIds(isAllChecked ? [] : items.map((item) => item.id))
  }

  const handleType = (item: CartItem, quantity: number) => {
    setDraftQuantities((prev) => ({ ...prev, [item.id]: quantity }))
  }

  // Nút +/- thì gửi ngay; ô nhập thì gọi lúc rời ô (onBlur) với số đang gõ
  const commitQuantity = (item: CartItem, quantity: number) => {
    if (quantity === item.quantity) {
      clearDraft(item.id)
      return
    }
    setDraftQuantities((prev) => ({ ...prev, [item.id]: quantity }))
    updateMutation.mutate({ id: item.id, quantity })
  }

  const handleDeleteChecked = () => {
    deleteMutation.mutate(checkedItems.map((item) => item.id))
  }

  // Trang đặt hàng (/checkout) đọc danh sách dòng đã chọn từ state này
  const handleBuy = () => {
    navigate(path.checkout, { state: { cartItemIds: checkedItems.map((item) => item.id) } })
  }

  return (
    <div className='bg-neutral-100 py-16'>
      <div className='container'>
        {cartItems && items.length === 0 && (
          <div className='text-center'>
            <img src={noproduct} alt='Giỏ hàng trống' className='mx-auto size-24' />
            <div className='mt-5 font-bold text-gray-400'>Giỏ hàng của bạn còn trống</div>
            <div className='mt-5'>
              <Link
                to={path.home}
                className='rounded-sm bg-orange px-10 py-2 uppercase text-white transition-all hover:bg-orange/80'
              >
                Mua ngay
              </Link>
            </div>
          </div>
        )}
        {items.length > 0 && (
          <>
            <div className='overflow-auto'>
              <div className='min-w-[1000px]'>
                <div className='grid grid-cols-12 rounded-sm bg-white px-9 py-5 text-sm capitalize text-gray-500 shadow'>
                  <div className='col-span-6'>
                    <div className='flex items-center'>
                      <div className='flex flex-shrink-0 items-center justify-center pr-3'>
                        <input
                          type='checkbox'
                          className='size-5 accent-orange'
                          checked={isAllChecked}
                          onChange={handleCheckAll}
                        />
                      </div>
                      <div className='flex-grow text-black'>Sản phẩm</div>
                    </div>
                  </div>
                  <div className='col-span-6'>
                    <div className='grid grid-cols-5 text-center'>
                      <div className='col-span-2'>Đơn giá</div>
                      <div className='col-span-1'>Số lượng</div>
                      <div className='col-span-1'>Số tiền</div>
                      <div className='col-span-1'>Thao tác</div>
                    </div>
                  </div>
                </div>
                <div className='my-3 rounded-sm bg-white p-5 shadow'>
                  {items.map((item) => {
                    const productPath = `${path.home}${generateNameId({ name: item.product.name, id: item.product.id })}`
                    const isSaving = updateMutation.isPending && updateMutation.variables?.id === item.id
                    return (
                      <div
                        key={item.id}
                        className='mb-5 grid grid-cols-12 items-center rounded-sm border border-gray-200 px-4 py-5 text-center text-sm text-gray-500 first:mt-0 last:mb-0'
                      >
                        <div className='col-span-6'>
                          <div className='flex'>
                            <div className='flex flex-shrink-0 items-center justify-center pr-3'>
                              <input
                                type='checkbox'
                                className='size-5 accent-orange'
                                checked={checkedIds.includes(item.id)}
                                onChange={() => handleCheck(item.id)}
                              />
                            </div>
                            <div className='flex flex-grow'>
                              <Link to={productPath} className='h-20 w-20 flex-shrink-0'>
                                <img
                                  alt={item.product.name}
                                  src={item.product.image}
                                  className='h-full w-full object-cover'
                                />
                              </Link>
                              <div className='flex-grow px-2 pb-2 pt-1 text-left'>
                                <Link to={productPath} className='line-clamp-2 text-black'>
                                  {item.product.name}
                                </Link>
                              </div>
                            </div>
                          </div>
                        </div>
                        <div className='col-span-6'>
                          <div className='grid grid-cols-5 items-center'>
                            <div className='col-span-2'>
                              <div className='flex items-center justify-center'>
                                {hasDiscount(item.product.priceBeforeDiscount, item.product.price) && (
                                  <span className='mr-3 text-gray-300 line-through'>
                                    ₫{formatCurrency(item.product.priceBeforeDiscount)}
                                  </span>
                                )}
                                <span>₫{formatCurrency(item.product.price)}</span>
                              </div>
                            </div>
                            <div className='col-span-1'>
                              <QuantityController
                                max={item.product.quantity}
                                value={draftQuantities[item.id] ?? item.quantity}
                                classNameWrapper={isSaving ? 'pointer-events-none opacity-60' : ''}
                                onIncrease={(value) => commitQuantity(item, value)}
                                onDecrease={(value) => commitQuantity(item, value)}
                                onType={(value) => handleType(item, value)}
                                onBlur={() => commitQuantity(item, draftQuantities[item.id] ?? item.quantity)}
                              />
                            </div>
                            <div className='col-span-1'>
                              <span className='text-orange'>₫{formatCurrency(item.product.price * item.quantity)}</span>
                            </div>
                            <div className='col-span-1'>
                              <button
                                className='bg-none text-black transition-colors hover:text-orange disabled:cursor-not-allowed'
                                disabled={deleteMutation.isPending}
                                onClick={() => deleteMutation.mutate([item.id])}
                              >
                                Xóa
                              </button>
                            </div>
                          </div>
                        </div>
                      </div>
                    )
                  })}
                </div>
              </div>
            </div>
            <div className='sticky bottom-0 z-10 mt-8 flex flex-col rounded-sm border border-gray-100 bg-white p-5 shadow sm:flex-row sm:items-center'>
              <div className='flex flex-shrink-0 items-center justify-center pr-3'>
                <input
                  type='checkbox'
                  className='size-5 accent-orange'
                  checked={isAllChecked}
                  onChange={handleCheckAll}
                />
              </div>
              <button className='mx-3 border-none bg-none' onClick={handleCheckAll}>
                Chọn tất cả ({items.length})
              </button>
              <button
                className='mx-3 border-none bg-none disabled:cursor-not-allowed disabled:text-gray-300'
                disabled={checkedItems.length === 0 || deleteMutation.isPending}
                onClick={handleDeleteChecked}
              >
                Xóa
              </button>
              <div className='mt-5 flex flex-col sm:ml-auto sm:mt-0 sm:flex-row sm:items-center'>
                <div>
                  <div className='flex items-center sm:justify-end'>
                    <div>Tổng thanh toán ({checkedItems.length} sản phẩm):</div>
                    <div className='ml-2 text-2xl text-orange'>₫{formatCurrency(totalPrice)}</div>
                  </div>
                  <div className='flex items-center text-sm sm:justify-end'>
                    <div className='text-gray-500'>Tiết kiệm</div>
                    <div className='ml-6 text-orange'>₫{formatCurrency(totalSaved)}</div>
                  </div>
                </div>
                <Button
                  className='mt-5 flex h-10 w-52 items-center justify-center bg-red-500 text-center text-sm uppercase text-white hover:bg-red-600 sm:ml-4 sm:mt-0'
                  disabled={checkedItems.length === 0}
                  onClick={handleBuy}
                >
                  Mua hàng
                </Button>
              </div>
            </div>
          </>
        )}
      </div>
    </div>
  )
}
