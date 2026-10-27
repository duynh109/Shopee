import { useContext, useEffect, useMemo, useRef, useState } from 'react'
import { useForm } from 'react-hook-form'
import { yupResolver } from '@hookform/resolvers/yup'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'react-toastify'
import userApi from '../../apis/user.api'
import Avatar from '../../components/Avatar'
import Button from '../../components/Button'
import FormRow from '../../components/FormRow'
import Input from '../../components/Input'
import { AppContext } from '../../contexts/app.context'
import { ProblemDetail } from '../../types/utils.type'
import { UpdateProfileBody, User } from '../../types/user.type'
import { setProfileToLS } from '../../utils/auth'
import { ProfileSchema, profileSchema } from '../../utils/rules'
import { isAxiosUnprocessableEntityError, toISODate } from '../../utils/utils'

const AVATAR_MAX_BYTES = 1024 * 1024
const AVATAR_TYPES = ['image/jpeg', 'image/png']

const formFields = ['name', 'phone', 'address', 'dateOfBirth'] as const
const isFormField = (key: string): key is (typeof formFields)[number] => (formFields as readonly string[]).includes(key)

// Field null trong DB thì ô nhập là chuỗi rỗng (ô nhập không nhận null)
const toFormValues = (user: User): ProfileSchema => ({
  name: user.name ?? '',
  phone: user.phone ?? '',
  address: user.address ?? '',
  dateOfBirth: user.dateOfBirth ?? ''
})

export default function Profile() {
  const queryClient = useQueryClient()
  const { setProfile } = useContext(AppContext)

  const { data: userData } = useQuery({ queryKey: ['me'], queryFn: userApi.getMe })
  const user = userData?.data

  // Ảnh vừa chọn nhưng chưa lưu. previewUrl là địa chỉ tạm trỏ tới file trong bộ nhớ trình duyệt, để hiện ảnh
  // trước khi upload
  const [avatar, setAvatar] = useState<{ file: File; previewUrl: string } | null>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)

  // Địa chỉ tạm giữ file trong bộ nhớ tới khi tải lại trang, nên phải tự giải phóng: cleanup chạy mỗi khi avatar đổi
  // (chọn ảnh khác, lưu xong) và khi rời trang
  useEffect(() => {
    return () => {
      if (avatar) URL.revokeObjectURL(avatar.previewUrl)
    }
  }, [avatar])

  const formValues = useMemo(() => (user ? toFormValues(user) : undefined), [user])

  const {
    register,
    handleSubmit,
    setError,
    reset,
    formState: { errors }
  } = useForm<ProfileSchema>({
    resolver: yupResolver(profileSchema),
    defaultValues: { name: '', phone: '', address: '', dateOfBirth: '' },
    // Có dữ liệu từ BE (hoặc vừa lưu xong) thì form tự đặt lại theo đó
    values: formValues
  })

  const saveMutation = useMutation({
    mutationFn: async ({ file, body }: { file: File | null; body: UpdateProfileBody }) => {
      // Upload chỉ lưu file và trả URL, chưa gắn vào user nên phải PUT tiếp (API_SPEC §4.3)
      const avatarUrl = file ? (await userApi.uploadAvatar(file)).data.url : undefined
      return userApi.updateMe({ ...body, avatar: avatarUrl })
    }
  })

  const onSubmit = handleSubmit((data) => {
    saveMutation.mutate(
      {
        file: avatar?.file ?? null,
        // Ô để trống thì không gửi (undefined): BE hiểu "giữ nguyên". Không có cách xoá một field về trống (§4.2)
        body: {
          name: data.name || undefined,
          phone: data.phone || undefined,
          address: data.address || undefined,
          dateOfBirth: data.dateOfBirth || undefined
        }
      },
      {
        onSuccess: (response) => {
          // Ba nơi giữ bản sao của user: cache của trang này, AppContext (Header, menu trái, form đặt hàng) và
          // localStorage (để còn sau khi tải lại trang)
          queryClient.setQueryData(['me'], response)
          setProfile(response.data)
          setProfileToLS(response.data)
          setAvatar(null)
          // Đặt lại form theo đúng dữ liệu BE vừa trả về. Không thể trông chờ `values` làm hộ: nếu BE trả y hệt dữ
          // liệu cũ thì RHF thấy "không đổi" và không reset, và ô người dùng vừa xoá trống vẫn trống trên màn hình
          // dù BE giữ nguyên giá trị cũ (ô trống không được gửi đi, xem onSubmit)
          reset(toFormValues(response.data))
          toast.success('Đã cập nhật hồ sơ')
        },
        onError: (error) => {
          if (!isAxiosUnprocessableEntityError<ProblemDetail>(error)) return
          // Lỗi của field nào hiện ở ô đó. Lỗi không thuộc ô nào (ví dụ errors.image của upload) thì hiện toast
          Object.entries(error.response?.data.errors ?? {}).forEach(([key, message]) => {
            if (isFormField(key)) setError(key, { type: 'server', message })
            else toast.error(message)
          })
        }
      }
    )
  })

  const handleFileChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    // Xoá giá trị ô chọn file: nếu không, chọn lại đúng file vừa chọn sẽ không kích hoạt onChange
    event.target.value = ''
    if (!file) return
    // Kiểm ở đây chỉ để người dùng khỏi chờ upload vô ích; BE vẫn kiểm lại bằng nội dung file
    if (!AVATAR_TYPES.includes(file.type)) {
      toast.error('Chỉ nhận ảnh PNG hoặc JPEG')
      return
    }
    if (file.size > AVATAR_MAX_BYTES) {
      toast.error('Ảnh tải lên tối đa 1MB')
      return
    }
    setAvatar({ file, previewUrl: URL.createObjectURL(file) })
  }

  // Ngày sinh phải là ngày đã qua: ô chọn ngày chỉ cho chọn tới hôm qua
  const yesterday = toISODate(new Date(Date.now() - 24 * 60 * 60 * 1000))

  return (
    <div className='rounded-sm bg-white px-2 pb-10 shadow md:px-7 md:pb-20'>
      <div className='border-b border-b-gray-200 py-6'>
        <h1 className='text-lg font-medium text-gray-900'>Hồ sơ của tôi</h1>
        <div className='mt-1 text-gray-700'>Quản lý thông tin hồ sơ để bảo mật tài khoản</div>
      </div>

      <form className='mt-8 flex flex-col-reverse md:flex-row md:items-start' onSubmit={onSubmit} noValidate>
        <div className='mt-6 flex-grow md:mt-0 md:pr-12'>
          <FormRow label='Email'>
            <div className='pt-3 text-gray-700'>{user?.email}</div>
          </FormRow>
          <FormRow label='Tên'>
            <Input
              name='name'
              register={register}
              errorMessage={errors.name?.message}
              classNameInput='px-3 py-2 w-full outline-none border border-gray-300 focus:border-gray-500 rounded-sm focus:shadow-sm'
            />
          </FormRow>
          <FormRow label='Số điện thoại'>
            <Input
              name='phone'
              register={register}
              errorMessage={errors.phone?.message}
              classNameInput='px-3 py-2 w-full outline-none border border-gray-300 focus:border-gray-500 rounded-sm focus:shadow-sm'
            />
          </FormRow>
          <FormRow label='Địa chỉ'>
            <Input
              name='address'
              register={register}
              errorMessage={errors.address?.message}
              classNameInput='px-3 py-2 w-full outline-none border border-gray-300 focus:border-gray-500 rounded-sm focus:shadow-sm'
            />
          </FormRow>
          <FormRow label='Ngày sinh'>
            <Input
              name='dateOfBirth'
              type='date'
              max={yesterday}
              register={register}
              errorMessage={errors.dateOfBirth?.message}
              classNameInput='px-3 py-2 w-full outline-none border border-gray-300 focus:border-gray-500 rounded-sm focus:shadow-sm'
            />
          </FormRow>
          <FormRow label=''>
            <Button
              type='submit'
              className='flex h-9 items-center rounded-sm bg-orange px-5 text-center text-sm text-white hover:bg-orange/80'
              isLoading={saveMutation.isPending}
              disabled={saveMutation.isPending}
            >
              Lưu
            </Button>
          </FormRow>
        </div>

        <div className='flex justify-center md:w-72 md:border-l md:border-l-gray-200'>
          <div className='flex flex-col items-center'>
            <div className='my-5 size-24'>
              <Avatar src={avatar?.previewUrl ?? user?.avatar} className='size-full' />
            </div>
            {/* Ô chọn file của trình duyệt không tạo kiểu được nên ẩn đi, nút bên dưới bấm hộ nó */}
            <input
              ref={fileInputRef}
              type='file'
              accept='.jpg,.jpeg,.png'
              className='hidden'
              onChange={handleFileChange}
            />
            <button
              type='button'
              className='flex h-10 items-center justify-end rounded-sm border bg-white px-6 text-sm text-gray-600 shadow-sm hover:bg-gray-50'
              onClick={() => fileInputRef.current?.click()}
            >
              Chọn ảnh
            </button>
            <div className='mt-3 text-gray-400'>Dung lượng file tối đa 1 MB</div>
            <div className='text-gray-400'>Định dạng: .JPEG, .PNG</div>
          </div>
        </div>
      </form>
    </div>
  )
}
