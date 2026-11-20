import {useEffect, useState} from 'react';
import {adminUsers} from '../../api/admin';
import {toApiError, type ApiRequestError} from '../../api/http';
import {Spinner} from '../../components/Spinner';
import type {UserDto} from '../../types/api';

/** Список пользователей: только чтение (права на редактирование не заводили). */
export function AdminUsersPage() {
  const [users, setUsers] = useState<UserDto[] | null>(null);
  const [error, setError] = useState<ApiRequestError | null>(null);

  useEffect(() => {
    adminUsers()
      .then(setUsers)
      .catch((err) => setError(toApiError(err)));
  }, []);

  if (error) {
    return <p className='alert alert--error'>{error.message}</p>;
  }
  if (!users) {
    return <Spinner />;
  }

  return (
    <section className='page'>
      <h1 className='page__title'>Пользователи ({users.length})</h1>

      <div className='table-wrap'>
        <table className='table'>
          <thead>
            <tr>
              <th>id</th>
              <th>Имя</th>
              <th>E-mail</th>
              <th>Роль</th>
            </tr>
          </thead>
          <tbody>
            {users.map((user) => (
              <tr key={user.id}>
                <td>{user.id}</td>
                <td>
                  {user.firstName} {user.lastName}
                </td>
                <td>{user.email}</td>
                <td>
                  {user.admin ? <span className='badge badge--admin'>ADMIN</span> : <span className='badge badge--muted'>USER</span>}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}
