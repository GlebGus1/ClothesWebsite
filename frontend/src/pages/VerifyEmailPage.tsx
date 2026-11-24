import {useState, type FormEvent} from 'react';
import {Link, useLocation, useNavigate} from 'react-router-dom';
import {ApiErrorBox} from '../components/ApiErrorBox';
import {resendVerificationCode} from '../api/auth';
import {useAuth} from '../context/AuthContext';
import {ApiRequestError, toApiError} from '../api/http';

const FIELD_NAMES: Record<string, string> = {
  email: 'E-mail',
  code: 'PIN-код',
};

const DEFAULT_NOTE =
  'Мы отправили на почту 6-значный PIN-код. Введите его ниже — вход в магазин откроется сразу после подтверждения.';

/**
 * Подтверждение e-mail по PIN-коду.
 *
 * Сюда приводит ответ /api/auth/register (мы передаём e-mail состоянием маршрута,
 * чтобы не спрашивать его повторно). Ручка /api/auth/verify-email публичная, поэтому
 * форма работает и без токена: пароль здесь не нужен — только e-mail и 6 цифр.
 *
 * Если SMTP на сервере не настроен, бэкенд печатает PIN-код в консоль — об этом
 * подсказка внизу формы, чтобы локальную разработку можно было пройти без почты.
 */
export function VerifyEmailPage() {
  const {verifyEmail} = useAuth();
  const navigate = useNavigate();
  const location = useLocation() as {state?: {email?: string; message?: string}};

  const [email, setEmail] = useState(location.state?.email ?? '');
  const [note, setNote] = useState(location.state?.message || DEFAULT_NOTE);
  const [error, setError] = useState<ApiRequestError | null>(null);
  const [busy, setBusy] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const code = String(data.get('code') ?? '').replace(/[\s-]/g, '');

    if (!email) {
      setError(new ApiRequestError(undefined, 0, 'Проверьте данные формы', {email: 'Укажите e-mail'}));
      return;
    }
    if (!/^\d{6}$/.test(code)) {
      setError(new ApiRequestError(undefined, 0, 'Проверьте данные формы', {code: 'PIN-код — ровно 6 цифр'}));
      return;
    }

    setBusy(true);
    try {
      // успешное подтверждение = бэкенд выдал токены и AuthContext записал пользователя
      await verifyEmail({email, code});
      navigate('/catalog', {replace: true});
    } catch (err) {
      setError(toApiError(err));
    } finally {
      setBusy(false);
    }
  }

  async function handleResend() {
    if (!email) {
      setError(new ApiRequestError(undefined, 0, 'Проверьте данные формы', {email: 'Укажите e-mail'}));
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const status = await resendVerificationCode(email);
      setNote(status.requiresVerification
        ? 'Отправили новый PIN-код на почту. Старый после этого перестаёт работать.'
        : status.message);
    } catch (err) {
      setError(toApiError(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className='page auth'>
      <form className='form' onSubmit={handleSubmit}>
        <h1 className='page__title'>Подтверждение e-mail</h1>
        <ApiErrorBox error={error} fieldNames={FIELD_NAMES} />
        <p className='form__hint'>{note}</p>

        <label className='field'>
          <span>E-mail</span>
          <input
            type='email'
            name='email'
            required
            autoComplete='email'
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            placeholder='you@example.com'
          />
        </label>

        <label className='field'>
          <span>PIN-код из письма (6 цифр)</span>
          {/* inputMode='text' + pattern: на телефоне не вылезает цифровая клавиатура,
              которая иногда «съедает» вставку кода из письма */}
          <input
            name='code'
            required
            pattern='\d{6}'
            maxLength={6}
            autoComplete='one-time-code'
            autoFocus
            placeholder='123456'
          />
        </label>

        <button type='submit' className='btn btn--primary btn--large' disabled={busy}>
          {busy ? 'Проверяем…' : 'подтвердить e-mail'}
        </button>

        <button type='button' className='btn btn--ghost' onClick={handleResend} disabled={busy}>
          отправить код ещё раз
        </button>

        <p className='form__hint'>
          Письма нет? Если бэкенд запущен локально и почта не настроена, PIN-код печатается
          в консоли сервера строкой «PIN-код подтверждения регистрации».
        </p>

        <p className='form__hint'>
          Уже подтвердили? <Link to='/login'>войти</Link> · <Link to='/catalog'>в каталог</Link>
        </p>
      </form>
    </section>
  );
}
