package com.example.appShop.entities;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


@Entity
@Table(name = "tshirts")
public class TShirt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    @Column(name = "description", columnDefinition = "text")
    private String description;
    private String size;
    private Double price;
    private boolean available;
   @OneToMany(mappedBy = "tShirt", cascade = CascadeType.ALL,fetch = FetchType.LAZY,orphanRemoval = true)
    private List<Image> images = new ArrayList<>();
    private int previewImageId;
    private LocalDateTime dateOfCreated;
    private boolean isDisabled;

    /**
     * Тематическая коллекция (дроп). Поле необязательное: null = «вне коллекции».
     * fetch = EAGER сознательно: для связи «многие к одному» Hibernate доклеивает
     * LEFT OUTER JOIN, а не ходит за каждой карточкой отдельно. Так карточка товара
     * получает название и цвет бейджа бесплато, без N+1 запросов на страницу каталога.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "collection_id")
    private Collection collection;

    /**
     * Просмотры карточки товара. Счётчик считает НЕ запросы, а уникальные просмотры:
     * ViewCounterService проверяет связку «посетитель + товар + дата» (см. TrackingEvent).
     * nullable = false + columnDefinition — чтобы у новых колонок не было NULL в старых строках.
     */
    @Column(name = "views_count", nullable = false, columnDefinition = "bigint default 0")
    private Long viewsCount = 0L;

    public boolean isDisabled() {
        return isDisabled;
    }

    public void setDisabled(boolean isDisabled) {
        this.isDisabled = isDisabled;
    }

    public void addImageToTShirt(Image image){
        image.settShirt(this);
        images.add(image);
    }

    @PrePersist
    private void init(){
        dateOfCreated = LocalDateTime.now();
    }
    public TShirt(Long id, String name, String description, String size, Double price, boolean available) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.size = size;
        this.price = price;
        this.available = available;
    }

    public TShirt() {

    }

    public List<Image> getImages() {
        return images;
    }

    public void setImages(List<Image> images) {
        this.images = images;
    }

    public int getPreviewImageId() {
        return previewImageId;
    }

    public void setPreviewImageId(int previewImageId) {
        this.previewImageId = previewImageId;
    }

    public LocalDateTime getDateOfCreated() {
        return dateOfCreated;
    }

    public void setDateOfCreated(LocalDateTime dateOfCreated) {
        this.dateOfCreated = dateOfCreated;
    }

    public Long getId() {
        return id;
    }


    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSize() {
        return size;
    }

    public void setSize(String size) {
        this.size = size;
    }

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public Collection getCollection() {
        return collection;
    }

    public void setCollection(Collection collection) {
        this.collection = collection;
    }

    public long getViewsCount() {
        return viewsCount == null ? 0L : viewsCount;
    }

    public void setViewsCount(Long viewsCount) {
        this.viewsCount = viewsCount;
    }

    public void incrementViewsCount() {
        this.viewsCount = getViewsCount() + 1;
    }
}