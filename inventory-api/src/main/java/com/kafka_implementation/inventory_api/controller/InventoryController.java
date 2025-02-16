package com.kafka_implementation.inventory_api.controller;

import com.kafka_implementation.inventory_api.entity.Product;
import com.kafka_implementation.inventory_api.service.InventoryService;
import com.kafka_implementation.shared.dto.OrderEvent;
import com.kafka_implementation.shared.dto.ProductDTO;
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
        model.addAttribute("stockUpdateRequest", new OrderEvent());
        return "stock-update-form";
    }

    @PostMapping("/update-stock")
    public String updateStock(@ModelAttribute OrderEvent request, Model model) {
        inventoryService.updateStock(request);
        List<Product> products = inventoryService.getAllProducts();
        model.addAttribute("products", products);
        return "inventory-list";
    }

    @GetMapping("/stock")
    public String getStock(@RequestParam Long productId, Model model) {
        Optional<Integer> stock = inventoryService.getStock(productId);
        model.addAttribute("stock", stock.orElse(0));
        return "stock-view";
    }

    @GetMapping("/products")
    @ResponseBody
    public List<ProductDTO> getProductsAsJson() {
        List<Product> products = inventoryService.getAllProducts();
        return products.stream().map(product -> new ProductDTO(
                product.getProductCode(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getCategory(),
                product.getStock()
        )).toList();
    }

    @PostMapping("/update-stock-api")
    @ResponseBody
    public String updateStockAPI(@RequestBody OrderEvent request) {
        inventoryService.updateStock(request);
        return "Stock updated successfully";
    }

}
