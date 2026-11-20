package com.example.appShop.dto;

/** Не JSON, а «кусок бинарника + метаданные», чтобы контроллер мог собрать ResponseEntity<byte[]>. */
public record ImageBinary(byte[] content, String contentType, String fileName, long size) {
}
