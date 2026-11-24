import {StrictMode} from 'react';
import {createRoot} from 'react-dom/client';
import {BrowserRouter} from 'react-router-dom';
import {App} from './App';
import {AuthProvider} from './context/AuthContext';
import {CartProvider} from './context/CartContext';
import {FavoritesProvider} from './context/FavoritesContext';
import './styles/tokens.css';
import './styles/layout.css';
import './styles/components.css';
import './styles/pages.css';

const rootElement = document.getElementById('root');
if (!rootElement) {
  throw new Error('В index.html не найден элемент #root');
}

createRoot(rootElement).render(
  <StrictMode>
    <BrowserRouter>
      <AuthProvider>
        {/* FavoritesProvider внутри AuthProvider: избранное грузим только когда
            известен пользователь (id берём из токена, а не из параметров запроса) */}
        <FavoritesProvider>
          <CartProvider>
            <App />
          </CartProvider>
        </FavoritesProvider>
      </AuthProvider>
    </BrowserRouter>
  </StrictMode>,
);
