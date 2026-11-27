import {useCallback, useEffect, useState} from 'react';
import {Link, useSearchParams} from 'react-router-dom';
import {adminTShirts, hideTShirt, restoreTShirt, setAvailability} from '../../api/adminProducts';
import {adminCollections} from '../../api/collections';
import {imageUrl, toApiError, type ApiRequestError} from '../../api/http';
import {Pagination} from '../../components/Pagination';
import {Spinner} from '../../components/Spinner';
import type {AdminTShirtDto, CollectionDto, PageResponse} from '../../types/api';
import {money} from '../../utils/format';

/**
 * Таблица «Товары (админ)».
 *
 * Читается отдельный эндпоинт /api/admin/tshirts (а не публичный каталог):
 * только там есть viewsCount, «в каком заказе ушла вещь» и снятые с продажи позиции.
 *
 * Быстрое переключатель «в продаже» слева в строке — это PATCH
 * /api/admin/tshirts/{id}/availability: админу нельзя лезть в форму товара, чтобы
 * снять/вернуть одну галочку. Форма остаётся для правок цены, описания и фото.
 */
export function AdminProductsPage() {
  const [page, setPage] = useState<PageResponse<AdminTShirtDto> | null>(null);
  const [collections, setCollections] = useState<CollectionDto[]>([]);
  const [showDisabled, setShowDisabled] = useState(false);
  // выбранная коллекция живёт в URL (?collection=3): так ссылка с названием коллекции
  // в строке таблицы работает как обычная ссылка, и её можно скопировать/открыть
  const [searchParams, setSearchParams] = useSearchParams();
  const collectionId = numberOf(searchParams.get('collection'));
  const [pageNumber, setPageNumber] = useState(0);
  const [error, setError] = useState<ApiRequestError | null>(null);
  const [feedback, setFeedback] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<number | null>(null);

  const load = useCallback(() => {
    setError(null);
    return adminTShirts({
      page: pageNumber,
      pageSize: 20,
      includeDisabled: showDisabled,
      collectionId: collectionId ?? undefined,
    })
      .then(setPage)
      .catch((err) => setError(toApiError(err)));
  }, [pageNumber, showDisabled, collectionId]);

  useEffect(() => {
    adminCollections()
      .then(setCollections)
      .catch(() => setCollections([]));
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  function patchRow(id: number, patch: Partial<AdminTShirtDto>) {
    setPage((current) =>
      current ? {...current, content: current.content.map((row) => (row.id === id ? {...row, ...patch} : row))} : current,
    );
  }

  async function toggleAvailability(id: number, nextAvailable: boolean) {
    setBusyId(id);
    setError(null);
    try {
      await setAvailability(id, nextAvailable);
      patchRow(id, {available: nextAvailable});
      setFeedback(`Товар #${id}: ${nextAvailable ? 'снова в продаже' : 'снят с продажи'}`);
    } catch (err) {
      setError(toApiError(err));
    } finally {
      setBusyId(null);
    }
  }

  async function handleHide(id: number) {
    if (!window.confirm(`Скрыть товар #${id} из каталога? История заказов сохранится.`)) {
      return;
    }
    setBusyId(id);
    try {
      await hideTShirt(id);
      patchRow(id, {disabled: true, available: false});
      setFeedback(`Товар #${id} скрыт из каталога (заказы остались)`);
    } catch (err) {
      setError(toApiError(err));
    } finally {
      setBusyId(null);
    }
  }

  async function handleRestore(id: number) {
    setBusyId(id);
    setError(null);
    try {
      const restored = await restoreTShirt(id);
      patchRow(id, {disabled: false, available: restored.available});
      setFeedback(`Товар #${restored.id} снова в продаже`);
    } catch (err) {
      setError(toApiError(err));
    } finally {
      setBusyId(null);
    }
  }

  return (
    <section className='page'>
      <h1 className='page__title'>Товары (админ)</h1>

      <div className='toolbar'>
        <Link to='/admin/products/edit/new' className='btn btn--primary'>
          + добавить товар
        </Link>
        <label className='checkbox'>
          <input type='checkbox' checked={showDisabled} onChange={(event) => setShowDisabled(event.target.checked)} />
          показывать скрытые
        </label>
        <label className='field field--inline'>
          <span>Коллекция</span>
          <select
            value={collectionId ?? ''}
            onChange={(event) => {
              setPageNumber(0);
              const next = new URLSearchParams(searchParams);
              if (event.target.value === '') {
                next.delete('collection');
              } else {
                next.set('collection', event.target.value);
              }
              setSearchParams(next);
            }}
          >
            <option value=''>все</option>
            {collections.map((collection) => (
              <option key={collection.id} value={collection.id}>
                {collection.name} ({collection.productsCount})
              </option>
            ))}
          </select>
        </label>
      </div>

      {error && <p className='alert alert--error'>{error.message}</p>}
      {feedback && <p className='alert alert--success'>{feedback}</p>}
      {!page && <Spinner />}

      {page && (
        <div className='table-wrap'>
          <table className='table'>
            <thead>
              <tr>
                <th>id</th>
                <th>фото</th>
                <th>название / коллекция</th>
                <th>размер</th>
                <th>цена</th>
                <th title='Уникальные просмотры карточки товара'>просмотры</th>
                <th>в продаже</th>
                <th>продан в</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {page.content.map((product) => (
                <tr key={product.id} className={product.disabled ? 'is-muted' : ''}>
                  <td>{product.id}</td>
                  <td>
                    {product.previewImageId ? (
                      <img className='table__thumb' src={imageUrl(product.previewImageId)} alt={product.name} loading='lazy' />
                    ) : (
                      '—'
                    )}
                  </td>
                  <td>
                    <Link to={`/product/${product.id}`}>{product.name}</Link>
                    {product.collectionName && (
                      <div className='muted'>
                        <Link to={`/admin/products?collection=${product.collectionId}`}>{product.collectionName}</Link>
                      </div>
                    )}
                  </td>
                  <td>{product.size ?? '—'}</td>
                  <td>{money(product.price)}</td>
                  <td>{product.viewsCount}</td>
                  <td>
                    {/* быстрое переключение доступности: PATCH без открытия формы товара */}
                    <label className='switch' title='Снять/вернуть в продажу'>
                      <input
                        type='checkbox'
                        checked={product.available && !product.disabled}
                        disabled={busyId === product.id || product.disabled}
                        onChange={(event) => toggleAvailability(product.id, event.target.checked)}
                      />
                      <span>
                        {product.disabled ? (
                          <span className='badge badge--muted'>скрыт</span>
                        ) : product.available ? (
                          <span className='badge badge--delivered'>в продаже</span>
                        ) : (
                          <span className='badge badge--sold'>не продаётся</span>
                        )}
                      </span>
                    </label>
                  </td>
                  <td>
                    {product.soldInOrderId ? (
                      // ссылка на заказ: из товара в заказ и обратно (в заказах есть ссылка на товар)
                      <Link className='badge badge--order' to={`/orders/${product.soldInOrderId}`}>
                        Заказ #{product.soldInOrderId}
                      </Link>
                    ) : (
                      <span className='muted'>—</span>
                    )}
                  </td>
                  <td className='table__actions'>
                    <Link className='btn btn--ghost btn--small' to={`/admin/products/edit/${product.id}`}>
                      изменить
                    </Link>
                    {product.disabled ? (
                      <button
                        type='button'
                        className='btn btn--primary btn--small'
                        onClick={() => handleRestore(product.id)}
                        disabled={busyId === product.id}
                      >
                        {busyId === product.id ? 'Возвращаем…' : 'вернуть в продажу'}
                      </button>
                    ) : (
                      <button
                        type='button'
                        className='btn btn--danger btn--small'
                        onClick={() => handleHide(product.id)}
                        disabled={busyId === product.id}
                      >
                        скрыть
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {page && page.content.length === 0 && <p className='empty'>В этой выборке нет товаров.</p>}
      {page && <Pagination page={page} onChange={setPageNumber} />}
    </section>
  );
}

/** Значение из URL: пусто или мусор -> null (NaN в запрос улетел бы строкой 'NaN'). */
function numberOf(raw: string | null): number | null {
  const value = Number(raw);
  return raw && Number.isFinite(value) && value > 0 ? value : null;
}
