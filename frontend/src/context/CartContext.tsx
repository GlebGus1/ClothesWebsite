import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';

/**
 * Корзина. Важно: позиция заказа = товар (размер у товара один). Бэкенд в
 * OrderService схлопывает tshirtIds в LinkedHashSet, поэтому количество здесь не нужно —
 * дубликаты отсекаются и во фронтенде, и на сервере (проверяется на сервере!).
 *
 * В корзине лежат только id/название/размер/цена/обложка — их достаточно для
 * отображения. Подойдёт как TShirtDto из каталога, так и TShirtDetailsDto со
 * страницы товара: лишние поля структурно не мешают, а цены «из воздуха» тут
 * нет — итог к заказу сервер считает сам по базе.
 */
export interface CartItem {
  id: number;
  name: string;
  size: string | null;
  price: number | null;
  previewImageId: number | null;
}

const STORAGE_KEY = 'clothes.cart';
/** Промокод храним отдельным ключом: применённый код должен пережить переход
 * «корзина -> оформление», но это ВСЕГО ЛИШЬ строка — скидку считает сервер. */
const PROMO_KEY = 'clothes.promo';

interface CartContextValue {
  items: CartItem[];
  add: (product: CartItem) => void;
  remove: (id: number) => void;
  clear: () => void;
  has: (id: number) => boolean;
  count: number;
  total: number;
  /** Применённый промокод (null — без скидки). */
  promoCode: string | null;
  setPromoCode: (code: string | null) => void;
}

const CartContext = createContext<CartContextValue | null>(null);

function readCart(): CartItem[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    const parsed = raw ? (JSON.parse(raw) as CartItem[]) : [];
    return Array.isArray(parsed) ? parsed.filter((item) => typeof item?.id === 'number') : [];
  } catch {
    return [];
  }
}

function readPromoCode(): string | null {
  try {
    const raw = localStorage.getItem(PROMO_KEY);
    return raw && raw.trim() ? raw.trim().toUpperCase() : null;
  } catch {
    return null;
  }
}

export function CartProvider({children}: {children: ReactNode}) {
  const [items, setItems] = useState<CartItem[]>(readCart);
  const [promoCode, setPromoCodeState] = useState<string | null>(readPromoCode);

  useEffect(() => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(items));
  }, [items]);

  useEffect(() => {
    if (promoCode) {
      localStorage.setItem(PROMO_KEY, promoCode);
    } else {
      localStorage.removeItem(PROMO_KEY);
    }
  }, [promoCode]);

  const add = useCallback<CartContextValue['add']>((product) => {
    setItems((current) =>
      current.some((item) => item.id === product.id)
        ? current
        : [...current, {id: product.id, name: product.name, size: product.size ?? null, price: product.price ?? null, previewImageId: product.previewImageId ?? null}],
    );
  }, []);

  const remove = useCallback((id: number) => setItems((current) => current.filter((item) => item.id !== id)), []);
  // вместе с заказом снимаем и промокод: код «одноразовый» для пользователя,
  // повторять его на следующий заказ — уже другой сценарий
  const clear = useCallback(() => {
    setItems([]);
    setPromoCodeState(null);
  }, []);
  const setPromoCode = useCallback((code: string | null) => {
    setPromoCodeState(code ? code.trim().toUpperCase() : null);
  }, []);
  const has = useCallback((id: number) => items.some((item) => item.id === id), [items]);

  const value = useMemo<CartContextValue>(
    () => ({
      items,
      add,
      remove,
      clear,
      has,
      count: items.length,
      total: items.reduce((sum, item) => sum + (item.price ?? 0), 0),
      promoCode,
      setPromoCode,
    }),
    [items, add, remove, clear, has, promoCode, setPromoCode],
  );

  return <CartContext.Provider value={value}>{children}</CartContext.Provider>;
}

export function useCart(): CartContextValue {
  const context = useContext(CartContext);
  if (!context) {
    throw new Error('useCart можно вызывать только внутри <CartProvider>');
  }
  return context;
}
