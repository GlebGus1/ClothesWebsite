package com.example.appShop.сontrollers;

import com.example.appShop.entities.Image;
import com.example.appShop.entities.TShirt;
import com.example.appShop.repositories.TShirtRepository;
import com.example.appShop.services.TShirtsService;
import jakarta.servlet.http.HttpSession;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Controller
public class TShirtController {
    private String currentSize;
    private String currentOrder;
    private String currentAvailable;
    @Autowired
    private TShirtRepository tShirtRepository;

    @Autowired
    private TShirtsService tShirtsService;

    @GetMapping("/tshirts")
    public String getAllTShirts(Model model) {
        List<TShirt> tShirts = tShirtRepository.findAll();
        model.addAttribute("tShirts", tShirts);
        return "tshirts";
    }
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @GetMapping("/tshirts/create")
    public String showCreateForm(Model model) {
        model.addAttribute("tShirt", new TShirt());
        return "create-tshirt";
    }
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PostMapping("/tshirts/create")
    public String createTShirt(@ModelAttribute("tShirt") TShirt tShirt,
                               @RequestParam("file1") MultipartFile file1,
                               @RequestParam("file2") MultipartFile file2) throws IOException {
        tShirtsService.saveTShirt(tShirt, file1, file2);
        return "redirect:/tshirts";
    }
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PostMapping("/tshirts/delete/{id}")
    public String deleteTShirt(@PathVariable Long id) {
        tShirtRepository.deleteById(id);
        return "redirect:/tshirts";
    }
    @GetMapping("/tshirts/edit/{id}")
    public String showEditTShirtForm(@PathVariable("id") Long id, Model model) {
        TShirt tShirt = tShirtsService.getTShirtsById(id);
        model.addAttribute("tShirt", tShirt);
        return "editTShirt";
    }
    @PostMapping("/tshirts/edit/{id}")
    public String editTShirt(@PathVariable("id") Long id,
                             @ModelAttribute("tShirt") TShirt updatedTShirt,
                             @RequestParam("file1") MultipartFile file1,
                             @RequestParam("file2") MultipartFile file2) throws IOException {

        TShirt tShirt = tShirtsService.getTShirtsById(id);

        tShirt.setName(updatedTShirt.getName());
        tShirt.setDescription(updatedTShirt.getDescription());
        tShirt.setSize(updatedTShirt.getSize());
        tShirt.setPrice(updatedTShirt.getPrice());
        tShirt.setAvailable(updatedTShirt.isAvailable());

        if (!file1.isEmpty() || !file2.isEmpty()) {

            tShirt.getImages().clear();

            if (!file1.isEmpty()) {
                Image image1 = createImage(file1);
                image1.settShirt(tShirt);
                image1.setPreviewImage(true);
                tShirt.getImages().add(image1);
            }

            if (!file2.isEmpty()) {
                Image image2 = createImage(file2);
                image2.settShirt(tShirt);
                tShirt.getImages().add(image2);
            }
        }

        tShirtsService.saveTShirt(tShirt, file1, file2);

        return "redirect:/tshirts";
    }

    private Image createImage(MultipartFile file) throws IOException {
        Image image = new Image();
        image.setName(file.getOriginalFilename());
        image.setOriginalFileName(file.getOriginalFilename());
        image.setSize(file.getSize());
        image.setContentType(file.getContentType());
        image.setBytes(file.getBytes());
        return image;
    }


    @GetMapping("/tshirts/sortByPrice")
    public String sortByPrice(Model model, @RequestParam("order") String order) {
        List<TShirt> tShirts;
        if (currentSize != null && !currentSize.isEmpty()) {
            tShirts = tShirtsService.getTShirtsBySize(currentSize);
        } else {
            tShirts = tShirtsService.getAllTShirts();
        }
        if (order.equals("asc")) {
            tShirts.sort(Comparator.comparing(TShirt::getPrice));
        } else {
            tShirts.sort(Comparator.comparing(TShirt::getPrice).reversed());
        }
        model.addAttribute("tShirts", tShirts);
        currentOrder = order;
        return "tshirts";
    }

    @GetMapping("/tshirts/sortByAvailable")
    public String sortByAvailable(Model model, @RequestParam("available") String available) {
        List<TShirt> tShirts;
        if (currentSize != null && !currentSize.isEmpty()) {
            tShirts = tShirtsService.getTShirtsBySize(currentSize);
        } else if (currentOrder != null && !currentOrder.isEmpty()) {
            tShirts = tShirtsService.getAllTShirts();
            if (currentOrder.equals("asc")) {
                tShirts.sort(Comparator.comparing(TShirt::getPrice));
            } else {
                tShirts.sort(Comparator.comparing(TShirt::getPrice).reversed());
            }
        } else {
            tShirts = tShirtsService.getAllTShirts();
        }
        if (available.equals("true")) {
            tShirts = tShirts.stream()
                    .filter(TShirt::isAvailable)
                    .collect(Collectors.toList());
        } else if (available.equals("false")) {
            boolean hasFalseAvailable = tShirts.stream()
                    .anyMatch(tshirt -> !tshirt.isAvailable());
            if (!hasFalseAvailable) {
                tShirts = Collections.emptyList();
            }
        }
        model.addAttribute("tShirts", tShirts);
        currentAvailable = available;

        return "tshirts";
    }

    @GetMapping("/tshirts/sortBySize")
    public String sortBySize(@RequestParam("size") String size, Model model) {
        currentSize = size;
        List<TShirt> tShirts;
        if (size.isEmpty()) {
            tShirts = tShirtsService.getAllTShirts();
        } else {
            tShirts = tShirtsService.getTShirtsBySize(size);
        }
        model.addAttribute("tShirts", tShirts);
        return "tshirts";
    }
}