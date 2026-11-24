import {deleteResource, getJson, postJson} from './http';
import type {TShirtDto} from '../types/api';

/**
 * Избранное («сердечко»). Все ручки требуют токен: бэкенд берёт пользователя
 * из токена, id пользователя в запросе нет — чужое избранное изменить нельзя.
 */

export const listFavorites = (): Promise<TShirtDto[]> => getJson<TShirtDto[]>('/favorites');

/** Только id избранных товаров — лёгкий запрос для каталога, чтобы закрасить сердечки. */
export const favoriteIds = (): Promise<number[]> => getJson<number[]>('/favorites/ids');

export const addFavorite = (tshirtId: number): Promise<void> => postJson<void>(`/favorites/${tshirtId}`);

export const removeFavorite = (tshirtId: number): Promise<void> => deleteResource(`/favorites/${tshirtId}`);
