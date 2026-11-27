import {useCallback, useEffect, useState} from 'react';
import {
  adminCollections,
  createCollection,
  deleteCollection,
  updateCollection,
} from '../../api/collections';
import {toApiError, type ApiRequestError as ApiError} from '../../api/http';
import {Spinner} from '../../components/Spinner';
import {CollectionFormPanel} from './CollectionFormPanel';
import type {CollectionDto, CollectionForm} from '../../types/api';
import {shortDate} from '../../utils/format';

/**
 * Коллекции (дропы) в админке.
 *
 * Что это вообще такое в базе: таблица `collections`, а у футболки появилась
 * необязательная связь `collection_id`. Необязательная — принципиально: подавляющее
 * большинство товаров существует «вне полок», и удаление коллекции не должно
 * удалять товары (на них уже ссылаются заказы). Поэтому удаление коллекции на бэкенде только
 * «отвязывает» товары (UPDATE tshirts SET collection_id = NULL).
 *
 * Быстрый переключатель «активна» сделан через обычный PUT формы: отдельного
 * эндпоинта для активности нет, и это правильно — форма одна, правила валидации
 * в одном месте (название обязательное, цвет — HEX).
 */
export function AdminCollectionsPage() {
  const [collections, setCollections] = useState<CollectionDto[] | null>(null);
  const [error, setError] = useState<ApiError | null>(null);
  const [feedback, setFeedback] = useState<string | null>(null);
  const [editing, setEditing] = useState<CollectionDto | null>(null);
  const [formOpen, setFormOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [busyId, setBusyId] = useState<number | null>(null);

  const load = useCallback(() => {
    return adminCollections()
      .then(setCollections)
      .catch((err) => setError(toApiError(err)));
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  function toForm(collection: CollectionDto): CollectionForm {
    return {
      name: collection.name,
      description: collection.description ?? '',
      badgeColor: collection.badgeColor ?? '#b45309',
      active: collection.active,
    };
  }

  async function handleSubmit(form: CollectionForm) {
    setSaving(true);
    setError(null);
    try {
      const saved = editing
        ? await updateCollection(editing.id, form)
        : await createCollection(form);
      setFeedback(`Коллекция «${saved.name}» сохранена (товаров на полке: ${saved.productsCount})`);
      closeForm();
      await load();
    } catch (err) {
      setError(toApiError(err));
    } finally {
      setSaving(false);
    }
  }

  async function handleToggleActive(collection: CollectionDto) {
    setBusyId(collection.id);
    setError(null);
    try {
      await updateCollection(collection.id, {...toForm(collection), active: !collection.active});
      setFeedback(`Коллекция «${collection.name}» ${collection.active ? 'скрыта от покупателей' : 'снова видна в каталоге'}`);
      await load();
    } catch (err) {
      setError(toApiError(err));
    } finally {
      setBusyId(null);
    }
  }

  async function handleDelete(collection: CollectionDto) {
    if (
      !window.confirm(
        `Удалить коллекцию «${collection.name}»? Её товары (${collection.productsCount}) останутся в каталоге — ` +
          'просто перестанут относиться к дропу.',
      )
    ) {
      return;
    }
    setError(null);
    try {
      await deleteCollection(collection.id);
      setFeedback(`Коллекция «${collection.name}» удалена, товары остались`);
      if (editing?.id === collection.id) {
        closeForm();
      }
      await load();
    } catch (err) {
      setError(toApiError(err));
    }
  }

  function openCreate() {
    setEditing(null);
    setFormOpen(true);
    setFeedback(null);
  }

  function openEdit(collection: CollectionDto) {
    setEditing(collection);
    setFormOpen(true);
    setFeedback(null);
  }

  function closeForm() {
    setEditing(null);
    setFormOpen(false);
  }

  if (error && !collections) {
    return <p className='alert alert--error'>{error.message}</p>;
  }
  if (!collections) {
    return <Spinner />;
  }

  return (
    <section className='page'>
      <h1 className='page__title'>Коллекции и дропы ({collections.length})</h1>

      {error && <p className='alert alert--error'>{error.message}</p>}
      {feedback && <p className='alert alert--success'>{feedback}</p>}

      <div className='page__actions'>
        <button type='button' className='btn btn--primary' onClick={openCreate} disabled={formOpen}>
          создать коллекцию
        </button>
      </div>

      {formOpen && (
        // key -> при переключении «новая -> правим #3» форма пересоздаётся и поля
        // берут значения нужной коллекции, а не остаются от прошлой записи
        <CollectionFormPanel
          key={editing ? `edit-${editing.id}` : 'create'}
          initial={editing}
          busy={saving}
          onSubmit={handleSubmit}
          onCancel={closeForm}
        />
      )}

      {collections.length === 0 && <p className='empty'>Коллекций нет — создайте первую, и в каталоге появится баннер с дропами.</p>}

      {collections.length > 0 && (
        <div className='table-wrap'>
          <table className='table'>
            <thead>
              <tr>
                <th>Коллекция</th>
                <th>Описание</th>
                <th>Товаров</th>
                <th>Создана</th>
                <th>Витрина</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {collections.map((collection) => (
                <tr key={collection.id} className={collection.active ? '' : 'is-muted'}>
                  <td>
                    <span className='dot' style={{background: collection.badgeColor ?? 'var(--color-primary)'}} aria-hidden='true' />
                    <b>{collection.name}</b>
                    <div className='muted'>{collection.badgeColor ?? 'цвет по умолчанию'}</div>
                  </td>
                  <td>{collection.description || <span className='muted'>—</span>}</td>
                  <td>{collection.productsCount}</td>
                  <td>{shortDate(collection.createdAt)}</td>
                  <td>
                    {collection.active ? (
                      <span className='badge badge--delivered'>показывается</span>
                    ) : (
                      <span className='badge badge--muted'>скрыта</span>
                    )}
                  </td>
                  <td className='table__actions'>
                    <button type='button' className='btn btn--ghost btn--small' onClick={() => openEdit(collection)}>
                      редактировать
                    </button>
                    <button
                      type='button'
                      className='btn btn--small btn--primary'
                      onClick={() => handleToggleActive(collection)}
                      disabled={busyId === collection.id}
                      title={collection.active ? 'Скрыть полку от покупателей' : 'Показать полку в каталоге'}
                    >
                      {collection.active ? 'скрыть' : 'показать'}
                    </button>
                    <button type='button' className='btn btn--danger btn--small' onClick={() => handleDelete(collection)}>
                      удалить
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
