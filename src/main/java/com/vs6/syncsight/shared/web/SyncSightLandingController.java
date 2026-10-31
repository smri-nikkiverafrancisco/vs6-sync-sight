package com.vs6.syncsight.shared.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SyncSightLandingController {

    @GetMapping({"/sync-sight", "/sync-sight/"})
    public String landing() {
        return "sync-sight/index";
    }

    // Short links: /sync-sight/promotions and /sync-sight/elements go to their first page
    @GetMapping({"/sync-sight/promotions", "/sync-sight/promotions/"})
    public String promotions() {
        return "redirect:/sync-sight/promotions/ongoing/starting";
    }

    @GetMapping({"/sync-sight/elements", "/sync-sight/elements/"})
    public String elements() {
        return "redirect:/sync-sight/elements/ongoing/starting";
    }
}