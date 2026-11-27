import {getJson, postJson, putJson, deleteResource} from './http';
import type {CollectionDto, CollectionForm} from '../types/api';

/**
 * Публичный список коллекций (дропов) для баннера и чипсов над каталогом.
 * Приходят ТОЛЬКО активные: выключенную админом полку покупатель не увидит
 * ни в меню, ни в фильтрах. Отдельного «?all=true» у публичной ручки нет —
 * полный список живёт в /api/admin/collections (доступ только у ADMIN).
 */
export const listCollections = (): Promise<CollectionDto[]> => getJson<CollectionDto[]>('/collections');

/** Всё ниже — только ADMIN (см. AdminCollectionController): тут видны и выключенные полки. */
export const adminCollections = (): Promise<CollectionDto[]> => getJson<CollectionDto[]>('/admin/collections');

export const createCollection = (form: CollectionForm): Promise<CollectionDto> =>
  postJson<CollectionDto>('/admin/collections', form);

export const updateCollection = (id: number, form: CollectionForm): Promise<CollectionDto> =>
  putJson<CollectionDto>(`/admin/collections/${id}`, form);

/**
 * Удаление коллекции. Товары НЕ удаляются — они остаются без коллекции
 * (на них уже могут ссылаться заказы), поэтому удаление безопасно и для витрины,
 * и для истории продаж.
 */
export const deleteCollection = (id: number): Promise<void> => deleteResource(`/admin/collections/${id}`);
