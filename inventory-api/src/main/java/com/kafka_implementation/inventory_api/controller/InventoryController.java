package com.kafka_implementation.inventory_api.controller;

import com.kafka_implementation.inventory_api.entity.Product;
import com.kafka_implementation.inventory_api.service.InventoryService;
import com.kafka_implementation.shared.dto.OrderEvent;
import com.kafka_implementation.shared.dto.ProductDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@Tag(name = "Inventory", description = "Inventory API for managing products and stock")
@Controller
@RequestMapping("/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @Operation(summary = "List all products", description = "Displays all available products in the inventory")
    @ApiResponse(responseCode = "200", description = "Product list retrieved successfully")
    @GetMapping()
    public String listProducts(Model model) {
        List<Product> products = inventoryService.getAllProducts();
        model.addAttribute("products", products);
        return "inventory-list";
    }

    @Operation(summary = "Show Add Product Form", description = "Returns the form for adding a new product")
    @GetMapping("/add-product")
    public String showAddProductForm(Model model) {
        model.addAttribute("product", new Product());
        return "product-form";
    }

    @Operation(summary = "Add a new product", description = "Saves a new product to the inventory")
    @ApiResponse(responseCode = "201", description = "Product added successfully")
    @PostMapping("/add-product")
    public String addProduct(@ModelAttribute Product product, Model model) {
        inventoryService.addProduct(product);
        model.addAttribute("message", "✅ Product added successfully!");
        return "redirect:/inventory";
    }

    @Operation(summary = "Show Stock Update Form", description = "Returns the form for updating stock")
    @GetMapping("/update-stock")
    public String showStockUpdateForm(Model model) {
        model.addAttribute("stockUpdateRequest", new OrderEvent());
        return "stock-update-form";
    }

    @Operation(summary = "Update stock based on an OrderEvent", description = "Updates stock quantity for products")
    @ApiResponse(responseCode = "200", description = "Stock updated successfully")
    @PostMapping("/update-stock")
    public String updateStock(@ModelAttribute OrderEvent request, Model model) {
        inventoryService.updateStock(request);
        List<Product> products = inventoryService.getAllProducts();
        model.addAttribute("products", products);
        return "inventory-list";
    }

    @Operation(summary = "Get stock for a product", description = "Returns the current stock level for a product by ID")
    @ApiResponse(responseCode = "200", description = "Stock retrieved successfully")
    @GetMapping("/stock")
    public String getStock(@RequestParam Long productId, Model model) {
        Optional<Integer> stock = inventoryService.getStock(productId);
        model.addAttribute("stock", stock.orElse(0));
        return "stock-view";
    }

    @Operation(summary = "Get all products (JSON)", description = "Returns all products in JSON format")
    @ApiResponse(responseCode = "200", description = "Product list retrieved successfully")
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

    @Operation(summary = "Update stock via API", description = "Updates stock quantity based on an OrderEvent (JSON request)")
    @ApiResponse(responseCode = "200", description = "Stock updated successfully")
    @PostMapping("/update-stock-api")
    @ResponseBody
    public String updateStockAPI(@RequestBody OrderEvent request) {
        inventoryService.updateStock(request);
        return "Stock updated successfully";
    }
}
