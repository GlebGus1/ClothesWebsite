package com.example.appShop.services;

import com.example.appShop.entities.Image;
import com.example.appShop.exceptions.InvalidRequestException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * Правила загрузки картинок: что вообще считать картинкой и какого размера.
 *
 * Без такой «политики» загрузка файлов — дыра: можно залить .exe на 2 ГБ или
 * файл с content-type "image/png", внутри которого произвольные байты.
 * Проверку по content-type дополняем списком разрешённых типов, а лимиты
 * выставлены и в application.properties (multipart), и здесь — defence in depth.
 */
public final class ImageUploadPolicy {

    public static final long MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024;
    public static final int MAX_FILES_PER_REQUEST = 5;

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/png", "image/jpeg", "image/jpg", "image/webp", "image/gif");

    /**
     * Первые байты настоящих картинок («магические числа»). Content-type присылает
     * браузер, ему нельзя верить: его подделывает и вредоносный файл, и кривый клиент.
     */
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] GIF87 = {'G', 'I', 'F', '8', '7', 'a'};
    private static final byte[] GIF89 = {'G', 'I', 'F', '8', '9', 'a'};
    private static final byte[] RIFF = {'R', 'I', 'F', 'F'};
    private static final byte[] WEBP = {'W', 'E', 'B', 'P'};

    private ImageUploadPolicy() {
    }

    public static void checkCount(List<MultipartFile> files) {
        if (files != null && files.size() > MAX_FILES_PER_REQUEST) {
            throw new InvalidRequestException("Можно загрузить не больше " + MAX_FILES_PER_REQUEST + " файлов");
        }
    }

    public static boolean isEmpty(MultipartFile file) {
        return file == null || file.isEmpty();
    }

    public static Image toImageEntity(MultipartFile file) {
        if (isEmpty(file)) {
            throw new InvalidRequestException("Пустой файл");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new InvalidRequestException("Файл больше 5 МБ: " + file.getOriginalFilename());
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType.toLowerCase())) {
            throw new InvalidRequestException("Разрешены только изображения: PNG, JPEG, WEBP, GIF");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new InvalidRequestException("Не удалось прочитать файл " + file.getOriginalFilename());
        }
        if (!looksLikeImage(bytes)) {
            throw new InvalidRequestException(
                    "Файл " + file.getOriginalFilename() + " по содержимому не похож на изображение");
        }

        Image image = new Image();
        // В старом коде было file.getName() — это имя поля формы, а не файла. Исправлено.
        image.setName(file.getOriginalFilename());
        image.setOriginalFileName(file.getOriginalFilename());
        image.setContentType(contentType);
        image.setSize(file.getSize());
        image.setBytes(bytes);
        return image;
    }

    /** Проверка «магических чисел»: PNG/JPEG/GIF/WEBP. Остальные форматы не пропускаем. */
    static boolean looksLikeImage(byte[] bytes) {
        if (bytes == null || bytes.length < 12) {
            return false;
        }
        return startsWith(bytes, PNG, 0)
                || startsWith(bytes, JPEG, 0)
                || startsWith(bytes, GIF87, 0)
                || startsWith(bytes, GIF89, 0)
                || (startsWith(bytes, RIFF, 0) && startsWith(bytes, WEBP, 8));
    }

    private static boolean startsWith(byte[] bytes, byte[] signature, int offset) {
        if (bytes.length < offset + signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if (bytes[offset + i] != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
