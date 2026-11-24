import {useState, type FormEvent} from 'react';
import {Link, useNavigate} from 'react-router-dom';
import {ApiErrorBox} from '../components/ApiErrorBox';
import {useAuth} from '../context/AuthContext';
import {ApiRequestError, toApiError, type ApiRequestError as ApiError} from '../api/http';

const FIELD_NAMES: Record<string, string> = {
  email: 'E-mail',
  password: 'Пароль',
  firstName: 'Имя',
  lastName: 'Фамилия',
};

/**
 * Регистрация. Правила длины поля заданы на бэкенде (аннотации в RegisterRequest),
 * поэтому тексты ошибок приходит сразу в ApiError.fields — фронтенд их только печатает.
 *
 * ВАЖНО: регистрация больше НЕ логинит. Бэкенд создаёт аккаунт со статусом
 * «не подтверждён» и отправляет 6-значный PIN-код, поэтому нас ждут на /verify-email.
 * Токенов у пользователя нет, пока он не введёт этот PIN-код.
 */
export function RegisterPage() {
  const {register} = useAuth();
  const navigate = useNavigate();
  const [error, setError] = useState<ApiError | null>(null);
  const [busy, setBusy] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const data = new FormData(form);
    const password = String(data.get('password'));
    const repeat = String(data.get('passwordRepeat'));

    if (password !== repeat) {
      setError(new ApiRequestError(undefined, 0, 'Проверьте данные формы', {passwordRepeat: 'Пароли не совпадают'}));
      return;
    }

    setBusy(true);
    const email = String(data.get('email'));
    try {
      const status = await register({
        firstName: String(data.get('firstName')),
        lastName: String(data.get('lastName')),
        email,
        password,
      });
      if (status.requiresVerification) {
        // передаём e-mail состоянием маршрута — форма PIN-кода не должна спрашивать его заново
        navigate('/verify-email', {replace: true, state: {email: status.email, message: status.message}});
      } else {
        // на сервере подтверждение отключено (app.verification.enabled=false) — сразу на вход
        navigate('/login', {replace: true, state: {email, message: status.message}});
      }
    } catch (err) {
      setError(toApiError(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className='page auth'>
      <form className='form' onSubmit={handleSubmit}>
        <h1 className='page__title'>Регистрация</h1>
        <ApiErrorBox error={error} fieldNames={FIELD_NAMES} />

        <div className='form__row'>
          <label className='field'>
            <span>Имя</span>
            <input name='firstName' required maxLength={20} autoComplete='given-name' />
          </label>
          <label className='field'>
            <span>Фамилия</span>
            <input name='lastName' required maxLength={20} autoComplete='family-name' />
          </label>
        </div>

        <label className='field'>
          <span>E-mail</span>
          <input type='email' name='email' required autoComplete='email' placeholder='you@example.com' />
        </label>

        <label className='field'>
          <span>Пароль (минимум 6 символов)</span>
          <input type='password' name='password' required minLength={6} autoComplete='new-password' />
        </label>

        <label className='field'>
          <span>Повторите пароль</span>
          <input type='password' name='passwordRepeat' required minLength={6} autoComplete='new-password' />
        </label>

        <button type='submit' className='btn btn--primary btn--large' disabled={busy}>
          {busy ? 'Создаём аккаунт…' : 'зарегистрироваться'}
        </button>

        <p className='form__hint'>
          После регистрации мы отправим на почту 6-значный PIN-код: вход откроется только
          после его ввода. Если письмо не пришло, а сервер работает локально — PIN-код
          напечатан в консоли бэкенда.
        </p>

        <p className='form__hint'>
          Уже есть аккаунт? <Link to='/login'>войти</Link>
        </p>
      </form>
    </section>
  );
}
