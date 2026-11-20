import {type ReactNode} from 'react';
import {Navigate, useLocation} from 'react-router-dom';
import {useAuth} from '../context/AuthContext';
import {Spinner} from './Spinner';

/**
 * Защита маршрутов на фронтенде — это UX (не пустой экран вместо 401).
 * Настоящий контроль — на бэкенде: токен и роль проверяет JwtAuthenticationFilter,
 * а права на /api/admin/** описаны в SecurityConfig.
 */
export function ProtectedRoute({children, adminOnly = false}: {children: ReactNode; adminOnly?: boolean}) {
  const {user, initializing} = useAuth();
  const location = useLocation();

  if (initializing) {
    return <Spinner label='Проверяем сессию…' />;
  }

  if (!user) {
    // сохраняем, куда шёл пользователь, чтобы после входа вернуть его туда
    return <Navigate to='/login' replace state={{from: location.pathname}} />;
  }

  if (adminOnly && !user.admin) {
    return <Navigate to='/catalog' replace />;
  }

  return <>{children}</>;
}
