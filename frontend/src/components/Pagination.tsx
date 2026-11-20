import type {PageResponse} from '../types/api';

/** Постраничная навигация. Бэкенд отдаёт page/totalPages — рисуем по ним. */
export function Pagination({page, onChange}: {page: PageResponse<unknown>; onChange: (page: number) => void}) {
  if (page.totalPages <= 1) {
    return null;
  }

  const pages = Array.from({length: page.totalPages}, (_, index) => index);
  const window = pages.filter((index) => Math.abs(index - page.page) <= 2);

  return (
    <nav className='pagination' aria-label='Страницы'>
      <button type='button' className='btn btn--ghost' disabled={page.page === 0} onClick={() => onChange(page.page - 1)}>
        ←
      </button>
      {window[0] > 0 && <span className='pagination__dots'>…</span>}
      {window.map((index) => (
        <button
          key={index}
          type='button'
          className={`pagination__page ${index === page.page ? 'is-active' : ''}`}
          onClick={() => onChange(index)}
        >
          {index + 1}
        </button>
      ))}
      {window[window.length - 1] < page.totalPages - 1 && <span className='pagination__dots'>…</span>}
      <button
        type='button'
        className='btn btn--ghost'
        disabled={page.last}
        onClick={() => onChange(page.page + 1)}
      >
        →
      </button>
      <span className='pagination__info'>всего {page.totalElements}</span>
    </nav>
  );
}
