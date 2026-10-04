package com.duynh.shopee.storage;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUploadController {
    private final ImageStorageService imageStorageService;

    public AdminUploadController(ImageStorageService imageStorageService) {
        this.imageStorageService = imageStorageService;
    }

    /**
     * Admin tải ảnh sản phẩm lên, nhận URL để điền vào image / images[] khi tạo
     * hoặc sửa sản phẩm.
     * consumes = multipart: gửi JSON vào đây nhận 415 rõ ràng.
     * required = false: thiếu field "image" thì để service trả 422 có tên field,
     * giống thiếu field trong JSON
     * (để required = true thì Spring ném lỗi riêng và ra 400/500 thay vì 422).
     */
    @PostMapping(value = "/upload-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public UploadImageResponse uploadImage(@RequestParam(value = "image", required = false) MultipartFile image) {
        String imageUrl = imageStorageService.store(image);
        return new UploadImageResponse(imageUrl);
    }
}
