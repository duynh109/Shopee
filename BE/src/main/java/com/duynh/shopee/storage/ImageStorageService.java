package com.duynh.shopee.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.duynh.shopee.exception.FieldValidationException;

@Service
public class ImageStorageService {
    private static final Logger logger = LoggerFactory.getLogger(ImageStorageService.class);
    private static final byte[] JPEG_MAGIC = new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF };
    private static final byte[] PNG_MAGIC = new byte[] { (byte) 0x89, (byte) 0x50, (byte) 0x4E, (byte) 0x47,
            (byte) 0x0D, (byte) 0x0A, (byte) 0x1A, (byte) 0x0A };

    private final Path root;
    private final String publicUrl;

    public ImageStorageService(@Value("${app.upload.dir}") String uploadDir,
            @Value("${app.upload.public-url}") String publicUrl) {
        this.root = Path.of(uploadDir).toAbsolutePath().normalize();
        this.publicUrl = publicUrl;
        try {
            Files.createDirectories(this.root);
        } catch (IOException e) {
            throw new UncheckedIOException("Không tạo được thư mục upload " + root, e);
        }
        logger.info("Ảnh upload lưu tại {}", root);
    }

    /**
     * Lưu một ảnh client gửi lên, trả về URL công khai của nó.
     * Gọi từ: POST /api/admin/upload-image và POST /api/users/me/avatar.
     * Chỉ lưu file, không đụng DB — muốn gắn URL vào sản phẩm hay avatar thì FE gọi
     * tiếp API sửa tương ứng.
     */
    public String store(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new FieldValidationException("image", "Vui lòng chọn ảnh");
        }
        String extension = detectExtension(image);
        if (extension == null) {
            throw new FieldValidationException("image", "Chỉ nhận ảnh PNG hoặc JPEG");
        }
        String filename = UUID.randomUUID() + "." + extension;
        Path target = root.resolve(filename);
        try (InputStream in = image.getInputStream()) {
            Files.copy(in, target);
        } catch (IOException e) {
            throw new UncheckedIOException("Không lưu được file " + filename, e);
        }
        return publicUrl + filename;
    }

    /**
     * Đoán định dạng từ NỘI DUNG file, không từ tên file hay Content-Type — hai thứ
     * đó client ghi gì cũng được.
     * Trả "jpg" / "png"; không nhận ra thì null.
     * Gọi getInputStream() ở đây rồi gọi lại ở store() không sao: mỗi lần gọi là mở
     * một stream mới từ đầu.
     */
    private String detectExtension(MultipartFile image) {
        byte[] head;
        // try-with-resources: stream tự đóng khi ra khỏi khối, kể cả khi có exception.
        // IOException là checked exception — lỗi đọc file tạm là lỗi hệ thống, người
        // gọi không làm gì được,
        // nên gói vào UncheckedIOException để nó bay thẳng lên GlobalExceptionHandler →
        // 500.
        try (InputStream in = image.getInputStream()) {
            head = in.readNBytes(8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (startsWith(head, PNG_MAGIC)) {
            return "png";
        } else if (startsWith(head, JPEG_MAGIC)) {
            return "jpg";
        } else {
            return null;
        }
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        return data.length >= prefix.length
                && Arrays.equals(data, 0, prefix.length, prefix, 0, prefix.length);
    }
}
