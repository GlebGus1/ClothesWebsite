import {deleteResource, getJson, http, patchJson} from './http';
import type {AdminTShirtDto, CatalogQuery, PageResponse, TShirtDetailsDto} from '../types/api';

/**
 * Товарные операции администратора. Зеркалят AdminTShirtController
 * (/api/admin/tshirts/**) плюс два «исторических» пути (/api/tshirts для создания,
 * обновления и удаления — права на них проверяет SecurityConfig, а не фронтенд).
 *
 * Таблица админки читается ОТДЕЛЬНЫМ эндпоинтом /api/admin/tshirts, а не публичным
 * /api/tshirts?includeDisabled=true: раньше «подсмотреть снятый с продажи товар»
 * было разрешено прямо в публичном методе по роли пользователя, и это легко
 * выезжает за пределы метода. Здесь же видны columns, которых у витрины нет вообще:
 * viewsCount и soldInOrderId.
 */

export const adminTShirts = (query: CatalogQuery): Promise<PageResponse<AdminTShirtDto>> =>
  getJson<PageResponse<AdminTShirtDto>>('/admin/tshirts', {...query});

/** Карточка ЛЮБОЙ позиции, включая скрытую: форма правки должна открываться и на снятых товарах. */
export const getTShirtForAdmin = (id: number): Promise<TShirtDetailsDto> =>
  getJson<TShirtDetailsDto>(`/admin/tshirts/${id}`);

/**
 * Быстрое «в продаже / нет в продаже» прямо из таблицы, без формы товара.
 * Ответ — карточка целиком (с картинками), чтобы строку таблицы можно было
 * перерисовать, не дёргая список заново.
 */
export const setAvailability = (id: number, available: boolean): Promise<TShirtDetailsDto> =>
  patchJson<TShirtDetailsDto>(`/admin/tshirts/${id}/availability`, {available});

/** Выбор главной картинки (превью в каталоге). */
export const setPreviewImage = (tShirtId: number, imageId: number): Promise<TShirtDetailsDto> =>
  patchJson<TShirtDetailsDto>(`/admin/tshirts/${tShirtId}/preview/${imageId}`);

/** «Вернуть в продажу» для снятой позиции: is_disabled=false + available=true. */
export const restoreTShirt = (id: number): Promise<TShirtDetailsDto> =>
  patchJson<TShirtDetailsDto>(`/admin/tshirts/${id}/restore`);

/** Создание/обновление товара: multipart, где часть "data" — JSON формы, "files" — картинки. */
export async function saveTShirt(params: {id?: number; data: string; files: File[]}): Promise<TShirtDetailsDto> {
  const form = new FormData();
  form.append('data', new Blob([params.data], {type: 'application/json'}));
  params.files.forEach((file) => form.append('files', file));

  const request = params.id ? http.put(`/tshirts/${params.id}`, form) : http.post('/tshirts', form);
  const response = await request;
  return response.data as TShirtDetailsDto;
}

/** Мягкое удаление: товар скрывается из каталога, но заказы по нему остаются. */
export const hideTShirt = (id: number): Promise<void> => deleteResource(`/tshirts/${id}`);

export const deleteImage = (imageId: number): Promise<void> => deleteResource(`/images/${imageId}`);
