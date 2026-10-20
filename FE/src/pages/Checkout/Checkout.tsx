import { useContext } from 'react'
import { useForm } from 'react-hook-form'
import { yupResolver } from '@hookform/resolvers/yup'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, Navigate, generatePath, useLocation, useNavigate } from 'react-router-dom'
import { toast } from 'react-toastify'
import cartApi from '../../apis/cart.api'
import orderApi from '../../apis/order.api'
import Button from '../../components/Button'
import Input from '../../components/Input'
import { paymentMethodLabel } from '../../constants/order'
import path from '../../constants/path'
import { AppContext } from '../../contexts/app.context'
import { ProblemDetail } from '../../types/utils.type'
import { Schema, schema } from '../../utils/rules'
import { formatCurrency, generateNameId, isAxiosUnprocessableEntityError } from '../../utils/utils'

type FormData = Pick<Schema, 'recipientName' | 'recipientPhone' | 'shippingAddress' | 'note'>
const checkoutSchema = schema.pick(['recipientName', 'recipientPhone', 'shippingAddress', 'note'])
const formFields: (keyof FormData)[] = ['recipientName', 'recipientPhone', 'shippingAddress', 'note']

export default function Checkout() {
  const location = useLocation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { profile } = useContext(AppContext)

  // Trang Giỏ hàng gửi sang danh sách dòng đã tick qua navigate(..., { state }). State nằm trong lịch sử trình
  // duyệt chứ không trên URL, nên tải lại trang vẫn còn, nhưng gõ thẳng /checkout thì không có.
  const cartItemIds = (location.state as { cartItemIds?: string[] } | null)?.cartItemIds

  const { data: cartData } = useQuery({ queryKey: ['cart'], queryFn: cartApi.getCart })
  // Chỉ lấy các dòng giỏ có trong danh sách đã chọn. Đọc từ giỏ (cache dùng chung với trang Giỏ hàng) chứ không
  // truyền cả dòng qua state, để giá và tồn kho luôn là bản mới nhất.
  const items = (cartData?.data ?? []).filter((item) => cartItemIds?.includes(item.id))
  const subtotal = items.reduce((sum, item) => sum + item.product.price * item.quantity, 0)

  // Điền sẵn từ hồ sơ. Hồ sơ lưu lúc đăng nhập nên có thể cũ, và người dùng sửa được ngay trên form
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors }
  } = useForm<FormData>({
    resolver: yupResolver(checkoutSchema),
    defaultValues: {
      recipientName: profile?.name ?? '',
      recipientPhone: profile?.phone ?? '',
      shippingAddress: profile?.address ?? '',
      note: ''
    }
  })

  const createOrderMutation = useMutation({ mutationFn: orderApi.createOrder })

  // Hook (useState, useQuery, useForm, useMutation...) phải được gọi theo cùng một thứ tự ở MỌI lần render, nên mọi
  // câu lệnh `return` sớm đều nằm SAU các hook ở trên. Đặt return trước hook thì React báo lỗi hoặc chạy sai.
  if (!cartItemIds || cartItemIds.length === 0) return <Navigate to={path.cart} replace />
  // Giỏ đã tải xong mà không còn dòng nào trong số đã chọn (đã bị xoá ở tab khác...): quay về giỏ
  if (cartData && items.length === 0) return <Navigate to={path.cart} replace />

  const onSubmit = handleSubmit((data) => {
    createOrderMutation.mutate(
      {
        // Gửi đúng những dòng đang hiện trên màn hình. BE từ chối cả đơn nếu có một id không đặt được
        cartItemIds: items.map((item) => item.id),
        recipientName: data.recipientName,
        recipientPhone: data.recipientPhone,
        shippingAddress: data.shippingAddress,
        // Ghi chú trống thì không gửi, để BE lưu null chứ không lưu chuỗi rỗng
        note: data.note || undefined,
        // v1 chỉ có COD (API_SPEC §2.5), nên không để người dùng chọn
        paymentMethod: 'COD'
      },
      {
        onSuccess: (response) => {
          toast.success('Đặt hàng thành công', { autoClose: 1500 })
          // Chuyển trang TRƯỚC khi làm mới giỏ: lúc giỏ tải lại, các dòng đã đặt biến mất và nếu trang này còn
          // đang hiện thì điều kiện "không còn dòng nào" ở trên sẽ đẩy người dùng về giỏ thay vì về đơn hàng
          navigate(generatePath(path.purchaseDetail, { id: response.data.id }), { replace: true })
          queryClient.invalidateQueries({ queryKey: ['cart'] })
          queryClient.invalidateQueries({ queryKey: ['orders'] })
        },
        onError: (error) => {
          if (isAxiosUnprocessableEntityError<ProblemDetail>(error)) {
            // 422: BE chỉ ra field nào sai, khoá trùng tên field của form nên đổ thẳng vào
            const serverErrors = error.response?.data.errors ?? {}
            formFields.forEach((field) => {
              if (serverErrors[field]) setError(field, { message: serverErrors[field], type: 'Server' })
            })
            return
          }
          // 404 (có dòng không còn) hoặc 409 (hết hàng, hoặc giỏ vừa đổi): interceptor đã hiện toast, tải lại giỏ
          // để màn hình khớp với thực tế
          queryClient.invalidateQueries({ queryKey: ['cart'] })
        }
      }
    )
  })

  return (
    <div className='bg-neutral-100 py-10'>
      <div className='container'>
        <div className='mb-4 text-xl font-medium'>Đặt hàng</div>

        <div className='rounded-sm bg-white p-6 shadow'>
          <div className='font-medium text-orange'>Thông tin nhận hàng</div>
          <form
            className='mt-4 grid grid-cols-1 gap-x-6 md:grid-cols-2'
            onSubmit={onSubmit}
            noValidate
            id='checkout-form'
          >
            <Input
              name='recipientName'
              register={register}
              errorMessage={errors.recipientName?.message}
              placeholder='Tên người nhận'
            />
            <Input
              name='recipientPhone'
              register={register}
              errorMessage={errors.recipientPhone?.message}
              placeholder='Số điện thoại (ví dụ 0912345678)'
              inputMode='tel'
            />
            <Input
              name='shippingAddress'
              register={register}
              className='md:col-span-2'
              errorMessage={errors.shippingAddress?.message}
              placeholder='Địa chỉ giao hàng'
            />
            <div className='md:col-span-2'>
              <textarea
                rows={2}
                className='w-full rounded-sm border border-gray-300 p-3 outline-none focus:border-gray-500 focus:shadow-sm'
                placeholder='Ghi chú cho người bán (không bắt buộc)'
                {...register('note')}
              />
              <div className='min-h-[1.25rem] text-sm text-red-600'>{errors.note?.message}</div>
            </div>
          </form>
        </div>

        <div className='mt-4 rounded-sm bg-white p-6 shadow'>
          <div className='font-medium text-orange'>Sản phẩm</div>
          <div className='mt-4 divide-y divide-gray-100'>
            {items.map((item) => (
              <div key={item.id} className='flex items-center py-3 text-sm'>
                <img src={item.product.image} alt={item.product.name} className='size-16 flex-shrink-0 object-cover' />
                <Link
                  to={`${path.home}${generateNameId({ name: item.product.name, id: item.product.id })}`}
                  className='mx-3 line-clamp-2 flex-grow'
                >
                  {item.product.name}
                </Link>
                <div className='w-28 text-right text-gray-500'>₫{formatCurrency(item.product.price)}</div>
                <div className='w-16 text-right text-gray-500'>x{item.quantity}</div>
                <div className='w-32 text-right text-orange'>₫{formatCurrency(item.product.price * item.quantity)}</div>
              </div>
            ))}
          </div>
        </div>

        <div className='mt-4 rounded-sm bg-white p-6 shadow'>
          <div className='flex items-center justify-between text-sm'>
            <div className='text-gray-500'>Phương thức thanh toán</div>
            <div>{paymentMethodLabel.COD}</div>
          </div>
          <div className='mt-3 flex items-center justify-between text-sm'>
            <div className='text-gray-500'>Tổng tiền hàng ({items.length} sản phẩm)</div>
            <div>₫{formatCurrency(subtotal)}</div>
          </div>
          <div className='mt-3 flex items-center justify-between text-sm'>
            <div className='text-gray-500'>Phí vận chuyển</div>
            <div className='text-gray-500'>Được tính khi đặt hàng</div>
          </div>
          <div className='mt-5 flex items-center justify-end'>
            <Link to={path.cart} className='mr-4 text-sm text-gray-500 hover:text-orange'>
              Quay lại giỏ hàng
            </Link>
            {/* form='checkout-form' cho nút nằm ngoài thẻ <form> vẫn submit được form đó */}
            <Button
              type='submit'
              form='checkout-form'
              className='flex h-10 w-52 items-center justify-center bg-red-500 text-center text-sm uppercase text-white hover:bg-red-600'
              isLoading={createOrderMutation.isPending}
              disabled={createOrderMutation.isPending}
            >
              Đặt hàng
            </Button>
          </div>
        </div>
      </div>
    </div>
  )
}
