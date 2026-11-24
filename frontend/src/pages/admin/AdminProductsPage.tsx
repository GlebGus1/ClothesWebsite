import {useCallback, useEffect, useState} from 'react';
import {Link} from 'react-router-dom';
import {deleteTShirt, listTShirts, restoreTShirt} from '../../api/catalog';
import {imageUrl, toApiError, type ApiRequestError} from '../../api/http';
import {Pagination} from '../../components/Pagination';
import {Spinner} from '../../components/Spinner';
import type {PageResponse, TShirtDto} from '../../types/api';
import {money} from '../../utils/format';

/**
 * Админ: список товаров. Флаг includeDisabled=true показывает и «удалённые»
 * (мягко удалённые) позиции — бэкенд учитывает его только для роли ADMIN.
 *
 * Для скрытой позиции кнопка «скрыть» НЕ показываем — вместо неё «вернуть в
 * продажу» (PATCH /api/admin/tshirts/{id}/restore): иначе снятую с продажи вещь
 * было бы нечем вернуть обратно, кроме как через форму товара.
 */
export function AdminProductsPage() {
  const [page, setPage] = useState<PageResponse<TShirtDto> | null>(null);
  const [showDisabled, setShowDisabled] = useState(false);
  const [pageNumber, setPageNumber] = useState(0);
  const [error, setError] = useState<ApiRequestError | null>(null);
  const [feedback, setFeedback] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<number | null>(null);

  const load = useCallback(() => {
    setError(null);
    return listTShirts({page: pageNumber, pageSize: 20, includeDisabled: showDisabled})
      .then(setPage)
      .catch((err) => setError(toApiError(err)));
  }, [pageNumber, showDisabled]);

  useEffect(() => {
    void load();
  }, [load]);

  async function handleDelete(id: number) {
    if (!window.confirm(`Скрыть товар #${id} из каталога? История заказов сохранится.`)) {
      return;
    }
    setBusyId(id);
    try {
      await deleteTShirt(id);
      // если скрытые позиции сейчас показаны, запись остаётся в списке, но
      // помечается как скрытая; при showDisabled=false её просто убираем
      setPage((current) => {
        if (!current) {
          return current;
        }
        const content = showDisabled
          ? current.content.map((item) => (item.id === id ? {...item, disabled: true, available: false} : item))
          : current.content.filter((item) => item.id !== id);
        return {...current, content};
      });
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
      setPage((current) => {
        if (!current) {
          return current;
        }
        // товар возвращается в продажу: is_disabled=false, available=true
        const content = current.content.map((item) =>
          item.id === id ? {...item, disabled: false, available: true} : item,
        );
        return {...current, content};
      });
      setFeedback(`Товар #${restored.id} снова в продаже`);
    } catch (err) {
      setError(toApiError(err));
    } finally {
      setBusyId(null);
    }
  }

  if (!page) {
    return <Spinner />;
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
      </div>

      {error && <p className='alert alert--error'>{error.message}</p>}
      {feedback && <p className='alert alert--success'>{feedback}</p>}

      <div className='table-wrap'>
        <table className='table'>
          <thead>
            <tr>
              <th>id</th>
              <th>фото</th>
              <th>название</th>
              <th>размер</th>
              <th>цена</th>
              <th>статус</th>
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
                </td>
                <td>{product.size ?? '—'}</td>
                <td>{money(product.price)}</td>
                <td>
                  {product.disabled ? (
                    <span className='badge badge--muted'>скрыт</span>
                  ) : product.available ? (
                    <span className='badge badge--delivered'>в продаже</span>
                  ) : (
                    // available=false — либо вещь купили (OrderService сам снимает с продажи),
                    // либо её снял админ галочкой в форме товара
                    <span className='badge badge--sold'>не продаётся</span>
                  )}
                </td>
                <td className='table__actions'>
                  <Link className='btn btn--ghost btn--small' to={`/admin/products/edit/${product.id}`}>
                    изменить
                  </Link>
                  {product.disabled ? (
                    // скрытую позицию нельзя «скрыть» ещё раз — только вернуть
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
                      onClick={() => handleDelete(product.id)}
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

      <Pagination page={page} onChange={setPageNumber} />
    </section>
  );
}
