package com.oilpricedbmanager.controller;

import com.oilpricedbmanager.dto.DiscountResponse;
import com.oilpricedbmanager.repository.DiscountRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/discounts")
public class DiscountController {
    private final DiscountRepository discountRepository;

    public DiscountController(DiscountRepository discountRepository) {
        this.discountRepository = discountRepository;
    }

    @GetMapping
    public List<DiscountResponse> activeDiscounts() {
        return discountRepository.findActive();
    }
}
