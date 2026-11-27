import {Link} from 'react-router-dom';
import {imageUrl} from '../api/http';
import type {OrderItemDto} from '../types/api';
import {money} from '../utils/format';

/**
 * Список позиций заказа мини-карточками: обложка, название, размер, цена.
 *
 * Раньше в таблице заказов админа позиции были одной строкой через запятую — по ней
 * невозможно понять, о какой именно вещи речь и где её править. Теперь у позиции есть
 * обложка (её принёс сервер в OrderDto.Item.previewImageId) и ссылка:
 *  - variant="admin"    → на форму товара (админу править),
 *  - variant="customer" → на карточку товара (покупателю смотреть).
 *
 * Один компонент на два места намеренно: разница только в адресе ссылки, а вёрстка
 * и формат цены должны быть одинаковыми, чтобы «одна и та же позиция» в заказах
 * покупателя и администратора выглядела идентично.
 */
export function OrderItemsList({items, variant = 'customer'}: {items: OrderItemDto[]; variant?: 'admin' | 'customer'}) {
  if (items.length === 0) {
    // заказ без позиций — легаси-строка из старой базы: не падаем, показываем текст
    return <span className='muted'>позиций нет</span>;
  }

  return (
    <ul className='order-items order-items--cards'>
      {items.map((item) => (
        <li className='order-item' key={`${item.id}-${item.name}`}>
          {item.previewImageId ? (
            <img className='order-item__thumb' src={imageUrl(item.previewImageId)} alt={item.name} loading='lazy' />
          ) : (
            <span className='order-item__thumb order-item__thumb--empty' aria-hidden='true' />
          )}
          <span className='order-item__text'>
            {variant === 'admin' ? (
              <Link to={`/admin/products/edit/${item.id}`}>{item.name}</Link>
            ) : (
              <Link to={`/product/${item.id}`}>{item.name}</Link>
            )}
            <span className='muted'>
              размер: {item.size ?? '—'} · {money(item.price)}
            </span>
          </span>
        </li>
      ))}
    </ul>
  );
}
