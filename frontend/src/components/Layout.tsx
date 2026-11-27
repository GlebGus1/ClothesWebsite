import {Outlet} from 'react-router-dom';
import {Header} from './Header';
import {CookieConsentBanner} from './CookieConsentBanner';

export function Layout() {
  return (
    <div className='app'>
      <Header />
      <main className='app__main'>
        <Outlet />
      </main>
      <footer className='footer'>
        Учебный проект: Spring Boot REST + JWT на бэкенде, React + Vite во фронтенде.
      </footer>
      {/* Один на всё приложение: решение о согласии запоминается, поэтому
          баннер появится ровно один раз на браузер, а не на каждой странице */}
      <CookieConsentBanner />
    </div>
  );
}
