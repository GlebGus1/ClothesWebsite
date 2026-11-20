/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Базовый URL REST API. По умолчанию '/api' — тогда работает прокси Vite в dev-режиме. */
  readonly VITE_API_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
