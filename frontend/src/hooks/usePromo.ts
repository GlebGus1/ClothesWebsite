import {useCallback, useEffect, useMemo, useRef, useState} from 'react';
import {validatePromoCode} from '../api/promo';
import {toApiError} from '../api/http';
import {useCart} from '../context/CartContext';
import type {PromoValidationResult} from '../types/api';

/**
 * Промокод в корзине/оформлении — общий хук для обеих страниц.
 *
 * Разделение ответственности такое:
 *  - в localStorage живёт ТОЛЬКО сам код (строка), он переживает переход
 *    «корзина -> оформление»;
 *  - процент и денежные итоги каждый раз возвращает сервер
 *    (POST /api/promo-codes/validate).
 *
 * Почему так, а не «сохранить скидку в корзину»: localStorage правит любой
 * пользователь devtools. Код — просто подсказка серверу, а сумму, скидку и итог
 * считает бэкенд по ценам из БД (см. PromoCodeService.validate).
 *
 * Хук сам перепроверяет код, когда состав корзины изменился (товар убрали —
 * сумма стала другой), поэтому показанный «итог» не может устареть.
 */
export function usePromo(tshirtIds: number[]) {
  const {promoCode, setPromoCode} = useCart();
  const [applied, setApplied] = useState<PromoValidationResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  // Набор id как строка: два одинаковых состава корзины = одна и та же проверка,
  // даже если порядок добавления товаров отличается.
  const signature = useMemo(() => tshirtIds.slice().sort((a, b) => a - b).join(','), [tshirtIds]);
  const validated = useRef('');

  const run = useCallback(
    async (rawCode: string, idsSignature: string) => {
      // помечаем пару «код + состав корзины» проверенной ДО запроса: иначе успешный
      // setPromoCode ниже снова дёрнул бы эффект и мы получили бы бесконечный цикл
      validated.current = `${rawCode}|${idsSignature}`;
      const ids = idsSignature ? idsSignature.split(',').map(Number) : [];
      setBusy(true);
      try {
        const result = await validatePromoCode(rawCode, ids);
        setApplied(result);
        setError(null);
        setPromoCode(result.code);
        return result;
      } catch (err) {
        setApplied(null);
        setError(toApiError(err).message);
        return null;
      } finally {
        setBusy(false);
      }
    },
    [setPromoCode],
  );

  const applyCode = useCallback(
    (rawCode: string) => run(rawCode.trim().toUpperCase(), signature),
    [run, signature],
  );

  const clearCode = useCallback(() => {
    validated.current = '';
    setApplied(null);
    setError(null);
    setPromoCode(null);
  }, [setPromoCode]);

  // Первый рендер страницы: в корзине уже лежит код (пользователь вернулся на
  // страницу) или состав корзины изменился -> сервер пересчитывает суммы.
  useEffect(() => {
    const key = `${promoCode ?? ''}|${signature}`;
    if (!promoCode || !signature || validated.current === key) {
      return;
    }
    void run(promoCode, signature);
  }, [promoCode, signature, run]);

  return {applied, error, busy, applyCode, clearCode, savedCode: promoCode};
}
