import {useEffect, useRef, useState, type FormEvent} from 'react';
import {Link, useNavigate, useParams} from 'react-router-dom';
import {deleteImage, getTShirtForAdmin, restoreTShirt, saveTShirt, setPreviewImage} from '../../api/catalog';
import {ApiRequestError, imageUrl, toApiError, type ApiRequestError as ApiError} from '../../api/http';
import {ApiErrorBox} from '../../components/ApiErrorBox';
import {Spinner} from '../../components/Spinner';
import type {TShirtDetailsDto, TShirtForm} from '../../types/api';
import {fileSize} from '../../utils/format';

const MAX_FILES = 5;
const MAX_FILE_BYTES = 5 * 1024 * 1024;

/** Главное фото ставим в начало галереи админки: сразу видно, какая картинка обложка. */
function previewFirst(images: TShirtDetailsDto['images']) {
  const preview = images.find((image) => image.previewImage);
  return preview ? [preview, ...images.filter((image) => image.id !== preview.id)] : images;
}

function previewName(product: TShirtDetailsDto | null): string {
  const preview = product?.images.find((image) => image.previewImage);
  return preview ? preview.originalFileName : 'не выбрано';
}

const FIELD_NAMES: Record<string, string> = {
  name: 'Название',
  size: 'Размер',
  price: 'Цена',
  files: 'Файлы',
};

/**
 * Форма товара (multipart). Часть "data" отправляется как JSON-блоб, поэтому
 * бэкенд валидирует поля точно так же, как обычный JSON (TShirtFormRequest).
 * Клиентские проверки (5 файлов, 5 МБ, только image/*) — удобство; настоящие
 * правила всё равно на сервере (ImageUploadPolicy).
 */
export function AdminProductFormPage() {
  const {id} = useParams<{id: string}>();
  const isNew = id === 'new';
  const productId = isNew ? null : Number(id);

  const navigate = useNavigate();
  const fileInput = useRef<HTMLInputElement>(null);

  const [product, setProduct] = useState<TShirtDetailsDto | null>(null);
  const [files, setFiles] = useState<File[]>([]);
  const [error, setError] = useState<ApiError | null>(null);
  const [busy, setBusy] = useState(false);
  /** id картинки, для которой сейчас идёт запрос (сделать главной / удалить). */
  const [busyImageId, setBusyImageId] = useState<number | null>(null);

  useEffect(() => {
    if (productId === null) {
      return;
    }
    // ВАЖНО: берём «админскую» ручку. Публичный GET /tshirts/{id} прячет скрытые
    // товары (is_disabled=true), и форма скрытой позиции падала бы с 404.
    getTShirtForAdmin(productId)
      .then(setProduct)
      .catch((err) => setError(toApiError(err)));
  }, [productId]);

  function pickFiles(list: FileList | null) {
    const picked = Array.from(list ?? []);
    const images = picked.filter((file) => file.type.startsWith('image/'));
    if (images.length !== picked.length) {
      setError(new ApiRequestError(undefined, 0, 'Можно выбирать только изображения'));
    }
    setFiles(images.slice(0, MAX_FILES));
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);

    if (files.length > MAX_FILES) {
      setError(new ApiRequestError(undefined, 0, `За раз не больше ${MAX_FILES} файлов`));
      return;
    }
    const tooBig = files.find((file) => file.size > MAX_FILE_BYTES);
    if (tooBig) {
      setError(new ApiRequestError(undefined, 0, `Файл ${tooBig.name} больше 5 МБ`));
      return;
    }

    const payload: TShirtForm = {
      name: String(data.get('name') ?? ''),
      description: String(data.get('description') ?? ''),
      size: String(data.get('size') ?? ''),
      price: Number(data.get('price')),
      available: data.get('available') !== null,
    };

    setBusy(true);
    try {
      const saved = await saveTShirt({id: productId ?? undefined, data: JSON.stringify(payload), files});
      setProduct(saved);
      setFiles([]);
      if (fileInput.current) {
        fileInput.current.value = '';
      }
      setError(null);
      navigate(`/admin/products/edit/${saved.id}`, {replace: true});
    } catch (err) {
      setError(toApiError(err));
    } finally {
      setBusy(false);
    }
  }

  /**
   * Смена главной картинки. Бэкенд хранит обложку в TShirt.previewImageId и отвечает
   * обновлённой карточкой товара — берём её целиком, поэтому рамка и плашка
   * «главное фото» переезжают сразу, без перезагрузки страницы.
   */
  async function handleMakePreview(imageId: number) {
    if (productId === null || busyImageId !== null) {
      return;
    }
    setBusyImageId(imageId);
    try {
      setProduct(await setPreviewImage(productId, imageId));
      setError(null);
    } catch (err) {
      setError(toApiError(err));
    } finally {
      setBusyImageId(null);
    }
  }

  async function handleDeleteImage(imageId: number) {
    if (productId === null || busyImageId !== null || !window.confirm('Удалить изображение?')) {
      return;
    }
    setBusyImageId(imageId);
    try {
      await deleteImage(imageId);
      setProduct(await getTShirtForAdmin(productId));
      setError(null);
    } catch (err) {
      setError(toApiError(err));
    } finally {
      setBusyImageId(null);
    }
  }

  async function handleRestore() {
    if (productId === null || busy) {
      return;
    }
    setBusy(true);
    try {
      // сервер ставит is_disabled=false и available=true: товар снова в каталоге
      setProduct(await restoreTShirt(productId));
      setError(null);
    } catch (err) {
      setError(toApiError(err));
    } finally {
      setBusy(false);
    }
  }

  if (!isNew && !product && !error) {
    return <Spinner />;
  }

  return (
    <section className='page'>
      <h1 className='page__title'>
        {isNew ? 'Новый товар' : `Товар #${productId}`}
        {product?.disabled && <span className='badge badge--muted'>скрыт из каталога</span>}
      </h1>
      <ApiErrorBox error={error} fieldNames={FIELD_NAMES} />

      {product?.disabled && (
        <div className='toolbar'>
          <button type='button' className='btn btn--primary' onClick={handleRestore} disabled={busy}>
            вернуть в продажу
          </button>
          <span className='form__hint'>
            Товар скрыт: покупатели его не видят. «Вернуть в продажу» снимает скрытие
            и снова делает позицию доступной.
          </span>
        </div>
      )}

      <form className='form' onSubmit={handleSubmit}>
        <label className='field'>
          <span>Название</span>
          <input name='name' required defaultValue={product?.name ?? ''} maxLength={100} />
        </label>

        <label className='field'>
          <span>Описание</span>
          <textarea name='description' rows={4} defaultValue={product?.description ?? ''} />
        </label>

        <div className='form__row'>
          <label className='field'>
            <span>Размер (S/M/L/XL…)</span>
            <input name='size' required defaultValue={product?.size ?? ''} maxLength={10} />
          </label>
          <label className='field'>
            <span>Цена</span>
            <input name='price' type='number' min='0.01' step='0.01' required defaultValue={product?.price ?? ''} />
          </label>
        </div>

        <label className='checkbox'>
          <input type='checkbox' name='available' defaultChecked={product?.available ?? true} />
          в наличии (можно покупать)
        </label>

        <label className='field'>
          <span>Изображения (до 5 файлов, каждый до 5 МБ)</span>
          <input ref={fileInput} type='file' accept='image/*' multiple onChange={(event) => pickFiles(event.target.files)} />
        </label>

        {files.length > 0 && (
          <ul className='file-list'>
            {files.map((file) => (
              <li key={`${file.name}-${file.size}`}>
                {file.name} <span className='muted'>{fileSize(file.size)}</span>
              </li>
            ))}
          </ul>
        )}

        <button type='submit' className='btn btn--primary btn--large' disabled={busy}>
          {busy ? 'Сохраняем…' : 'сохранить товар'}
        </button>
      </form>

      {product && product.images.length > 0 && (
        <div className='panel'>
          <h2>Галерея ({product.images.length})</h2>
          <p className='form__hint'>
            Главное фото видно покупателю в каталоге и первым на странице товара. Сейчас главное — «
            {previewName(product)}»; чтобы сделать другим, нажмите «сделать главным».
          </p>
          <div className='admin-gallery'>
            {previewFirst(product.images).map((image) => (
              <figure key={image.id} className={`admin-image ${image.previewImage ? 'is-preview' : ''}`}>
                <img src={imageUrl(image.id)} alt={image.originalFileName} loading='lazy' />
                {image.previewImage && <span className='admin-image__flag'>главное фото</span>}
                <figcaption>
                  <span className='muted'>
                    #{image.id} · {fileSize(image.size)}
                  </span>
                  <div className='admin-image__actions'>
                    {!image.previewImage && (
                      <button
                        type='button'
                        className='btn btn--ghost btn--small'
                        onClick={() => handleMakePreview(image.id)}
                        disabled={busyImageId !== null}
                      >
                        {busyImageId === image.id ? '…' : 'сделать главным'}
                      </button>
                    )}
                    <button
                      type='button'
                      className='btn btn--danger btn--small'
                      onClick={() => handleDeleteImage(image.id)}
                      disabled={busyImageId !== null}
                    >
                      удалить
                    </button>
                  </div>
                </figcaption>
              </figure>
            ))}
          </div>
        </div>
      )}

      <Link className='btn btn--ghost' to='/admin/products'>
        ← к списку товаров
      </Link>
    </section>
  );
}
