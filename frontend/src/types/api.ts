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
  /** e-mail подтверждён PIN-кодом (иначе вход закрыт). */
  enabled: boolean;
  /** Аккаунт заблокирован администратором (чёрный список). */
  banned: boolean;
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
  /** Коллекция (дроп). null — товар «вне коллекции». */
  collectionId: number | null;
  collectionName: string | null;
  /** Цвет акцента #RRGGBB из настроек коллекции (null — цвет по умолчанию). */
  collectionBadgeColor: string | null;
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
  /** Уникальные просмотры карточки (анти-фрод: не больше одного на посетителя в сутки). */
  viewsCount: number;
}

/** Поля формы товара (часть multipart-запроса "data"). */
export interface TShirtForm {
  name: string;
  description: string;
  size: string;
  price: number | null;
  available: boolean;
  /** null / 0 — «без коллекции». */
  collectionId: number | null;
}

export type OrderStatus = 'PROCESSING' | 'IN_TRANSIT' | 'DELIVERED' | 'CANCELED';

export interface OrderItemDto {
  id: number;
  name: string;
  size: string | null;
  price: number | null;
  /** Обложка позиции: в таблице заказов админ видит мини-карточку с фото. */
  previewImageId: number | null;
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
  collectionId?: number;
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

/**
 * Коллекция (дроп). Приходит и с публичного GET /api/collections (только активные),
 * и из админки (там же productsCount — сколько товаров на полке).
 */
export interface CollectionDto {
  id: number;
  name: string;
  description: string | null;
  badgeColor: string | null;
  active: boolean;
  createdAt: string | null;
  productsCount: number;
}

/** Поля формы коллекции в админке (пустой badgeColor — цвет по умолчанию). */
export interface CollectionForm {
  name: string;
  description: string;
  badgeColor: string;
  active: boolean;
}

/**
 * Строка таблицы «Товары (админ)»: чем публичной карточки больше —
 * viewsCount и soldInOrderId (в каком заказе ушла вещь). наружу их не показываем.
 */
export interface AdminTShirtDto {
  id: number;
  name: string;
  size: string | null;
  price: number | null;
  available: boolean;
  disabled: boolean;
  dateOfCreated: string | null;
  previewImageId: number | null;
  viewsCount: number;
  collectionId: number | null;
  collectionName: string | null;
  soldInOrderId: number | null;
}

/** Ответ GET /api/auth/captcha-status: нужен ли виджет Cloudflare Turnstile. */
export interface CaptchaStatus {
  required: boolean;
  siteKey: string | null;
  message: string | null;
}

/** Результат ручного возврата позиций отменённого заказа в продажу. */
export interface RestoreItemsResult {
  order: OrderDto;
  restoredCount: number;
  /** Позиции, которые не вернули в продажу, с причиной в тексте. */
  skippedNames: string[];
}

/** Шаг воронки «визиты → просмотры → корзина → заказы». */
export interface FunnelStepDto {
  key: 'visits' | 'productViews' | 'cartAdds' | 'orders';
  label: string;
  value: number;
  /** Конверсия от предыдущего шага, %. */
  conversionFromPrevious: number;
  /** Конверсия от визитов, %. */
  conversionFromVisits: number;
}

export interface TopProductDto {
  id: number;
  name: string;
  size: string | null;
  price: number | null;
  previewImageId: number | null;
  available: boolean;
  /** Просмотры или «сердечки» — зависит от таблицы. */
  metric: number;
}

export interface AnalyticsDashboardDto {
  revenue: number;
  discountsAmount: number;
  subtotal: number;
  ordersCount: number;
  cancelledOrdersCount: number;
  productsCount: number;
  customersCount: number;
  funnel: {
    visits: number;
    productViews: number;
    cartAdds: number;
    orders: number;
    steps: FunnelStepDto[];
  };
  mostViewed: TopProductDto[];
  mostFavorited: TopProductDto[];
}

/** Отчёт «как использован промокод» (отменённые заказы в него не входят). */
export interface PromoCodeUsageDto {
  code: string;
  timesUsed: number;
  ordersSubtotal: number;
  ordersTotal: number;
  /** Упущенная выгода: сколько магазин не получил из-за скидки. */
  discountAmount: number;
  orders: {
    orderId: number;
    customerEmail: string | null;
    nameOfUser: string | null;
    createdAt: string | null;
    status: OrderStatus;
    discountPercent: number;
    subtotal: number;
    discountAmount: number;
    total: number;
  }[];
}
