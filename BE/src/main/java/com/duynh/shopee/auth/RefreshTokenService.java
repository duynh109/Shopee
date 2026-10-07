package com.duynh.shopee.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.duynh.shopee.user.PasswordChangedEvent;
import com.duynh.shopee.user.User;

@Service
public class RefreshTokenService {
    private static final Logger logger = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;
    private final long refreshExpirationSeconds;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository,
            @Value("${jwt.refresh-expiration-seconds}") long refreshExpirationSeconds) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshExpirationSeconds = refreshExpirationSeconds;
    }

    /**
    * Phát một refresh token mới cho user. Gọi khi đăng nhập / đăng ký.
    * Trả về CHUỖI GỐC để gửi cho client; DB chỉ lưu bản băm của nó.
    */

    @Transactional
    public String issue(User user) {
        byte[] randomBytes = new byte[32];
        RANDOM.nextBytes(randomBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        String tokenHash = hash(token);
        RefreshToken refreshToken = new RefreshToken(user, tokenHash,
                Instant.now().plusSeconds(refreshExpirationSeconds));
        refreshTokenRepository.save(refreshToken);
        return token;
    }

    static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public User consume(String rawToken) {
        Instant now = Instant.now();
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHashForUpdate(hash(rawToken))
                .orElseThrow(() -> new InvalidRefreshTokenException());
        if (refreshToken.isRevoked()) {
            refreshTokenRepository.revokeAllActiveByUserId(refreshToken.getUser().getId(), now);
            throw new InvalidRefreshTokenException();
        }
        if (refreshToken.isExpired(now))
            throw new InvalidRefreshTokenException();
        refreshToken.revoke(now);
        return refreshToken.getUser();
    }

    @Transactional
    public void revoke(String rawToken) {
        refreshTokenRepository.revokeByTokenHash(hash(rawToken), Instant.now());
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onChangePassword(PasswordChangedEvent event) {
        refreshTokenRepository.revokeAllActiveByUserId(event.userId(), Instant.now());
    }

    /**
     * Xoá hẳn các refresh token đã quá hạn, chạy theo lịch ở jwt.refresh-purge-cron.
     * Chỉ xoá token QUÁ HẠN: token đã thu hồi mà còn hạn phải giữ — nhờ nó consume() mới nhận ra được
     * khi có người đem token đã đổi đi dùng lại. Quá hạn rồi thì ai đưa lên cũng chỉ nhận 401.
     * Không ai gọi trực tiếp: Spring tự gọi theo lịch, ở luồng riêng, ngoài mọi request.
     */
    @Scheduled(cron = "${jwt.refresh-purge-cron}", zone = "Asia/Ho_Chi_Minh")
    @Transactional
    public void purgeExpired() {
        int deleted = refreshTokenRepository.deleteExpired(Instant.now());
        if (deleted > 0)
            logger.info("Đã xoá {} refresh token đã quá hạn", deleted);
    }
}
