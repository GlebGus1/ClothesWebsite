package com.example.appShop.сontrollers;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
@Controller
public class DeliveryController {


    @GetMapping("/delivery")
    public String showDeliveryForm(Model model) {
        List<String> streets = getAllStreets();
        model.addAttribute("streets", streets);
        return "delivery-form";
    }
    public List<String> getAllStreets() {
        return null;
    }
}
