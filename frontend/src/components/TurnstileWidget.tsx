import {useEffect, useRef, useState} from 'react';

/**
 * Cloudflare Turnstile в форме регистрации.
 *
 * Как это работает без npm-пакета:
 *  1. бэкенд отдаёт GET /api/auth/captcha-status — только там знает, нужен ли капча
 *     вообще (ключ Turnstile не задан → капчи нет, и поля в форме не появляется);
 *  2. если нужна — подключаем скрипт Cloudflare и рендерим виджет вручную
 *     (render=explicit), чтобы управлять жизненным циклом внутри React;
 *  3. Cloudflare отдаёт токен (короткую «путёвку» на 300 секунд), мы кладём его в
 *     состояние формы и отправляем вместе с регистрацией;
 *  4. токен проверяет сервер через siteverify. Именно серверная проверка имеет
 *     значение: виджет в браузере — это UX, а не защита. Злоумышленник может вовсе
 *     его не открывать и слать запросы напрямую в API — его остановит именно
 *     verifyOrThrow в AuthService.register.
 *
 * Скрипт грузится один раз на страницу (кэш в module-level переменной), иначе
 * несколько mount/unmount формы в SPA добавили бы по тегу <script> на каждый рендер.
 */
declare global {
  interface Window {
    turnstile?: {
      render: (element: HTMLElement, options: Record<string, unknown>) => string;
      remove: (widgetId: string) => void;
    };
  }
}

const SCRIPT_SRC = 'https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit';

let scriptLoading: Promise<void> | null = null;

function loadTurnstile(): Promise<void> {
  if (window.turnstile) {
    return Promise.resolve();
  }
  if (scriptLoading) {
    return scriptLoading;
  }
  scriptLoading = new Promise<void>((resolve, reject) => {
    const script = document.createElement('script');
    script.src = SCRIPT_SRC;
    script.async = true;
    script.onload = () => {
      if (window.turnstile) {
        resolve();
      } else {
        reject(new Error('Cloudflare Turnstile загрузился, но объект turnstile недоступен'));
      }
    };
    // блокировщик расширений, корпоративный прокси или offline-разработка:
    // отказ не должен ломать форму — родитель покажет понятную подсказку
    script.onerror = () => {
      scriptLoading = null;
      reject(new Error('Не удалось загрузить скрипт капчи (возможно, его заблокировал блокировщик рекламы)'));
    };
    document.head.appendChild(script);
  });
  return scriptLoading;
}

export function TurnstileWidget({
  siteKey,
  onToken,
}: {
  siteKey: string;
  onToken: (token: string | null) => void;
}) {
  const holder = useRef<HTMLDivElement | null>(null);
  const [error, setError] = useState<string | null>(null);
  // колбэки Cloudflare живут вне React: держим свежий onToken в ref, чтобы в виджет
  // не устарела замыкания из первой отрисовки формы
  const onTokenRef = useRef(onToken);
  onTokenRef.current = onToken;

  useEffect(() => {
    let widgetId: string | null = null;
    let cancelled = false;

    loadTurnstile()
      .then(() => {
        if (cancelled || !holder.current || !window.turnstile) {
          return;
        }
        widgetId = window.turnstile.render(holder.current, {
          sitekey: siteKey,
          theme: 'light',
          callback: (token: string) => onTokenRef.current(token),
          // токен протух или челлендж упал — просим капчу заново, а не отправляем пустоту
          'expired-callback': () => onTokenRef.current(null),
          'error-callback': () => onTokenRef.current(null),
        });
      })
      .catch((err: Error) => {
        if (!cancelled) {
          setError(err.message);
        }
      });

    return () => {
      cancelled = true;
      if (widgetId && window.turnstile) {
        window.turnstile.remove(widgetId);
      }
    };
  }, [siteKey]);

  return (
    <div className='turnstile'>
      {/* контейнер заполняет Cloudflare: своих полей внутри нет */}
      <div ref={holder} className='turnstile__widget' />
      {error && (
        <p className='turnstile__error'>
          {error}. Регистрация без капчи сейчас невозможна: отключите блокировщик или
          обновите страницу.
        </p>
      )}
    </div>
  );
}
