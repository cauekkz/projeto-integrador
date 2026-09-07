package br.com.vanroute.backend.controllers;

import br.com.vanroute.backend.services.StorageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/storage")
public class StorageController {

    private final StorageService storageService;

    public StorageController(StorageService storageService) {
        this.storageService = storageService;
    }

    @GetMapping("/presigned-url")
    public ResponseEntity<StorageService.PresignedUrlResponse> getPresignedUrl(
            @RequestParam String fileName,
            @RequestParam(defaultValue = "application/octet-stream") String contentType) {
        
        StorageService.PresignedUrlResponse response = storageService.generatePresignedUrl(fileName, contentType);
        return ResponseEntity.ok(response);
    }
}
