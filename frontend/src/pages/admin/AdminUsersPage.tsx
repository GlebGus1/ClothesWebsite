import {useCallback, useEffect, useMemo, useState} from 'react';
import {adminUsers, setUserBanned} from '../../api/admin';
import {toApiError, type ApiRequestError} from '../../api/http';
import {useAuth} from '../../context/AuthContext';
import {Spinner} from '../../components/Spinner';
import type {UserDto} from '../../types/api';

/**
 * Пользователи + блокировка (чёрный список).
 *
 * Статусы различают два разных состояния, которые легко перепутать:
 *  - enabled=false — e-mail ещё не подтверждён PIN-кодом (аккаунт «висит», но не злой);
 *  - banned=true  — аккаунт заблокировал админ: вход и любые запросы с его токеном
 *    дают 403 «Аккаунт заблокирован».
 *
 * При блокировке сервер отзыввает ВСЕ refresh-токены пользователя, поэтому его
 * сессия умирает в течение минуты (access-токен живёт 15 минут, но продлить его
 * уже нельзя). Разблокировка старые токены не «оживляет» — человек входит заново.
 *
 * Кнопка «заблокировать» для самого себя выключена: это защита от ситуации, когда
 * единственный админ закрывает себе доступ к админке. На сервере это проверено
 * ещё раз (UserAdminService.setBanned), фронтенд только делает подсказку до клика.
 */
export function AdminUsersPage() {
  const {user: me} = useAuth();
  const [users, setUsers] = useState<UserDto[] | null>(null);
  const [query, setQuery] = useState('');
  const [error, setError] = useState<ApiRequestError | null>(null);
  const [feedback, setFeedback] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<number | null>(null);

  const load = useCallback(() => {
    setError(null);
    return adminUsers()
      .then(setUsers)
      .catch((err) => setError(toApiError(err)));
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const visible = useMemo(() => {
    if (!users) {
      return [];
    }
    const needle = query.trim().toLowerCase();
    if (!needle) {
      return users;
    }
    return users.filter((item) =>
      `${item.firstName} ${item.lastName} ${item.email}`.toLowerCase().includes(needle),
    );
  }, [users, query]);

  async function handleBan(userId: number, banned: boolean) {
    if (banned && !window.confirm(`Заблокировать пользователя #${userId}? Вход и все его активные сессии будут закрыты.`)) {
      return;
    }
    setBusyId(userId);
    setError(null);
    try {
      const updated = await setUserBanned(userId, banned);
      setUsers((current) => (current ? current.map((item) => (item.id === userId ? updated : item)) : current));
      setFeedback(
        banned
          ? `Пользователь ${updated.email} заблокирован, его сессии отозваны`
          : `Пользователь ${updated.email} разблокирован`,
      );
    } catch (err) {
      setError(toApiError(err));
    } finally {
      setBusyId(null);
    }
  }

  return (
    <section className='page'>
      <h1 className='page__title'>Пользователи{users ? ` (${users.length})` : ''}</h1>

      <div className='toolbar'>
        <label className='field field--inline'>
          <span>Поиск</span>
          <input
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder='имя или e-mail'
            aria-label='Поиск по имени или e-mail'
          />
        </label>
      </div>

      {error && <p className='alert alert--error'>{error.message}</p>}
      {feedback && <p className='alert alert--success'>{feedback}</p>}
      {!users && <Spinner />}

      {users && (
        <div className='table-wrap'>
          <table className='table'>
            <thead>
              <tr>
                <th>id</th>
                <th>Имя</th>
                <th>E-mail</th>
                <th>Роль</th>
                <th>Статус</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {visible.map((user) => {
                const isMe = me?.id === user.id;
                return (
                  <tr key={user.id} className={user.banned ? 'is-muted' : ''}>
                    <td>{user.id}</td>
                    <td>
                      {user.firstName} {user.lastName}
                      {isMe && <div className='muted'>это вы</div>}
                    </td>
                    <td>{user.email}</td>
                    <td>
                      {user.admin ? <span className='badge badge--admin'>ADMIN</span> : <span className='badge badge--muted'>USER</span>}
                    </td>
                    <td>
                      {user.banned ? (
                        <span className='badge badge--banned'>заблокирован</span>
                      ) : user.enabled ? (
                        <span className='badge badge--delivered'>активен</span>
                      ) : (
                        <span className='badge badge--muted'>ждет подтверждения e-mail</span>
                      )}
                    </td>
                    <td className='table__actions'>
                      <button
                        type='button'
                        className={`btn btn--small ${user.banned ? 'btn--primary' : 'btn--danger'}`}
                        onClick={() => handleBan(user.id, !user.banned)}
                        disabled={busyId === user.id || (isMe && !user.banned)}
                        title={isMe && !user.banned ? 'Нельзя заблокировать свой аккаунт — закроете доступ к админке' : undefined}
                      >
                        {busyId === user.id ? '…' : user.banned ? 'разблокировать' : 'заблокировать'}
                      </button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      {users && visible.length === 0 && <p className='empty'>Никого не нашло по этому запросу.</p>}
    </section>
  );
}
