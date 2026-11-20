package com.example.appShop.rest;

import com.example.appShop.dto.ImageBinary;
import com.example.appShop.services.ImageService;
import com.example.appShop.services.TShirtService;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Отдача картинок. GET доступен гостям — браузер сам делает этот запрос внутри <img src="...">,
 * и заголовок Authorization туда подставить нельзя.
 *
 * DELETE — только для админа (правило в SecurityConfig).
 */
@RestController
@RequestMapping("/api/images")
public class ImageController {

    private final ImageService imageService;
    private final TShirtService tShirtService;

    public ImageController(ImageService imageService, TShirtService tShirtService) {
        this.imageService = imageService;
        this.tShirtService = tShirtService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<byte[]> get(@PathVariable long id) {
        ImageBinary image = imageService.getBinary(id);

        ContentDisposition disposition = ContentDisposition.inline()
                .filename(image.fileName() == null ? ("image-" + id) : image.fileName(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .contentLength(image.size())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                // картинки меняются редко — кэшируем, чтобы не гонять longblob по каждому запросу
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic())
                .body(image.content());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        tShirtService.deleteImage(id);
        return ResponseEntity.noContent().build();
    }
}
