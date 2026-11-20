import {useState, type FormEvent} from 'react';
import {Link, useLocation, useNavigate} from 'react-router-dom';
import {ApiErrorBox} from '../components/ApiErrorBox';
import {useAuth} from '../context/AuthContext';
import {toApiError, type ApiRequestError} from '../api/http';

const FIELD_NAMES: Record<string, string> = {
  email: 'E-mail',
  password: 'Пароль',
  firstName: 'Имя',
  lastName: 'Фамилия',
};

/** Вход: бэкенд отвечает 401 с одинаковым текстом для «нет такого e-mail» и «неверный пароль». */
export function LoginPage() {
  const {login} = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [error, setError] = useState<ApiRequestError | null>(null);
  const [busy, setBusy] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    setBusy(true);
    try {
      await login({email: String(data.get('email')), password: String(data.get('password'))});
      const from = (location.state as {from?: string} | null)?.from;
      navigate(from ?? '/catalog', {replace: true});
    } catch (err) {
      setError(toApiError(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className='page auth'>
      <form className='form' onSubmit={handleSubmit}>
        <h1 className='page__title'>Вход</h1>
        <ApiErrorBox error={error} fieldNames={FIELD_NAMES} />

        <label className='field'>
          <span>E-mail</span>
          <input type='email' name='email' required autoComplete='email' placeholder='you@example.com' />
        </label>

        <label className='field'>
          <span>Пароль</span>
          <input type='password' name='password' required autoComplete='current-password' />
        </label>

        <button type='submit' className='btn btn--primary btn--large' disabled={busy}>
          {busy ? 'Проверяем…' : 'войти'}
        </button>

        <p className='form__hint'>
          Нет аккаунта? <Link to='/register'>зарегистрироваться</Link>
        </p>
      </form>
    </section>
  );
}
