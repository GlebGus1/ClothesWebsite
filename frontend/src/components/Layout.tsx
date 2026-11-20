import {Outlet} from 'react-router-dom';
import {Header} from './Header';

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
    </div>
  );
}
