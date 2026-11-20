package com.example.appShop.сontrollers;

import com.example.appShop.entities.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

@Controller
public class UserProfileController {
    @GetMapping("/profile")
    public String showUserProfile(Model model, Principal principal) {
        String email = principal.getName();
        model.addAttribute("user", new User(email));
       return "profile";
    }
    @GetMapping("/checkAuth")
    @ResponseBody
    public Map<String, Boolean> checkAuthentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Map<String, Boolean> result = new HashMap<>();
        result.put("authenticated", authentication != null && authentication.isAuthenticated());
        result.put("enabled", authentication != null && authentication.getPrincipal() instanceof User && ((User) authentication.getPrincipal()).isEnabled());
        return result;
    }
}
