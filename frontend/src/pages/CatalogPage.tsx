import {useEffect, useMemo, useState} from 'react';
import {useSearchParams} from 'react-router-dom';
import {listSizes, listTShirts} from '../api/catalog';
import {toApiError, type ApiRequestError} from '../api/http';
import {Pagination} from '../components/Pagination';
import {ProductCard} from '../components/ProductCard';
import {Spinner} from '../components/Spinner';
import {SORT_OPTIONS, type CatalogQuery, type PageResponse, type SortKey, type TShirtDto} from '../types/api';

const PAGE_SIZE = 12;

/**
 * Каталог: фильтры (размер, наличие, сортировка) живут в query-параметрах URL.
 * Это плюс для UX: ссылку на «M + цена по возрастанию + 2-я страница» можно
 * сохранить или отправить другу, а «назад» в браузере работает как обычно.
 */
export function CatalogPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [page, setPage] = useState<PageResponse<TShirtDto> | null>(null);
  const [sizes, setSizes] = useState<string[]>([]);
  const [error, setError] = useState<ApiRequestError | null>(null);
  const [loading, setLoading] = useState(true);

  const query = useMemo<CatalogQuery>(
    () => ({
      size: searchParams.get('size') ?? undefined,
      available: searchParams.get('available') === 'true' ? true : undefined,
      sort: (searchParams.get('sort') as SortKey | null) ?? undefined,
      page: Number(searchParams.get('page') ?? 0),
      pageSize: PAGE_SIZE,
    }),
    [searchParams],
  );

  useEffect(() => {
    listSizes()
      .then(setSizes)
      .catch(() => setSizes([]));
  }, []);

  useEffect(() => {
    let alive = true;
    setLoading(true);
    listTShirts(query)
      .then((result) => {
        if (alive) {
          setPage(result);
          setError(null);
        }
      })
      .catch((err) => {
        if (alive) {
          setError(toApiError(err));
        }
      })
      .finally(() => {
        if (alive) {
          setLoading(false);
        }
      });
    // отменяем «установку» результата, если пользователь уже ушёл на другую страницу
    return () => {
      alive = false;
    };
  }, [query]);

  function patchParams(patch: Record<string, string | null>) {
    const next = new URLSearchParams(searchParams);
    Object.entries(patch).forEach(([key, value]) => {
      if (value === null || value === '') {
        next.delete(key);
      } else {
        next.set(key, value);
      }
    });
    next.delete('page'); // любой новый фильтр начинает с первой страницы
    setSearchParams(next);
  }

  return (
    <section className='page'>
      <h1 className='page__title'>Каталог</h1>

      <form
        className='filters'
        onSubmit={(event) => {
          event.preventDefault();
          const data = new FormData(event.currentTarget);
          patchParams({
            size: String(data.get('size') ?? ''),
            available: String(data.get('available') ?? ''),
            sort: String(data.get('sort') ?? ''),
          });
        }}
      >
        <label className='field'>
          <span>Размер</span>
          <select name='size' defaultValue={query.size ?? ''}>
            <option value=''>любой</option>
            {sizes.map((size) => (
              <option key={size} value={size}>
                {size}
              </option>
            ))}
          </select>
        </label>

        <label className='field'>
          <span>Наличие</span>
          <select name='available' defaultValue={query.available ? 'true' : ''}>
            <option value=''>все</option>
            <option value='true'>в наличии</option>
          </select>
        </label>

        <label className='field'>
          <span>Сортировка</span>
          <select name='sort' defaultValue={query.sort ?? ''}>
            {SORT_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </label>

        <button type='submit' className='btn btn--primary'>
          Применить
        </button>
      </form>

      {error && <p className='alert alert--error'>{error.message}</p>}
      {loading && <Spinner />}

      {!loading && page && page.content.length === 0 && (
        <p className='empty'>По запросу ничего не найдено. Попробуйте сбросить фильтры.</p>
      )}

      {!loading && page && <div className='grid'>{page.content.map((product) => <ProductCard key={product.id} product={product} />)}</div>}
      {page && <Pagination page={page} onChange={(next) => patchParams({page: String(next)})} />}
    </section>
  );
}
