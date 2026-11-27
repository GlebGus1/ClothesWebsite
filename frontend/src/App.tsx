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
import {ProfilePage} from './pages/ProfilePage';
import {RegisterPage} from './pages/RegisterPage';
import {VerifyEmailPage} from './pages/VerifyEmailPage';
import {AdminAnalyticsPage} from './pages/admin/AdminAnalyticsPage';
import {AdminCollectionsPage} from './pages/admin/AdminCollectionsPage';
import {AdminOrdersPage} from './pages/admin/AdminOrdersPage';
import {AdminProductFormPage} from './pages/admin/AdminProductFormPage';
import {AdminProductsPage} from './pages/admin/AdminProductsPage';
import {AdminPromoCodesPage} from './pages/admin/AdminPromoCodesPage';
import {AdminUsersPage} from './pages/admin/AdminUsersPage';

/**
 * Карта маршрутов.
 *  - /catalog, /product/:id — доступны гостям (как и соответствующие GET в API);
 *  - /cart — локальная корзина, вход не нужен;
 *  - /verify-email — публичная: PIN-код подтверждает аккаунт ещё ДО выдачи токенов;
 *  - /profile, /checkout, /orders — ProtectedRoute (на сервере эти же запросы требуют токен);
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
        {/* Подтверждение e-mail по PIN-коду: доступ открыт и без токена —
            аккаунт ещё не активирован, токенов у пользователя нет */}
        <Route path='/verify-email' element={<VerifyEmailPage />} />
        <Route path='/cart' element={<CartPage />} />

        <Route
          path='/profile'
          element={
            <ProtectedRoute>
              <ProfilePage />
            </ProtectedRoute>
          }
        />

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
          path='/admin/promo-codes'
          element={
            <ProtectedRoute adminOnly>
              <AdminPromoCodesPage />
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
        {/* Коллекции (дропы) и аналитика — новые разделы админки. Оба закрыты
            и на фронтенде (adminOnly), и на сервере (hasRole('ADMIN')): маршрутизатор
            в браузере — это удобство, а не защита */}
        <Route
          path='/admin/collections'
          element={
            <ProtectedRoute adminOnly>
              <AdminCollectionsPage />
            </ProtectedRoute>
          }
        />
        <Route
          path='/admin/analytics'
          element={
            <ProtectedRoute adminOnly>
              <AdminAnalyticsPage />
            </ProtectedRoute>
          }
        />

        <Route path='*' element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}
