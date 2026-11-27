/**
 * Анонимный идентификатор посетителя для статистики.
 *
 * Что это такое: случайный uuid, который браузер сгенерировал сам и хранит
 * в localStorage. Он НЕ связан ни с e-mail, ни с токеном, ни с адресом сайта:
 * по нему нельзя понять, кто человек. Нужен он только чтобы посчитать воронку
 * («этот же посетитель посмотрел товар» → «этот же посетитель добавил в корзину»).
 *
 * Почему не id пользователя: карточки смотрят и гости без аккаунта, а заказы
 * всё равно считаются по базе. Почему не cookie сервера: тогда согласие на куки
 * пришлось бы обрабатывать на сервере, а нам проще вообще ничего не хранить.
 *
 * Ограничение 64 символа — как в @Size у TrackingRequest на бэкенде: сервер не
 * примет произвольно длинную строку, а в БД поле имеет длину 64.
 */

const VISITOR_KEY = 'clothes.visitor-id';
const MAX_LENGTH = 64;

function generate(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  // fallback для старых браузеров / небезопасного контекста (http без localhost)
  return `v-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
}

/** Идентификатор посетителя: тот же самый при повторных визитах с этого браузера. */
export function getVisitorId(): string {
  try {
    const saved = localStorage.getItem(VISITOR_KEY);
    if (saved && saved.length <= MAX_LENGTH) {
      return saved;
    }
    const fresh = generate().slice(0, MAX_LENGTH);
    localStorage.setItem(VISITOR_KEY, fresh);
    return fresh;
  } catch {
    // localStorage недоступен: живём с «одноразовым» id — статистика останется,
    // просто такой посетитель будет считаться новым при каждом открытии
    return generate().slice(0, MAX_LENGTH);
  }
}
