import {useState, type FormEvent} from 'react';
import type {CollectionDto, CollectionForm} from '../../types/api';

/**
 * Форма коллекции (дропа): название, описание, цвет бейджа, активность.
 *
 * Цвет — единственный «дизайнерский» параметр, который админ может задать сам,
 * и он же попадает в CSS карточек каталога. Поэтому на бэкенде поле проверено
 * регуляркой ^#?[0-9a-fA-F]{6}$: «название цвета» произвольным текстом быть не
 * может, а значит, его нельзя использовать как вектор для записи в style.
 *
 * Поле active важно для витрины: выключенная коллекция исчезает из чипсов и
 * баннера, но сами товары остаются на месте.
 */
export function CollectionFormPanel({
  initial,
  busy,
  onSubmit,
  onCancel,
}: {
  initial: CollectionDto | null;
  busy: boolean;
  onSubmit: (form: CollectionForm) => void;
  onCancel: () => void;
}) {
  // цвет по умолчанию = цвет бренда из дизайн-токенов, чтобы «новый дроп» не был
  // невидимым, пока админ не выбрал оттенок
  const [color, setColor] = useState(initial?.badgeColor ?? '#b45309');

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    onSubmit({
      name: String(data.get('name') ?? '').trim(),
      description: String(data.get('description') ?? '').trim(),
      badgeColor: color,
      active: data.get('active') === 'on',
    });
  }

  return (
    <form className='form panel' onSubmit={handleSubmit}>
      <h2 className='page__title page__title--small'>
        {initial ? `Коллекция #${initial.id}` : 'Новая коллекция'}
      </h2>

      <div className='form__row'>
        <label className='field'>
          <span>Название</span>
          <input name='name' required maxLength={80} defaultValue={initial?.name ?? ''} placeholder='Summer Drop' />
        </label>
        <label className='field field--narrow'>
          <span>Цвет бейджа</span>
          {/* input[type=color] всегда отдаёт #RRGGBB — ровно тот формат, который ждёт бэкенд */}
          <input type='color' value={color} onChange={(event) => setColor(event.target.value)} />
          <span className='muted'>{color}</span>
        </label>
      </div>

      <label className='field'>
        <span>Описание (показывается в баннере каталога)</span>
        <textarea name='description' rows={3} maxLength={1000} defaultValue={initial?.description ?? ''} />
      </label>

      <label className='checkbox'>
        <input type='checkbox' name='active' defaultChecked={initial?.active ?? true} />
        активна (покупатели видят эту полку в каталоге)
      </label>

      <div className='page__actions'>
        <button type='submit' className='btn btn--primary' disabled={busy}>
          {busy ? 'Сохраняем…' : 'сохранить'}
        </button>
        <button type='button' className='btn btn--ghost' onClick={onCancel} disabled={busy}>
          отмена
        </button>
      </div>
    </form>
  );
}
