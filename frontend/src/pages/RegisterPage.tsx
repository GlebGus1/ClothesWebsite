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
 * Успешная регистрация сразу логинит: бэкенд возвращает пару токенов.
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
    try {
      await register({
        firstName: String(data.get('firstName')),
        lastName: String(data.get('lastName')),
        email: String(data.get('email')),
        password,
      });
      navigate('/catalog', {replace: true});
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
          Уже есть аккаунт? <Link to='/login'>войти</Link>
        </p>
      </form>
    </section>
  );
}
