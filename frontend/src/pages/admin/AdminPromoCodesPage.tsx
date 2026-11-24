import {useCallback, useEffect, useState} from 'react';
import {
  adminPromoCodes,
  createPromoCode,
  deletePromoCode,
  updatePromoCode,
} from '../../api/promo';
import {toApiError, type ApiRequestError as ApiError} from '../../api/http';
import {Spinner} from '../../components/Spinner';
import {PromoCodeFormPanel} from './PromoCodeFormPanel';
import type {PromoCodeDto, PromoCodeForm} from '../../types/api';
import {dateTime, shortDate} from '../../utils/format';

/**
 * Админка промокодов (/admin/promo-codes).
 *
 * Что здесь происходит: список кодов с сервера, создание/правка в форме ниже,
 * удаление с подтверждением. Ничего «своего» про скидки фронтенд не решает —
 * бэкенд нормализует код (верхний регистр), следит за уникальностью и тем, чтобы
 * скидка была от 1 до 99 процентов.
 *
 * После сохранения показываем код, который вернул сервер: если админ оставил поле
 * пустым, код придумал сервер — его надо увидать и скопировать покупателю.
 */
export function AdminPromoCodesPage() {
  const [codes, setCodes] = useState<PromoCodeDto[] | null>(null);
  const [error, setError] = useState<ApiError | null>(null);
  const [feedback, setFeedback] = useState<string | null>(null);
  // editing === null && formOpen -> создаём новый; editing !== null -> правим существующий
  const [editing, setEditing] = useState<PromoCodeDto | null>(null);
  const [formOpen, setFormOpen] = useState(false);
  const [saving, setSaving] = useState(false);

  const load = useCallback(() => {
    return adminPromoCodes()
      .then((result) => setCodes(result))
      .catch((err) => setError(toApiError(err)));
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  async function handleSubmit(form: PromoCodeForm) {
    setSaving(true);
    setError(null);
    try {
      const saved = editing ? await updatePromoCode(editing.id, form) : await createPromoCode(form);
      setFeedback(`Промокод ${saved.code} сохранён: −${saved.discountPercent}%${saved.active ? '' : ' (код выключен)'}`);
      closeForm();
      await load();
    } catch (err) {
      setError(toApiError(err));
    } finally {
      setSaving(false);
    }
  }

  async function handleDelete(code: PromoCodeDto) {
    if (!window.confirm(`Удалить промокод ${code.code}? Заказы, где он уже применён, не поменяются.`)) {
      return;
    }
    setError(null);
    try {
      await deletePromoCode(code.id);
      setFeedback(`Промокод ${code.code} удалён`);
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

  function openEdit(code: PromoCodeDto) {
    setEditing(code);
    setFormOpen(true);
    setFeedback(null);
  }

  function closeForm() {
    setEditing(null);
    setFormOpen(false);
  }

  if (error && !codes) {
    return <p className='alert alert--error'>{error.message}</p>;
  }
  if (!codes) {
    return <Spinner />;
  }

  return (
    <section className='page'>
      <h1 className='page__title'>Промокоды ({codes.length})</h1>

      {error && <p className='alert alert--error'>{error.message}</p>}
      {feedback && <p className='alert alert--success'>{feedback}</p>}

      <div className='page__actions'>
        <button type='button' className='btn btn--primary' onClick={openCreate} disabled={formOpen}>
          создать промокод
        </button>
      </div>

      {formOpen && (
        // key=... -> при переключении «новый -> правим #5» форма пересоздаётся и
        // поля берут значения нужного кода, а не остаются от прошлой записи
        <PromoCodeFormPanel
          key={editing ? `edit-${editing.id}` : 'create'}
          initial={editing}
          busy={saving}
          onSubmit={handleSubmit}
          onCancel={closeForm}
        />
      )}

      {codes.length === 0 && <p className='empty'>Промокодов пока нет — создайте первый.</p>}

      {codes.length > 0 && (
        <div className='table-wrap'>
          <table className='table'>
            <thead>
              <tr>
                <th>Код</th>
                <th>Скидка</th>
                <th>Срок</th>
                <th>Создан</th>
                <th>Действия</th>
              </tr>
            </thead>
            <tbody>
              {codes.map((code) => (
                <tr key={code.id}>
                  <td>
                    <b>{code.code}</b>{' '}
                    {code.active ? (
                      <span className='badge badge--delivered'>активен</span>
                    ) : (
                      <span className='badge badge--muted'>выключен</span>
                    )}
                  </td>
                  <td>−{code.discountPercent}%</td>
                  <td>{code.expiresAt ? shortDate(code.expiresAt) : 'бессрочно'}</td>
                  <td>{dateTime(code.createdAt)}</td>
                  <td className='table__actions'>
                    <button type='button' className='btn btn--ghost btn--small' onClick={() => openEdit(code)}>
                      редактировать
                    </button>
                    <button type='button' className='btn btn--danger btn--small' onClick={() => handleDelete(code)}>
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
