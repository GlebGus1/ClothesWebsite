import {http} from './http';
import {hasVisitBeenSent, isTrackingAllowed, markVisitSent} from '../utils/consent';
import {getVisitorId} from '../utils/visitorId';

/**
 * Отправка событий воронки продаж (visits → product views → cart adds).
 *
 * Три важных правила, заложенные здесь:
 *  1) Без согласия в cookie-баннере запросов нет вообще — функция выходит молча.
 *     Поэтому «отказался» означает не «данные анонимизированы», а «данные не собраны».
 *  2) Ошибки глушим: статистика не должна ронять страницу или показывать красный
 *     текст там, где пользователь просто смотрит товар.
 *  3) Просмотры карточек здесь НЕ отправляются: их засчитывает сам бэкенд в
 *     GET /api/tshirts/{id} по заголовку X-Visitor-Id (см. TShirtController#getOne).
 *     Отдельная ручка для просмотров привела бы к двойному счёту.
 */

/** Заголовок для запроса карточки товара: без согласия его нет → счётчик не растёт. */
export function visitorHeaders(): Record<string, string> | undefined {
  return isTrackingAllowed() ? {'X-Visitor-Id': getVisitorId()} : undefined;
}

/** Визит на сайт — максимум один раз на сеанс браузера (плюс дедупликация на сервере). */
export async function trackSiteVisit(): Promise<void> {
  if (!isTrackingAllowed() || hasVisitBeenSent()) {
    return;
  }
  markVisitSent(); // помечаем ДО запроса: React в strict mode вызывает эффекты дважды
  try {
    await http.post('/track/visit', {visitorId: getVisitorId()});
  } catch {
    /* статистика не важнее работы магазина */
  }
}

/** Добавление товара в корзину (событие «намерение купить»). */
export async function trackCartAdd(tshirtId: number): Promise<void> {
  if (!isTrackingAllowed()) {
    return;
  }
  try {
    await http.post('/track/cart-add', {visitorId: getVisitorId(), tshirtId});
  } catch {
    /* игнорируем */
  }
}
