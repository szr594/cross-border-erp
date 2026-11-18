package com.szr.crosserp.identity.user;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DisplayDirectoryController {
    private final DisplayDirectoryService service;
    public DisplayDirectoryController(DisplayDirectoryService service) { this.service = service; }
    @GetMapping("/internal/identity/user-displays")
    public List<DisplayDirectoryService.Entry> entries() { return service.entries(); }
}
