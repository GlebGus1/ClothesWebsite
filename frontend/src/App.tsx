import {Navigate, Route, Routes} from 'react-router-dom';
import {Layout} from './components/Layout';
import {ProtectedRoute} from './components/ProtectedRoute';
import {CartPage} from './pages/CartPage';
import {CatalogPage} from './pages/CatalogPage';
import {CheckoutPage} from './pages/CheckoutPage';
import {LoginPage} from './pages/LoginPage';
import {NotFoundPage} from './pages/NotFoundPage';
import {OrderDetailsPage} from './pages/OrderDetailsPage';
import {OrdersPage} from './pages/OrdersPage';
import {ProductPage} from './pages/ProductPage';
import {RegisterPage} from './pages/RegisterPage';
import {AdminOrdersPage} from './pages/admin/AdminOrdersPage';
import {AdminProductFormPage} from './pages/admin/AdminProductFormPage';
import {AdminProductsPage} from './pages/admin/AdminProductsPage';
import {AdminUsersPage} from './pages/admin/AdminUsersPage';

/**
 * Mapa маршрутов.
 *  - /catalog, /product/:id — доступны гостям (как и соответствующие GET в API);
 *  - /cart — локальная корзина, вход не нужен;
 *  - /checkout, /orders — ProtectedRoute (на сервере эти же запросы требуют токен);
 *  - /admin/** — ProtectedRoute adminOnly (на сервере — hasRole('ADMIN')).
 */
export function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<Navigate to='/catalog' replace />} />
        <Route path='/catalog' element={<CatalogPage />} />
        <Route path='/product/:id' element={<ProductPage />} />
        <Route path='/login' element={<LoginPage />} />
        <Route path='/register' element={<RegisterPage />} />
        <Route path='/cart' element={<CartPage />} />

        <Route
          path='/checkout'
          element={
            <ProtectedRoute>
              <CheckoutPage />
            </ProtectedRoute>
          }
        />
        <Route
          path='/orders'
          element={
            <ProtectedRoute>
              <OrdersPage />
            </ProtectedRoute>
          }
        />
        <Route
          path='/orders/:id'
          element={
            <ProtectedRoute>
              <OrderDetailsPage />
            </ProtectedRoute>
          }
        />

        <Route
          path='/admin/products'
          element={
            <ProtectedRoute adminOnly>
              <AdminProductsPage />
            </ProtectedRoute>
          }
        />
        <Route
          path='/admin/products/edit/:id'
          element={
            <ProtectedRoute adminOnly>
              <AdminProductFormPage />
            </ProtectedRoute>
          }
        />
        <Route
          path='/admin/orders'
          element={
            <ProtectedRoute adminOnly>
              <AdminOrdersPage />
            </ProtectedRoute>
          }
        />
        <Route
          path='/admin/users'
          element={
            <ProtectedRoute adminOnly>
              <AdminUsersPage />
            </ProtectedRoute>
          }
        />

        <Route path='*' element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}
