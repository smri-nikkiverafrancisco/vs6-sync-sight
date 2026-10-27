package com.vs6.syncsight.shared.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * The front door: opening the site root sends you to the Starting Promotions page for now.
 */
@Controller
public class HomeController {

    @GetMapping("/")
    public String home() {
        return "redirect:/promotions/ongoing/starting";
    }
}