/**
 * Согласие на сбор обезличенной статистики (cookie-баннер).
 *
 * Как это работает простыми словами:
 *  - решение пользователя храним локально (localStorage), а не в куке сервера:
 *    серверу про согласие знать НЕ нужно — он просто не получает запросов,
 *    если человек отказался;
 *  - «нет решения» (null) — это отдельное состояние: баннер показываем,
 *    а аналитику НЕ шлём. Молчание ≠ согласие;
 *  - отказ не ломает магазин: корзина, вход, заказы работают как раньше,
 *    просто запросы /api/track/** и заголовок X-Visitor-Id исчезают.
 *
 * Почему не cookie: куки — это отдельная библиотека и свои риски размера/истечения,
 * а нам нужна одна строка, живущая между визитами.
 */

export type ConsentDecision = 'accepted' | 'declined';

const CONSENT_KEY = 'clothes.cookie-consent';
/** Флаг «в этом сеансе браузера визит уже отправлен» — чтобы не дёргать API
 * на каждой перезагрузке страницы (сервер и так схлопывает повторные визиты
 * одного посетителя за сутки, но лишний запрос — лишняя нагрузка). */
const VISIT_SENT_KEY = 'clothes.visit-sent';

function readDecision(): ConsentDecision | null {
  try {
    const raw = localStorage.getItem(CONSENT_KEY);
    return raw === 'accepted' || raw === 'declined' ? raw : null;
  } catch {
    // приватный режим/заблокированный localStorage: решение не сохраняем,
    // но и трекить без согласия не будем
    return null;
  }
}

export function getConsent(): ConsentDecision | null {
  return readDecision();
}

/** Разрешено ли вообще что-либо отправлять в статистику. */
export function isTrackingAllowed(): boolean {
  return readDecision() === 'accepted';
}

export function setConsent(decision: ConsentDecision): void {
  try {
    localStorage.setItem(CONSENT_KEY, decision);
    // после смены решения «визит уже отправлен» теряет смысл: при согласии
    // нужно отправить визит, при отказе — ничего не отправлять
    sessionStorage.removeItem(VISIT_SENT_KEY);
  } catch {
    /* игнорируем: без хранилища баннер просто покажется снова */
  }
}

export function hasVisitBeenSent(): boolean {
  try {
    return sessionStorage.getItem(VISIT_SENT_KEY) === '1';
  } catch {
    return false;
  }
}

export function markVisitSent(): void {
  try {
    sessionStorage.setItem(VISIT_SENT_KEY, '1');
  } catch {
    /* не критично */
  }
}
