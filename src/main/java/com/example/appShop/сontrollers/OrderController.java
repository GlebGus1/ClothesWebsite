package com.example.appShop.сontrollers;

import com.example.appShop.entities.Order;
import com.example.appShop.entities.TShirt;
import com.example.appShop.entities.User;
import com.example.appShop.repositories.OrderRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
public class OrderController {
    @Autowired
    private OrderRepository orderRepository;

    @RequestMapping(value = "/cart/create", method = RequestMethod.POST)
    public String createOrder(@RequestParam("nameOfUser") String nameOfUser,
                              @RequestParam("postOffice") String postOffice,
                              @RequestParam("postalCode") String postalCode,
                              @RequestParam("phoneNumber") String phoneNumber,
                              HttpSession session) {

        // ЭТИ СТРОКИ ПОМОГУТ НАМ УВИДЕТЬ ЗАПРОС В КОНСОЛИ IDEA:
        System.out.println("=== МЕТОД СОЗДАНИЯ ЗАКАЗА ВЫЗВАН ===");
        System.out.println("Имя: " + nameOfUser + ", Индекс: " + postalCode);

        List<TShirt> cartItems = (List<TShirt>) session.getAttribute("cartItems");
        if (cartItems == null || cartItems.isEmpty()) {
            System.out.println("Ошибка: Корзина в сессии пуста!");
            return "redirect:/cart?error=empty"; // Перенаправляем обратно в корзину с ошибкой
        }

        Order order = new Order();
        order.setNameOfUser(nameOfUser);
        order.setPostOffice(postOffice);
        order.setPostalCode(postalCode);
        order.setPhoneNumber(phoneNumber);
        order.setOrderStatus(Order.OrderStatus.PROCESSING);

        // БЕЗОПАСНОЕ ПОЛУЧЕНИЕ ПОЛЬЗОВАТЕЛЯ:
        User user = (User) session.getAttribute("user");
        if (user != null) {
            order.setUser(user);
        } else {
            System.out.println("Предупреждение: Заказ оформляет неавторизованный гость");
            order.setUser(null); // База данных должна разрешать null для user_id в таблице orders
        }

        order.setTshirts(cartItems);

        try {
            orderRepository.save(order);
            System.out.println("УСПЕХ: Заказ сохранен в базу данных!");
        } catch (Exception e) {
            System.out.println("КРИТИЧЕСКАЯ ОШИБКА СОХРАНЕНИЯ В БД: " + e.getMessage());
            e.printStackTrace();
            return "redirect:/cart?error=database";
        }

        session.removeAttribute("cartItems");
        return "redirect:/tshirts";
    }

    @GetMapping("/orders")
    public String getAllOrders(Model model) {
        List<Order> orders = orderRepository.findAll();
        model.addAttribute("orders", orders);
        return "orders";
    }
}
