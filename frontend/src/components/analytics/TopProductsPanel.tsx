import {Link} from 'react-router-dom';
import {imageUrl} from '../../api/http';
import type {TopProductDto} from '../../types/api';
import {money} from '../../utils/format';

/**
 * Топ товаров: либо по просмотрам, либо по добавлениям в избранное.
 *
 * Один компонент на две таблицы намеренно: difference только в подписи метрики.
 * Порядок задаёт сервер (ORDER BY … LIMIT), поэтому фронтенд ничего не сортирует
 * сам — иначе «топ-5» зависело бы от того, какая страница заказов доехала в браузер.
 */
export function TopProductsPanel({
  title,
  items,
  metricLabel,
}: {
  title: string;
  items: TopProductDto[];
  metricLabel: string;
}) {
  return (
    <section className='panel'>
      <h2 className='page__title page__title--small'>{title}</h2>

      {items.length === 0 && <p className='empty'>Данных пока нет.</p>}

      {items.length > 0 && (
        <ul className='ranked'>
          {items.map((item, index) => (
            <li className='ranked__row' key={item.id}>
              <span className='ranked__place'>{index + 1}</span>
              {item.previewImageId ? (
                <img className='ranked__thumb' src={imageUrl(item.previewImageId)} alt={item.name} loading='lazy' />
              ) : (
                <span className='ranked__thumb ranked__thumb--empty' aria-hidden='true' />
              )}
              <span className='ranked__text'>
                <Link to={`/product/${item.id}`}>{item.name}</Link>
                <span className='muted'>
                  размер: {item.size ?? '—'} · {money(item.price)} · {item.available ? 'в продаже' : 'продано'}
                </span>
              </span>
              <span className='ranked__metric'>
                {item.metric} <span className='muted'>{metricLabel}</span>
              </span>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
