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

/**
 * Вход: бэкенд отвечает 401 с одинаковым текстом для «нет такого e-mail» и «неверный пароль».
 *
 * Отдельный случай — 403 «e-mail не подтверждён»: учётные данные верные, но аккаунт
 * ещё не активирован PIN-кодом. Таким людям подсказываем ссылку на форму подтверждения,
 * иначе они думают, что пароль неправильный, и начинают его «восстанавливать».
 */
export function LoginPage() {
  const {login} = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [error, setError] = useState<ApiRequestError | null>(null);
  const [busy, setBusy] = useState(false);
  const [email, setEmail] = useState(
    (location.state as {email?: string} | null)?.email ?? '',
  );
  const notice = (location.state as {message?: string} | null)?.message;

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const enteredEmail = String(data.get('email'));
    setBusy(true);
    try {
      await login({email: enteredEmail, password: String(data.get('password'))});
      const from = (location.state as {from?: string} | null)?.from;
      navigate(from ?? '/catalog', {replace: true});
    } catch (err) {
      const apiError = toApiError(err);
      // 403 = аккаунт создан, но PIN-код из письма не введён
      if (apiError.status === 403) {
        setEmail(enteredEmail);
      }
      setError(apiError);
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className='page auth'>
      <form className='form' onSubmit={handleSubmit}>
        <h1 className='page__title'>Вход</h1>
        <ApiErrorBox error={error} fieldNames={FIELD_NAMES} />
        {notice && <p className='form__hint'>{notice}</p>}

        <label className='field'>
          <span>E-mail</span>
          <input
            type='email'
            name='email'
            required
            autoComplete='email'
            placeholder='you@example.com'
            value={email}
            onChange={(event) => setEmail(event.target.value)}
          />
        </label>

        <label className='field'>
          <span>Пароль</span>
          <input type='password' name='password' required autoComplete='current-password' />
        </label>

        <button type='submit' className='btn btn--primary btn--large' disabled={busy}>
          {busy ? 'Проверяем…' : 'войти'}
        </button>

        {error?.status === 403 && (
          <p className='form__hint'>
            <Link to='/verify-email' state={{email}}>
              ввести PIN-код из письма
            </Link>
          </p>
        )}

        <p className='form__hint'>
          Нет аккаунта? <Link to='/register'>зарегистрироваться</Link>
        </p>
      </form>
    </section>
  );
}
