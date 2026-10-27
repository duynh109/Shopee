import { useContext } from 'react'
import { useForm } from 'react-hook-form'
import { yupResolver } from '@hookform/resolvers/yup'
import { useMutation } from '@tanstack/react-query'
import { toast } from 'react-toastify'
import authApi from '../../apis/auth.api'
import userApi from '../../apis/user.api'
import Button from '../../components/Button'
import FormRow from '../../components/FormRow'
import Input from '../../components/Input'
import { AppContext } from '../../contexts/app.context'
import { ProblemDetail } from '../../types/utils.type'
import { ChangePasswordSchema, changePasswordSchema } from '../../utils/rules'
import { isAxiosUnprocessableEntityError } from '../../utils/utils'

const inputClassName =
  'px-3 py-2 w-full outline-none border border-gray-300 focus:border-gray-500 rounded-sm focus:shadow-sm'

export default function ChangePassword() {
  const { setProfile } = useContext(AppContext)
  const {
    register,
    handleSubmit,
    setError,
    reset,
    formState: { errors }
  } = useForm<ChangePasswordSchema>({ resolver: yupResolver(changePasswordSchema) })

  const changePasswordMutation = useMutation({
    // Trả về true nếu sau khi đổi còn đăng nhập lại được
    mutationFn: async (body: { password: string; newPassword: string }): Promise<boolean> => {
      const { data: user } = await userApi.updateMe(body)
      // BE vừa thu hồi MỌI refresh token của user (API_SPEC §4.2). Access token đang cầm còn sống tối đa 15 phút,
      // hết hạn là không đổi được token nữa và bị đăng xuất. Nên đăng nhập lại ngay bằng mật khẩu mới để lấy cặp
      // token mới; http client tự lưu token khi gọi auth/login.
      try {
        const { data } = await authApi.loginAccount({ email: user.email, password: body.newPassword })
        setProfile(data.user)
        return true
      } catch {
        // Mật khẩu đã đổi xong rồi, chỉ bước đăng nhập lại hỏng (thường là mất mạng; interceptor đã hiện toast lỗi)
        return false
      }
    }
  })

  const onSubmit = handleSubmit((data) => {
    changePasswordMutation.mutate(
      { password: data.password, newPassword: data.newPassword },
      {
        onSuccess: (reloggedIn) => {
          reset()
          if (reloggedIn) {
            toast.success('Đổi mật khẩu thành công')
          } else {
            toast.warning(
              'Đã đổi mật khẩu nhưng chưa đăng nhập lại được. Bạn sẽ cần đăng nhập lại khi phiên hiện tại hết hạn.'
            )
          }
        },
        onError: (error) => {
          if (!isAxiosUnprocessableEntityError<ProblemDetail>(error)) return
          const formError = error.response?.data.errors
          // Sai mật khẩu hiện tại: errors.password (API_SPEC §4.2)
          if (formError?.password) setError('password', { type: 'server', message: formError.password })
          if (formError?.newPassword) setError('newPassword', { type: 'server', message: formError.newPassword })
        }
      }
    )
  })

  return (
    <div className='rounded-sm bg-white px-2 pb-10 shadow md:px-7 md:pb-20'>
      <div className='border-b border-b-gray-200 py-6'>
        <h1 className='text-lg font-medium text-gray-900'>Đổi mật khẩu</h1>
        <div className='mt-1 text-gray-700'>Để bảo mật tài khoản, vui lòng không chia sẻ mật khẩu cho người khác</div>
      </div>

      <form className='mt-8 max-w-2xl' onSubmit={onSubmit} noValidate>
        <FormRow label='Mật khẩu hiện tại'>
          <Input
            name='password'
            type='password'
            autoComplete='current-password'
            register={register}
            errorMessage={errors.password?.message}
            classNameInput={inputClassName}
          />
        </FormRow>
        <FormRow label='Mật khẩu mới'>
          <Input
            name='newPassword'
            type='password'
            autoComplete='new-password'
            register={register}
            errorMessage={errors.newPassword?.message}
            classNameInput={inputClassName}
          />
        </FormRow>
        <FormRow label='Xác nhận mật khẩu'>
          <Input
            name='confirmNewPassword'
            type='password'
            autoComplete='new-password'
            register={register}
            errorMessage={errors.confirmNewPassword?.message}
            classNameInput={inputClassName}
          />
        </FormRow>
        <FormRow label=''>
          <Button
            type='submit'
            className='flex h-9 items-center rounded-sm bg-orange px-5 text-center text-sm text-white hover:bg-orange/80'
            isLoading={changePasswordMutation.isPending}
            disabled={changePasswordMutation.isPending}
          >
            Xác nhận
          </Button>
        </FormRow>
      </form>
    </div>
  )
}
