package com.wekers.microsb.controller;

import com.wekers.microsb.service.AutocompleteService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/catalogo")
public class SuggestController {

    private final AutocompleteService service;

    public SuggestController(AutocompleteService service) {
        this.service = service;
    }

    @GetMapping("/suggest")
    public List<String> suggest(@RequestParam String prefix) {
        return service.suggest(prefix);
    }
}