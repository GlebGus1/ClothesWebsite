import type {ApiRequestError} from '../api/http';

/**
 * Показывает то, что вернул бэкенд: общее сообщение + сообщения полей из
 * ApiError.fields (их формирует валидация Bean Validation в Java-DTO).
 * Так правила «пароль от 6 символов» не дублируются во фронтенде.
 */
export function ApiErrorBox({error, fieldNames = {}}: {error: ApiRequestError | null; fieldNames?: Record<string, string>}) {
  if (!error) {
    return null;
  }

  const entries = Object.entries(error.fields ?? {});

  return (
    <div className='alert alert--error' role='alert'>
      <p className='alert__title'>
        {error.status ? `${error.status}: ` : ''}
        {error.message}
      </p>
      {entries.length > 0 && (
        <ul className='alert__list'>
          {entries.map(([field, message]) => (
            <li key={field}>
              {fieldNames[field] ?? field}: {message}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
