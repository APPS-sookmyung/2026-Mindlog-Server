package com.apps.mindlog.home.controller;

import com.apps.mindlog.home.dto.HomeResponse;
import com.apps.mindlog.home.service.HomeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path="/api/home",version="1")
public class HomeController {
    private final HomeService homes;
    public HomeController(HomeService homes){this.homes=homes;}
    @GetMapping public HomeResponse getHome(){return homes.getHome();}
}
