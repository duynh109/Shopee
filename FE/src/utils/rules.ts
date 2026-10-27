import type { RegisterOptions, UseFormGetValues } from 'react-hook-form'
import * as yup from 'yup'
import { toISODate } from './utils'

type Rules = { [key in 'email' | 'password' | 'confirm_password']?: RegisterOptions }

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export const getRules = (getValues?: UseFormGetValues<any>): Rules => ({
  email: {
    required: {
      value: true,
      message: 'Email là bắt buộc'
    },
    pattern: {
      value: /^\S+@\S+\.\S+$/,
      message: 'Email không đúng định dạng'
    },
    maxLength: {
      value: 160,
      message: 'Độ dài từ 5-160 kí tự'
    },
    minLength: {
      value: 5,
      message: 'Độ dài từ 5-160 kí tự'
    }
  },
  password: {
    required: {
      value: true,
      message: 'Password là bắt buộc'
    },
    maxLength: {
      value: 160,
      message: 'Độ dài từ 6-160 kí tự'
    },
    minLength: {
      value: 6,
      message: 'Độ dài từ 6-160 kí tự'
    }
  },
  confirm_password: {
    required: {
      value: true,
      message: 'Nhập lại password là bắt buộc'
    },
    maxLength: {
      value: 160,
      message: 'Độ dài từ 6-160 kí tự'
    },
    minLength: {
      value: 6,
      message: 'Độ dài từ 6-160 kí tự'
    },
    validate:
      typeof getValues === 'function'
        ? (value) => value === getValues('password') || 'Nhập lại password không khớp'
        : undefined
  }
})

function testPriceMinMax(this: yup.TestContext<yup.AnyObject>) {
  const { priceMin, priceMax } = this.parent
  if (priceMin !== '' && priceMax !== '') {
    return Number(priceMin) <= Number(priceMax)
  }
  return priceMin !== '' || priceMax !== ''
}

const PHONE_REGEX = /^0\d{9,10}$/

export const schema = yup.object({
  email: yup
    .string()
    .required('Email là bắt buộc')
    .email('Email không đúng định dạng')
    .min(5, 'Độ dài từ 5-160 kí tự')
    .max(160, 'Độ dài từ 5-160 kí tự'),
  password: yup
    .string()
    .required('Password là bắt buộc')
    .min(6, 'Độ dài từ 6-160 kí tự')
    .max(160, 'Độ dài từ 6-160 kí tự'),
  confirm_password: yup
    .string()
    .required('Nhập lại password là bắt buộc')
    .min(6, 'Độ dài từ 6-160 kí tự')
    .max(160, 'Độ dài từ 6-160 kí tự')
    .oneOf([yup.ref('password')], 'Nhập lại password không khớp '),
  priceMin: yup.string().test({
    name: 'price-not-allowed',
    message: 'Giá không phù hợp',
    test: testPriceMinMax
  }),
  priceMax: yup.string().test({
    name: 'price-not-allowed',
    message: 'Giá không phù hợp',
    test: testPriceMinMax
  }),
  name: yup.string().trim().required('Tên sản phẩm là bắt buộc'),
  recipientName: yup.string().trim().required('Tên người nhận là bắt buộc').max(255, 'Tối đa 255 kí tự'),
  recipientPhone: yup.string().required('Số điện thoại là bắt buộc').matches(PHONE_REGEX, 'Số điện thoại không hợp lệ'),
  shippingAddress: yup.string().trim().required('Địa chỉ giao hàng là bắt buộc').max(255, 'Tối đa 255 kí tự'),
  // Không có .required: ghi chú là tuỳ chọn, nên kiểu suy ra là string | undefined
  note: yup.string().trim().max(500, 'Tối đa 500 kí tự'),
  cancelledReason: yup.string().trim().required('Vui lòng nhập lý do huỷ đơn').max(255, 'Tối đa 255 kí tự')
})

// Không dùng lại schema.name ở trên: đó là ô tìm kiếm và bắt buộc nhập, còn tên trong hồ sơ là tuỳ chọn.
// Mọi field để trống là hợp lệ: FE sẽ không gửi field trống, BE hiểu là "giữ nguyên".
export const profileSchema = yup.object({
  name: yup.string().trim().max(255, 'Tối đa 255 kí tự'),
  phone: yup.string().test('phone', 'Số điện thoại không hợp lệ', (value) => !value || PHONE_REGEX.test(value)),
  address: yup.string().trim().max(255, 'Tối đa 255 kí tự'),
  // So sánh chuỗi yyyy-MM-dd được vì cùng độ dài và thứ tự chữ cái trùng thứ tự thời gian
  dateOfBirth: yup
    .string()
    .test('past', 'Ngày sinh phải là ngày trong quá khứ', (value) => !value || value < toISODate(new Date()))
})

// BE giới hạn mật khẩu theo BYTE (bcrypt chỉ đọc 72 byte đầu), không theo kí tự: chữ có dấu chiếm 2–3 byte
const MAX_PASSWORD_BYTES = 72

export const changePasswordSchema = yup.object({
  // Chỉ bắt buộc nhập: đúng hay sai là việc của BE
  password: yup.string().required('Vui lòng nhập mật khẩu hiện tại'),
  newPassword: yup
    .string()
    .required('Vui lòng nhập mật khẩu mới')
    .min(6, 'Độ dài tối thiểu 6 kí tự')
    .test(
      'max-bytes',
      'Mật khẩu quá dài (tối đa 72 byte, chữ có dấu chiếm nhiều byte hơn)',
      (value) => !value || new TextEncoder().encode(value).length <= MAX_PASSWORD_BYTES
    ),
  confirmNewPassword: yup
    .string()
    .required('Vui lòng nhập lại mật khẩu mới')
    .oneOf([yup.ref('newPassword')], 'Nhập lại mật khẩu không khớp')
})

export type Schema = yup.InferType<typeof schema>
export type ProfileSchema = yup.InferType<typeof profileSchema>
export type ChangePasswordSchema = yup.InferType<typeof changePasswordSchema>
