import {Link} from 'react-router-dom';

export function NotFoundPage() {
  return (
    <section className='page'>
      <h1 className='page__title'>Страница не найдена</h1>
      <p className='empty'>
        Такой страницы нет. <Link to='/catalog'>в каталог</Link>
      </p>
    </section>
  );
}
