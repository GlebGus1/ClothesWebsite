import {getJson} from './http';
import {visitorHeaders} from './tracking';
import type {CatalogQuery, PageResponse, TShirtDetailsDto, TShirtDto} from '../types/api';

/**
 * Публичный каталог: то, что может делать гость без токена.
 *
 * Все «админские» операции над товарами лежат рядом, но в другом модуле —
 * api/adminProducts.ts (пути /api/admin/tshirts/**). Разделение совпадает с
 * бэкендом: TShirtController (витрина, скрытые товары не видны в принципе) и
 * AdminTShirtController (таблица админа: снятые с продажи, просмотры, заказы).
 */

export const listTShirts = (query: CatalogQuery): Promise<PageResponse<TShirtDto>> =>
  getJson<PageResponse<TShirtDto>>('/tshirts', {...query});

export const listSizes = (): Promise<string[]> => getJson<string[]>('/tshirts/sizes');

/**
 * Карточка товара.
 *
 * Если посетитель согласился на статистику, добавляем заголовок X-Visitor-Id —
 * и бэкенд засчитает просмотр (один на товар на посетителя в сутки). Без согласия
 * заголовка нет → счётчик просмотров не растёт. Никакой другой разницы для
 * пользователя нет: товар он видит тот же самый.
 */
export const getTShirt = (id: number): Promise<TShirtDetailsDto> =>
  getJson<TShirtDetailsDto>(`/tshirts/${id}`, undefined, {headers: visitorHeaders()});
