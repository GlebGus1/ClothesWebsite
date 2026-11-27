import {useEffect, useState, type FormEvent} from 'react';
import {Link, useNavigate} from 'react-router-dom';
import {captchaStatus} from '../api/auth';
import {ApiErrorBox} from '../components/ApiErrorBox';
import {TurnstileWidget} from '../components/TurnstileWidget';
import {useAuth} from '../context/AuthContext';
import {ApiRequestError, toApiError, type ApiRequestError as ApiError} from '../api/http';
import type {CaptchaStatus} from '../types/api';

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
  // капча — не решение фронтенда: сначала спрашиваем сервер, нужна ли она
  const [captcha, setCaptcha] = useState<CaptchaStatus | null>(null);
  const [captchaToken, setCaptchaToken] = useState<string | null>(null);

  useEffect(() => {
    captchaStatus()
      .then(setCaptcha)
      // запрос статуса не критичен: если он упал, форму не блокируем —
      // окончательное слово всё равно за сервером (он и отклонит запрос без токена)
      .catch(() => setCaptcha(null));
  }, []);

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

    // запрос без токена пришёл бы на сервер и получил 400, но лучше назвать
    // причину человеку сразу, до похода в сеть
    if (captcha?.required && !captchaToken) {
      setError(new ApiRequestError(undefined, 0, 'Проверьте данные формы', {captcha: 'Подтвердите, что вы не робот'}));
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
        captchaToken,
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
      // токен капчи живёт несколько минут и используется один раз: после неудачи
      // просим пройти её заново, иначе второй запрос уйдёт протухшим токеном
      if (captcha?.required) {
        setCaptchaToken(null);
      }
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

        {/* виджет появляется ТОЛЬКО если капча включена на сервере
            (captcha.turnstile.site-key / secret-key заданы) */}
        {captcha?.required && captcha.siteKey && (
          <div className='field'>
            <span>Защита от ботов</span>
            <TurnstileWidget siteKey={captcha.siteKey} onToken={setCaptchaToken} />
          </div>
        )}

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

        <p className='form__hint'>
          Чтобы остановить спам-ботов, регистрация ограничена по адресу: слишком много
          попыток с одного IP дают 429 «Слишком много запросов» — нужно подождать минуту
          (а при очень частых попытках — сутки). Проверка идёт ДО создания аккаунта,
          поэтому спам не попадает ни в базу, ни в почту.
        </p>
      </form>
    </section>
  );
}
