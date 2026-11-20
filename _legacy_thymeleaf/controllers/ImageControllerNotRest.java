package com.example.appShop.сontrollers;

import com.example.appShop.entities.Image;
import com.example.appShop.repositories.ImageRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.List;

@Controller
public class ImageControllerNotRest {
    ImageRepository imageRepository;

    public ImageControllerNotRest(ImageRepository imageRepository) {
        this.imageRepository = imageRepository;
    }

    @PostMapping("/images/{id}")
    public String deleteImageById(@PathVariable Long id) {
        imageRepository.deleteById(id);
        return "redirect:/tshirts";
    }
    @GetMapping("/images/all")
    public String findAllImages(Model model) {
     List<Image> images = imageRepository.findAll();
        model.addAttribute("images", images);
        return "images";
    }
}
