import type {CSSProperties} from 'react';
import type {CollectionDto} from '../types/api';

/**
 * Баннер + чипсы активных коллекций (дропов) над каталогом.
 *
 * Что это даёт покупателю: дроп — это «полка с истёкшим сроком». Товар в ней
 * один экземпляр, поэтому показать «летний дроп, осталось 4 вещи» полезнее,
 * чем заставлять человека гадать по названиям.
 *
 * Как устроено выбранное состояние: активный чипс приходит из URL
 * (?collectionId=3), а не из локального useState — каталог у нас уже живёт
 * в query-параметрах (размер, сортировка, страница), поэтому ссылка
 * «летний дроп + размер M + 2-я страница» остаётся делимой и рабочей кнопкой «назад».
 *
 * Цвет бейджа берётся из БД (Collection.badgeColor). Это единственный place,
 * где цвет из базы попадает в inline-стиль, и бэкенд проверяет формат регуляркой
 * ^#?[0-9a-fA-F]{6}$ — иначе через «название цвета» можно было бы засунуть
 * произвольный текст в атрибут style.
 */
export function CollectionsStrip({
  collections,
  activeId,
  onSelect,
}: {
  collections: CollectionDto[];
  activeId: number | null;
  onSelect: (id: number | null) => void;
}) {
  if (collections.length === 0) {
    // полок нет — блок не занимаем место вовсе (иначе в каталоге «дырка»)
    return null;
  }

  const active = activeId === null ? null : collections.find((item) => item.id === activeId) ?? null;

  return (
    <section className='collections' aria-label='Коллекции и дропы'>
      <div className='collections__banner' style={accentStyle(active?.badgeColor ?? collections[0]?.badgeColor)}>
        <div>
          <h2 className='collections__title'>{active ? active.name : 'Дропы этого сезона'}</h2>
          <p className='collections__note'>
            {active
              ? active.description || 'Вещи из этого дропа — по одному экземпляру: что забрал, то забрал.'
              : 'Собираем футболки тематическими партиями. Выберите полку — покажем только её.'}
          </p>
        </div>
        {active && (
          <span className='collections__count'>
            товаров: {active.productsCount}
            {active.productsCount === 0 && ' — полка уже опустела'}
          </span>
        )}
      </div>

      <div className='collections__chips' role='group' aria-label='Фильтр по коллекции'>
        <button
          type='button'
          className={`chip ${activeId === null ? 'is-active' : ''}`}
          onClick={() => onSelect(null)}
          aria-pressed={activeId === null}
        >
          все коллекции
        </button>
        {collections.map((collection) => (
          <button
            key={collection.id}
            type='button'
            className={`chip ${activeId === collection.id ? 'is-active' : ''}`}
            style={accentStyle(collection.badgeColor)}
            onClick={() => onSelect(collection.id)}
            aria-pressed={activeId === collection.id}
          >
            <span className='chip__dot' aria-hidden='true' />
            {collection.name}
            <span className='chip__count'>{collection.productsCount}</span>
          </button>
        ))}
      </div>
    </section>
  );
}

/**
 * CSS-переменная акцента. Значение — только HEX из БД (проверен регуляркой),
 * поэтому подстановка в стиль безопасна.
 */
function accentStyle(color: string | null | undefined): CSSProperties | undefined {
  // каст к CSSProperties нужен: React не знает про произвольные CSS-переменные
  // в типизации inline-стилей, хотя браузер их понимает полностью
  return color ? ({['--accent']: color} as CSSProperties) : undefined;
}
