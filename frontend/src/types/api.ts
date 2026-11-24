/**
 * Типы фронтенда зеркалируют Java-записи (DTO) из пакета com.example.appShop.dto.
 * Держим в одном месте: если бэкенд меняет record — меняем и здесь, компилятор
 * сразу покажет, где в UI поехали новые поля.
 */

export interface UserDto {
  id: number;
  email: string;
  firstName: string;
  lastName: string;
  admin: boolean;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  /** TTL access-токена в секундах. */
  expiresIn: number;
  user: UserDto;
}

/**
 * Ответ регистрации и повторной отправки кода. Токенов здесь НЕТ: пока e-mail
 * не подтверждён PIN-кодом, доступа к API у аккаунта быть не должно.
 */
export interface VerificationStatusDto {
  email: string;
  /** true — ждём ввод 6-значного PIN-кода; false — подтверждение не нужен (выключен на сервере). */
  requiresVerification: boolean;
  message: string;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

/** Ответ глобального обработчика ошибок ApiExceptionHandler. */
export interface ApiErrorBody {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
  fields: Record<string, string>;
}

export interface TShirtDto {
  id: number;
  name: string;
  description: string | null;
  size: string | null;
  price: number | null;
  available: boolean;
  disabled: boolean;
  dateOfCreated: string | null;
  previewImageId: number | null;
  /** id всех фото товара (главное — первым); нужны, чтобы листать галерею прямо в карточке. */
  imageIds: number[];
}

export interface ImageDto {
  id: number;
  name: string;
  originalFileName: string;
  size: number;
  contentType: string;
  previewImage: boolean;
  tShirtId: number;
}

/**
 * Карточка товара целиком: поля списка + полные метаданные картинок (id, имя, размер).
 * imageIds здесь не нужен: список id есть внутри images.
 */
export interface TShirtDetailsDto extends Omit<TShirtDto, 'imageIds'> {
  images: ImageDto[];
}

/** Поля формы товара (часть multipart-запроса "data"). */
export interface TShirtForm {
  name: string;
  description: string;
  size: string;
  price: number | null;
  available: boolean;
}

export type OrderStatus = 'PROCESSING' | 'IN_TRANSIT' | 'DELIVERED' | 'CANCELED';

export interface OrderItemDto {
  id: number;
  name: string;
  size: string | null;
  price: number | null;
}

export interface OrderDto {
  id: number;
  status: OrderStatus;
  nameOfUser: string;
  postOffice: string;
  postalCode: string;
  phoneNumber: string;
  createdAt: string | null;
  customerId: number | null;
  customerEmail: string | null;
  /** Промокод заказа (null — без скидки). Бэкенд хранит его «слепком». */
  promoCode: string | null;
  discountPercent: number;
  discountAmount: number;
  /** Итог к оплате, посчитанный сервером. */
  total: number;
  items: OrderItemDto[];
}

export interface CreateOrderPayload {
  nameOfUser: string;
  postOffice: string;
  postalCode: string;
  phoneNumber: string;
  tshirtIds: number[];
  promoCode: string | null;
}

/** Денежные итоги, которые посчитал сервер (цене из localStorage доверять нельзя). */
export interface TotalsDto {
  subtotal: number;
  discountPercent: number;
  discountAmount: number;
  total: number;
}

export interface PromoCodeDto {
  id: number;
  code: string;
  discountPercent: number;
  active: boolean;
  /** null — бессрочный код. */
  expiresAt: string | null;
  createdAt: string | null;
}

/** Поля формы промокода в админке: пустой code — сервер сам сгенерирует код. */
export interface PromoCodeForm {
  code: string;
  discountPercent: number | null;
  active: boolean;
  expiresAt: string | null;
}

/** Ответ проверки промокода в корзине: код + ПЕРЕСЧИТАННЫЕ сервером суммы. */
export interface PromoValidationResult {
  code: string;
  discountPercent: number;
  expiresAt: string | null;
  totals: TotalsDto;
  message: string;
}

export interface CatalogQuery {
  size?: string;
  available?: boolean;
  sort?: SortKey;
  page?: number;
  pageSize?: number;
  includeDisabled?: boolean;
}

/** Белый список сортировки, который понимает бэкенд (см. services/TShirtSort.java). */
export type SortKey = 'NEWEST' | 'OLDEST' | 'PRICE_ASC' | 'PRICE_DESC' | 'NAME_ASC';

export const SORT_OPTIONS: { value: SortKey; label: string }[] = [
  {value: 'NEWEST', label: 'Сначала новые'},
  {value: 'OLDEST', label: 'Сначала старые'},
  {value: 'PRICE_ASC', label: 'Цена: по возрастанию'},
  {value: 'PRICE_DESC', label: 'Цена: по убыванию'},
  {value: 'NAME_ASC', label: 'Название: А–Я'},
];
