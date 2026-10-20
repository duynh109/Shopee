import { useEffect } from 'react'
import { useForm } from 'react-hook-form'
import { yupResolver } from '@hookform/resolvers/yup'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'react-toastify'
import orderApi from '../../apis/order.api'
import Button from '../Button'
import { Order } from '../../types/order.type'
import { ProblemDetail } from '../../types/utils.type'
import { Schema, schema } from '../../utils/rules'
import { isAxiosUnprocessableEntityError } from '../../utils/utils'

type FormData = Pick<Schema, 'cancelledReason'>
const cancelSchema = schema.pick(['cancelledReason'])

interface Props {
  order: Order
  onClose: () => void
}

// Hộp thoại hỏi lý do rồi huỷ đơn. Dùng chung cho trang danh sách và trang chi tiết: ai cần huỷ thì chỉ việc
// hiện nó lên, còn gọi API, báo kết quả và làm mới dữ liệu đều nằm ở đây.
export default function CancelOrderDialog({ order, onClose }: Props) {
  const queryClient = useQueryClient()
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors }
  } = useForm<FormData>({ resolver: yupResolver(cancelSchema) })

  const cancelMutation = useMutation({
    mutationFn: (body: FormData) => orderApi.cancelOrder(order.id, body.cancelledReason)
  })

  // Bấm Esc để đóng. Gắn listener vào document nên PHẢI gỡ khi hộp thoại biến mất (hàm trả về của effect),
  // nếu không mỗi lần mở lại sẽ chồng thêm một listener.
  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !cancelMutation.isPending) onClose()
    }
    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [onClose, cancelMutation.isPending])

  const refreshOrders = () => {
    // Khoá ['orders'] là tiền tố của mọi danh sách (mọi tab, mọi trang) nên làm mới được tất cả cùng lúc
    queryClient.invalidateQueries({ queryKey: ['orders'] })
    queryClient.invalidateQueries({ queryKey: ['order', order.id] })
  }

  const onSubmit = handleSubmit((data) => {
    cancelMutation.mutate(data, {
      onSuccess: () => {
        toast.success('Đã huỷ đơn hàng', { autoClose: 1500 })
        refreshOrders()
        onClose()
      },
      onError: (error) => {
        if (isAxiosUnprocessableEntityError<ProblemDetail>(error)) {
          // 422: lý do không hợp lệ. Hiện ngay dưới ô nhập, hộp thoại vẫn mở để người dùng sửa
          const message = error.response?.data.errors?.cancelledReason
          if (message) setError('cancelledReason', { message, type: 'Server' })
          return
        }
        // 409 (đơn vừa chuyển sang đang giao...) hay 404: interceptor đã hiện toast. Trạng thái trên màn hình
        // đã cũ nên tải lại, và đóng hộp thoại vì không huỷ được nữa.
        refreshOrders()
        onClose()
      }
    })
  })

  return (
    <div
      className='fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4'
      // Bấm ra ngoài hộp để đóng, trừ lúc đang gửi
      onClick={() => !cancelMutation.isPending && onClose()}
    >
      <form
        className='w-full max-w-md rounded-sm bg-white p-6 shadow-lg'
        onSubmit={onSubmit}
        noValidate
        // Chặn click lan lên lớp nền, nếu không bấm trong hộp cũng bị đóng
        onClick={(event) => event.stopPropagation()}
      >
        <div className='text-lg font-medium'>Huỷ đơn hàng</div>
        <div className='mt-1 text-sm text-gray-500'>Mã đơn: {order.orderCode}</div>
        <label className='mt-4 block text-sm' htmlFor='cancelledReason'>
          Lý do huỷ đơn
        </label>
        <textarea
          id='cancelledReason'
          rows={3}
          className='mt-1 w-full rounded-sm border border-gray-300 p-2 text-sm outline-none focus:border-gray-500'
          placeholder='Ví dụ: Đặt nhầm sản phẩm'
          {...register('cancelledReason')}
        />
        <div className='min-h-[1.25rem] text-sm text-red-600'>{errors.cancelledReason?.message}</div>
        <div className='mt-4 flex justify-end gap-3'>
          <button
            type='button'
            className='rounded-sm border border-gray-300 px-5 py-2 text-sm hover:bg-gray-50'
            onClick={onClose}
            disabled={cancelMutation.isPending}
          >
            Không huỷ
          </button>
          <Button
            type='submit'
            className='flex items-center justify-center rounded-sm bg-orange px-5 py-2 text-sm text-white hover:bg-orange/90'
            isLoading={cancelMutation.isPending}
            disabled={cancelMutation.isPending}
          >
            Xác nhận huỷ
          </Button>
        </div>
      </form>
    </div>
  )
}
