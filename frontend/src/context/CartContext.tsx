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

interface CartContextValue {
  items: CartItem[];
  add: (product: CartItem) => void;
  remove: (id: number) => void;
  clear: () => void;
  has: (id: number) => boolean;
  count: number;
  total: number;
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

export function CartProvider({children}: {children: ReactNode}) {
  const [items, setItems] = useState<CartItem[]>(readCart);

  useEffect(() => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(items));
  }, [items]);

  const add = useCallback<CartContextValue['add']>((product) => {
    setItems((current) =>
      current.some((item) => item.id === product.id)
        ? current
        : [...current, {id: product.id, name: product.name, size: product.size ?? null, price: product.price ?? null, previewImageId: product.previewImageId ?? null}],
    );
  }, []);

  const remove = useCallback((id: number) => setItems((current) => current.filter((item) => item.id !== id)), []);
  const clear = useCallback(() => setItems([]), []);
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
    }),
    [items, add, remove, clear, has],
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
