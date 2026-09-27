package com.example.appShop.сontrollers;

import com.example.appShop.entities.TShirt;
import com.example.appShop.repositories.TShirtRepository;
import jakarta.servlet.http.HttpSession;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Controller
public class CartController {
    @Autowired
    private TShirtRepository tShirtRepository;

    @GetMapping("/cart")
    public String showCart(Model model, HttpSession session){
        List<TShirt> cartItems = (List<TShirt>) session.getAttribute("cartItems");
        model.addAttribute("cartItems", cartItems);
        return "cart";
    }
    @RequestMapping(value = "/cart/remove", method = {RequestMethod.GET, RequestMethod.POST})
    public String removeFromCart(@RequestParam("tShirtid") Long id, HttpSession session) {
        List<TShirt> cartItems = (List<TShirt>) session.getAttribute("cartItems");
        if (cartItems != null) {
            TShirt tShirtToRemove = null;
            for (TShirt tShirt : cartItems) {
                if (tShirt.getId() == id) {
                    tShirtToRemove = tShirt;
                    break;
                }
            }
            if (tShirtToRemove != null) {
                cartItems.remove(tShirtToRemove);

            }
        }
        session.setAttribute("cartItems", cartItems);
        return "redirect:/cart";
    }
    @RequestMapping(value = "/cart/add", method = {RequestMethod.GET, RequestMethod.POST})
    public String addToCart(@RequestParam("tShirtid") Long id, HttpSession session, Model model) {
        List<TShirt> cartItems = (List<TShirt>) session.getAttribute("cartItems");
        if (cartItems == null){
            cartItems = new ArrayList<>();
        }


        boolean itemExists = false;
        for (TShirt tShirt : cartItems) {
            if (tShirt.getId() == id) {
                itemExists = true;
                break;
            }
        }
        if (!itemExists) {
            TShirt tShirt = tShirtRepository.findById(id).orElse(null);
            Hibernate.initialize(tShirt.getImages());
            cartItems.add(tShirt);
            session.setAttribute("cartItems", cartItems);
            model.addAttribute("itemAdded", true);
        } else {
            Iterator<TShirt> iterator = cartItems.iterator();
            while (iterator.hasNext()) {
                TShirt tShirt = iterator.next();
                if (tShirt.getId() == id) {
                    iterator.remove();
                    break;
                }
            }
            session.setAttribute("cartItems", cartItems);
            model.addAttribute("itemAdded", false);
        }

        return "redirect:/tshirts";
    }
}
