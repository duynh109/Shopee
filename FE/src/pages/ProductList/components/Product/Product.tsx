import { Link } from 'react-router-dom'
import { Product as ProductType } from '../../../../types/product.type'
import { formatCurrency, formatNumberToSocialStyle, generateNameId, rateSale } from '../../../../utils/utils'
import ProductRating from '../../../../components/ProductRating'
import path from '../../../../constants/path'

interface Props {
  product: ProductType
}

export default function Product({ product }: Props) {
  return (
    <Link to={`${path.home}${generateNameId({ name: product.name, id: product._id })}`}>
      <div className='overflow-hidden rounded-sm bg-white shadow transition-transform duration-100 hover:translate-y-[-0.05rem] hover:shadow-md'>
        <div className='relative w-full pt-[100%]'>
          <img src={product.image} alt={product.name} className='absolute left-0 top-0 h-full w-full object-cover' />
          <img
            src='https://down-vn.img.susercontent.com/file/vn-11134258-7ras8-m1d5ib6irm8o68'
            className='absolute left-0 top-0 h-full w-full object-cover'
          />
        </div>
        <div className='flex flex-col overflow-hidden p-2'>
          <div className='line-clamp-2 min-h-[2rem] text-xs'>{product.name}</div>
          <div className='mt-3 flex items-center'>
            <div className='mr-[5px] text-orange'>
              <span className='text-xs'>₫</span>
              <span className='text-base'>{formatCurrency(product.price)}</span>
            </div>
            <div className='max-w-[50%] truncate text-sm text-gray-500 line-through'>
              ₫{formatCurrency(product.price_before_discount)}
            </div>
            <div className='ml-1 bg-[#feeeea] p-[0.125rem]'>
              <span className='block text-[0.625rem] leading-[0.75rem] text-orange'>
                -{rateSale(product.price_before_discount, product.price)}%
              </span>
            </div>
          </div>
          <div className='mt-3 flex items-center'>
            <ProductRating rating={product.rating} />
            <div className='ml-1 overflow-hidden text-xs leading-3'>
              Đã bán {formatNumberToSocialStyle(product.sold)}
            </div>
          </div>
          <div className='mt-3 flex text-[0.75rem] font-extralight text-black/65'>
            <img src='https://deo.shopeemobile.com/shopee/modules-federation/live/0/shopee__item-card-standard-v2/0.1.36/pc/5dd7b4560d0e2d3190e8.svg' />
            <span className='ml-1'>TP. Hồ Chí Minh</span>
          </div>
        </div>
      </div>
    </Link>
  )
}
