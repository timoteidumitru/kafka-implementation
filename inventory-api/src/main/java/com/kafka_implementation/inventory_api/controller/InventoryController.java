package com.kafka_implementation.inventory_api.controller;

import com.kafka_implementation.inventory_api.entity.Product;
import com.kafka_implementation.inventory_api.service.InventoryService;
import com.kafka_implementation.shared.dto.StockUpdateRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping()
    public String listProducts(Model model) {
        List<Product> products = inventoryService.getAllProducts();
        model.addAttribute("products", products);
        return "inventory-list";
    }

    @GetMapping("/add-product")
    public String showAddProductForm(Model model) {
        model.addAttribute("product", new Product());
        return "product-form";
    }

    @PostMapping("/add-product")
    public String addProduct(@ModelAttribute Product product, Model model) {
        inventoryService.addProduct(product);
        model.addAttribute("message", "✅ Product added successfully!");
        return "redirect:/inventory";
    }

    @GetMapping("/update-stock")
    public String showStockUpdateForm(Model model) {
        model.addAttribute("stockUpdateRequest", new StockUpdateRequest());
        return "stock-update-form";
    }

    @PostMapping("/update-stock")
    public String updateStock(@ModelAttribute StockUpdateRequest request, Model model) {
        inventoryService.updateStock(request);
        model.addAttribute("message", "✅ Stock updated successfully!");
        return "redirect:/inventory";
    }

    @GetMapping("/stock")
    public String getStock(@RequestParam Long productId, Model model) {
        Optional<Integer> stock = inventoryService.getStock(productId);
        model.addAttribute("stock", stock.orElse(0));
        return "stock-view";
    }
}
