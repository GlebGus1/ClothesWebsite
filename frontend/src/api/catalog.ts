import {deleteResource, getJson, http, patchJson} from './http';
import type {CatalogQuery, PageResponse, TShirtDetailsDto, TShirtDto} from '../types/api';

export const listTShirts = (query: CatalogQuery): Promise<PageResponse<TShirtDto>> =>
  getJson<PageResponse<TShirtDto>>('/tshirts', {...query});

export const listSizes = (): Promise<string[]> => getJson<string[]>('/tshirts/sizes');

export const getTShirt = (id: number): Promise<TShirtDetailsDto> => getJson<TShirtDetailsDto>(`/tshirts/${id}`);

/**
 * Карточка ЛЮБОЙ позиции, включая скрытую (is_disabled=true).
 * Публичный GET /tshirts/{id} такие товары прячет, поэтому форма редактирования
 * скрытого товара получала 404. Этот эндпоинт закрыт ролью ADMIN на сервере.
 */
export const getTShirtForAdmin = (id: number): Promise<TShirtDetailsDto> =>
  getJson<TShirtDetailsDto>(`/admin/tshirts/${id}`);

/** «Вернуть в продажу» для скрытого товара: is_disabled=false + available=true. */
export const restoreTShirt = (id: number): Promise<TShirtDetailsDto> =>
  patchJson<TShirtDetailsDto>(`/admin/tshirts/${id}/restore`);

export const deleteTShirt = (id: number): Promise<void> => deleteResource(`/tshirts/${id}`);

export const setPreviewImage = (tShirtId: number, imageId: number): Promise<TShirtDetailsDto> =>
  patchJson<TShirtDetailsDto>(`/tshirts/${tShirtId}/preview/${imageId}`);

export const deleteImage = (imageId: number): Promise<void> => deleteResource(`/images/${imageId}`);

/**
 * Создание/обновление товара: multipart/form-data, где часть "data" — JSON-строка
 * с полями формы, а часть "files" — сами картинки (до 5 штук).
 * Файлы, прикреплённые к существующему товару, добавляются к уже имеющимся галереям.
 */
export async function saveTShirt(params: {
  id?: number;
  data: string;
  files: File[];
}): Promise<TShirtDetailsDto> {
  const form = new FormData();
  form.append('data', new Blob([params.data], {type: 'application/json'}));
  params.files.forEach((file) => form.append('files', file));

  const url = params.id ? `/tshirts/${params.id}` : '/tshirts';
  const request = params.id ? http.put(url, form) : http.post(url, form);
  const response = await request;
  return response.data as TShirtDetailsDto;
}
