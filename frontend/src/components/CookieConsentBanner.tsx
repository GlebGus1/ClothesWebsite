import {useEffect, useState} from 'react';
import {trackSiteVisit} from '../api/tracking';
import {getConsent, isTrackingAllowed, setConsent, type ConsentDecision} from '../utils/consent';

/**
 * Баннер согласия на сбор обезличенной статистики.
 *
 * Логика простая:
 *  - решения нет → показываем баннер, запросов в /api/track/** нет;
 *  - «Принимаю» → сохраняем выбор в localStorage и отправляем событие «визит»;
 *  - «Отказываюсь» → сохраняем отказ; баннер больше не показываем, но и весь
 *    учёт (визиты, просмотры карточек, добавления в корзину) не отправляется.
 *
 * Почему баннер живёт в <Layout>, а не в каждой странице: он один на всё приложение,
 * и отправка «визита» тоже одна на сеанс браузера (см. utils/consent.hasVisitBeenSent).
 *
 * Важный момент для понимания архитектуры: сервер НЕ проверяет согласие. Ему
 * достаточно не получать запросов. Всё решение о том, стучаться или нет, принимает
 * фронтенд — это единственный способ честно выполнить «отказ».
 */
export function CookieConsentBanner() {
  const [decision, setDecision] = useState<ConsentDecision | null>(() => getConsent());

  useEffect(() => {
    // посетитель уже согласился раньше (прошлый визит) — баннер не показываем,
    // но визит в этом сеансе браузера всё равно нужно отправить один раз
    if (isTrackingAllowed()) {
      void trackSiteVisit();
    }
  }, []);

  if (decision) {
    return null;
  }

  function choose(next: ConsentDecision) {
    setConsent(next);
    setDecision(next);
    if (next === 'accepted') {
      void trackSiteVisit();
    }
  }

  return (
    <aside className='consent' role='region' aria-label='Согласие на сбор статистики'>
      <p className='consent__text'>
        Мы собираем обезличенную статистику: сколько людей заходило, какие футболки смотрели и
        клали в корзину. Это помогает понять, какие дропы продаются лучше. Мы не следим за вами
        по рекламным сетям и не передаём эти данные третьим лицам.
      </p>
      <div className='consent__actions'>
        <button type='button' className='btn btn--primary btn--small' onClick={() => choose('accepted')}>
          Принимаю
        </button>
        {/* «Отказаться» должно быть доступно так же легко, как «Принять», — иначе
            выбор ненастоящий. Магазин при этом работает полностью */}
        <button type='button' className='btn btn--ghost btn--small' onClick={() => choose('declined')}>
          Отказаться
        </button>
      </div>
    </aside>
  );
}
