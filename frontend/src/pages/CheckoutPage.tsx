import {useState, type FormEvent} from 'react';
import {Link, useNavigate} from 'react-router-dom';
import {createOrder} from '../api/orders';
import {ApiRequestError, toApiError, type ApiRequestError as ApiError} from '../api/http';
import {ApiErrorBox} from '../components/ApiErrorBox';
import {useAuth} from '../context/AuthContext';
import {useCart} from '../context/CartContext';
import {money} from '../utils/format';

const FIELD_NAMES: Record<string, string> = {
  nameOfUser: 'Имя получателя',
  postOffice: 'Отделение',
  postalCode: 'Индекс',
  phoneNumber: 'Телефон',
  tshirtIds: 'Корзина',
};

/**
 * Оформление заказа. Пользователь берётся из токена (в запросе его e-mail не передаём),
 * поэтому подделать «заказ на чужого пользователя» со фронтенда невозможно.
 * Ошибки валидации (индекс, телефон) приходят с бэкенда в ApiError.fields.
 */
export function CheckoutPage() {
  const {user} = useAuth();
  const {items, total, clear, count} = useCart();
  const navigate = useNavigate();
  const [error, setError] = useState<ApiError | null>(null);
  const [busy, setBusy] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (count === 0) {
      setError(new ApiRequestError(undefined, 0, 'Корзина пуста', {tshirtIds: 'добавьте хотя бы один товар'}));
      return;
    }

    const data = new FormData(event.currentTarget);
    setBusy(true);
    try {
      const order = await createOrder({
        nameOfUser: String(data.get('nameOfUser')),
        postOffice: String(data.get('postOffice')),
        postalCode: String(data.get('postalCode')),
        phoneNumber: String(data.get('phoneNumber')),
        tshirtIds: items.map((item) => item.id),
      });
      clear();
      navigate(`/orders/${order.id}`, {state: {created: true}});
    } catch (err) {
      setError(toApiError(err));
    } finally {
      setBusy(false);
    }
  }

  if (count === 0) {
    return (
      <section className='page'>
        <h1 className='page__title'>Оформление заказа</h1>
        <p className='empty'>
          Сначала добавь товары. <Link to='/catalog'>в каталог</Link>
        </p>
      </section>
    );
  }

  return (
    <section className='page'>
      <h1 className='page__title'>Оформление заказа</h1>

      <div className='checkout'>
        <form className='form' onSubmit={handleSubmit}>
          <ApiErrorBox error={error} fieldNames={FIELD_NAMES} />

          <label className='field'>
            <span>Имя получателя</span>
            <input name='nameOfUser' required defaultValue={user ? `${user.firstName} ${user.lastName}`.trim() : ''} />
          </label>

          <label className='field'>
            <span>Отделение получения</span>
            <input name='postOffice' required placeholder='Отделение 12, г. Киев' />
          </label>

          <div className='form__row'>
            <label className='field'>
              <span>Почтовый индекс (4–10 цифр)</span>
              <input name='postalCode' required pattern='\d{4,10}' inputMode='numeric' placeholder='70100' />
            </label>
            <label className='field'>
              <span>Телефон (7–15 цифр, можно с +)</span>
              <input name='phoneNumber' required pattern='\+?\d{7,15}' inputMode='tel' placeholder='+380631234567' />
            </label>
          </div>

          <button type='submit' className='btn btn--primary btn--large' disabled={busy}>
            {busy ? 'Отправляем…' : `заказ на ${money(total)}`}
          </button>
        </form>

        <aside className='checkout__summary'>
          <h2>Товары</h2>
          <ul>
            {items.map((item) => (
              <li key={item.id}>
                {item.name} <span>({item.size ?? '—'})</span> — {money(item.price)}
              </li>
            ))}
          </ul>
          <p className='checkout__total'>
            Итого: <b>{money(total)}</b>
          </p>
          <p className='form__hint'>Итог пересчитывается на сервере по ценам из базы данных.</p>
        </aside>
      </div>
    </section>
  );
}
