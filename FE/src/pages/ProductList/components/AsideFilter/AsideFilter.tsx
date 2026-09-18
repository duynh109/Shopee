import { createSearchParams, Link, useNavigate } from 'react-router-dom'
import { omit } from 'lodash'
import { yupResolver } from '@hookform/resolvers/yup'
import { ObjectSchema } from 'yup'
import classNames from 'classnames'
import path from '../../../../constants/path'
import Button from '../../../../components/Button'
import { Category } from '../../../../types/category.type'
import InputNumber from '../../../../components/InputNumber'
import { useForm, Controller } from 'react-hook-form'
import { Schema, schema } from '../../../../utils/rules'
import { NoUndefinedField } from '../../../../types/utils.type'
import RatingStars from '../RatingStars'
import { QueryConfig } from '../../../../hooks/useQueryConfig'
import InputV2 from '../../../../components/InputV2'
interface Props {
  queryConfig: QueryConfig
  categories: Category[]
}

type FormData = NoUndefinedField<Pick<Schema, 'price_max' | 'price_min'>>

/**
 * Rule validate
 * Nếu có price_min thì price_max thì price_max >= price_min
 * Còn không thì có price_min thì không có price_max và ngược lại
 */

const priceSchema = schema.pick(['price_min', 'price_max'])

export default function AsideFilter({ queryConfig, categories }: Props) {
  const { category } = queryConfig
  const {
    control,
    handleSubmit,
    trigger,
    formState: { errors }
  } = useForm<FormData>({
    defaultValues: {
      price_min: '',
      price_max: ''
    },
    resolver: yupResolver<FormData>(priceSchema as ObjectSchema<FormData>),
    shouldFocusError: false
  })

  const navigate = useNavigate()
  // console.log(errors)

  const onSubmit = handleSubmit((data) => {
    navigate({
      pathname: path.home,
      search: createSearchParams({
        ...queryConfig,
        price_max: data.price_max,
        price_min: data.price_min
      }).toString()
    })
  })

  const handleRemoveAll = () => {
    navigate({
      pathname: path.home,
      search: createSearchParams(omit(queryConfig, ['price_min', 'price_max', 'rating_filter', 'category'])).toString()
    })
  }

  return (
    <div className='py-4'>
      <Link
        to={path.home}
        className={classNames('flex items-center font-bold', {
          'text-orange': !category
        })}
      >
        <svg viewBox='0 0 12 10' className='mr-3 h-4 w-3 fill-current'>
          <g fillRule='evenodd' stroke='none' strokeWidth={1}>
            <g transform='translate(-373 -208)'>
              <g transform='translate(155 191)'>
                <g transform='translate(218 17)'>
                  <path d='m0 2h2v-2h-2zm4 0h7.1519633v-2h-7.1519633z' />
                  <path d='m0 6h2v-2h-2zm4 0h7.1519633v-2h-7.1519633z' />
                  <path d='m0 10h2v-2h-2zm4 0h7.1519633v-2h-7.1519633z' />
                </g>
              </g>
            </g>
          </g>
        </svg>
        Tất cả danh mục
      </Link>
      <div className='mb-[10px] mt-[18px] h-[1px] bg-gray-300' />
      <ul className='text-sm'>
        {categories.map((categoryItem) => {
          const isActive = category === categoryItem._id
          return (
            <li className='py-2 pl-2' key={categoryItem._id}>
              <Link
                to={{
                  pathname: path.home,
                  search: createSearchParams({
                    ...queryConfig,
                    category: categoryItem._id
                  }).toString()
                }}
                className={classNames('relative px-1', {
                  'font-semibold text-orange': isActive
                })}
              >
                {isActive && (
                  <svg viewBox='0 0 4 7' className='absolute left-[-10px] top-1 size-2 fill-orange'>
                    <polygon points='4 3.5 0 0 0 7' />
                  </svg>
                )}
                {categoryItem.name}
              </Link>
            </li>
          )
        })}
      </ul>
      <div className='mt-4 flex items-center font-bold uppercase'>
        <svg
          enableBackground='new 0 0 15 15'
          viewBox='0 0 15 15'
          x={0}
          y={0}
          className='mr-3 h-4 w-3 fill-current stroke-current'
        >
          <g>
            <polyline
              fill='none'
              points='5.5 13.2 5.5 5.8 1.5 1.2 13.5 1.2 9.5 5.8 9.5 10.2'
              strokeLinecap='round'
              strokeLinejoin='round'
              strokeMiterlimit={10}
            />
          </g>
        </svg>
        Bộ lọc tìm kiếm
      </div>
      <div className='my-5'>
        <div className='text-sm'>Khoảng Giá</div>
        <form className='mt-4' onSubmit={onSubmit}>
          <div className='flex items-start'>
            {/* <Controller
              control={control}
              name='price_min'
              render={({ field }) => {
                return (
                  <InputNumber
                    type='text'
                    className='w-20 grow text-xs'
                    placeholder='₫ TỪ'
                    classNameInput='p-1 w-full outline-none border border-gray-300 focus:border-gray-500 rounded-sm focus:shadow-sm'
                    classNameError='hidden'
                    {...field}
                    onChange={(event) => {
                      field.onChange(event)
                      trigger('price_max')
                    }}
                  />
                )
              }}
            /> */}

            <InputV2
              control={control}
              name='price_min'
              type='number'
              className='w-20 grow text-xs'
              placeholder='₫ TỪ'
              classNameInput='p-1 w-full outline-none border border-gray-300 focus:border-gray-500 rounded-sm focus:shadow-sm'
              classNameError='hidden'
              onChange={() => {
                trigger('price_max')
              }}
            />

            <div className='mx-2 mt-[2px] shrink-0'>-</div>
            <Controller
              control={control}
              name='price_max'
              render={({ field }) => {
                return (
                  <InputNumber
                    type='text'
                    className='w-20 grow text-xs'
                    placeholder='₫ ĐẾN'
                    classNameInput='p-1 w-full outline-none border border-gray-300 focus:border-gray-500 rounded-sm focus:shadow-sm'
                    classNameError='hidden'
                    {...field}
                    onChange={(event) => {
                      field.onChange(event)
                      trigger('price_min')
                    }}
                  />
                )
              }}
            />
          </div>
          <div className='mt-1 min-h-[1.25rem] text-center text-sm text-red-600'>{errors.price_min?.message}</div>
          <Button className='flex w-full items-center justify-center rounded-sm bg-orange p-2 text-sm uppercase text-white shadow-sm hover:bg-orange/80'>
            Áp dụng
          </Button>
        </form>
      </div>
      <div className='my-4 h-[0.5px] bg-gray-300' />
      <div className='mt-3'>
        <div className='text-sm'>Đánh giá</div>
        <RatingStars queryConfig={queryConfig} />
      </div>
      <div className='my-4 h-[1px] bg-gray-300' />
      <Button
        onClick={handleRemoveAll}
        className='flex w-full items-center justify-center rounded-sm bg-orange p-2 text-sm uppercase text-white shadow-sm hover:bg-orange/80'
      >
        Xóa tất cả
      </Button>
    </div>
  )
}
