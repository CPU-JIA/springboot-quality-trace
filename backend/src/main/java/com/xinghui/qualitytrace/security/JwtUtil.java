package com.xinghui.qualitytrace.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

/**
 * JWT 工具 —— 令牌的签发与校验（jjwt 0.13.0，0.12+ 链式 API）
 *
 * <p>令牌结构：subject = 用户ID；自定义 claims 携带 username 与 roles（角色编码列表），
 * 使拦截器无需查库即可还原用户身份与权限——这是无状态认证的核心收益。</p>
 *
 * <p>算法与密钥：HMAC 对称签名，jjwt 按密钥长度自动选择最强算法（本项目密钥 55 字节
 * → HS384）；密钥来自配置 jwt.secret，
 * 必须 ≥ 32 字节（256 位），否则 {@link Keys#hmacShaKeyFor} 抛 WeakKeyException。</p>
 *
 * <p>API 版本说明：0.12 起 API 大改（parserBuilder→parser、setSubject→subject、
 * verifyWith/parseSignedClaims 新链式），网上 0.11.x 老教程代码在本版本编译不过。</p>
 */
@Component
public class JwtUtil {

    /** roles 自定义 claim 的键名 */
    private static final String CLAIM_ROLES = "roles";
    /** username 自定义 claim 的键名 */
    private static final String CLAIM_USERNAME = "username";

    /** HS256 签名密钥（由 ≥32 字节的配置字符串派生） */
    private final SecretKey key;

    /** 令牌有效期（毫秒） */
    private final long expireMillis;

    public JwtUtil(@Value("${jwt.secret}") String secret,
                   @Value("${jwt.expire-millis}") long expireMillis) {
        // 密钥长度不足时此处直接抛 WeakKeyException 使启动失败——配置错误必须尽早暴露
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireMillis = expireMillis;
    }

    /**
     * 签发令牌
     *
     * @param userId   用户ID（写入 subject）
     * @param username 登录账号
     * @param roles    角色编码列表
     * @return 紧凑格式的 JWT 字符串
     */
    public String generate(Long userId, String username, List<String> roles) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_USERNAME, username)
                .claim(CLAIM_ROLES, roles)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireMillis))
                .signWith(key)
                .compact();
    }

    /**
     * 校验并解析令牌，还原为登录用户上下文对象
     *
     * @param token JWT 字符串（不含 "Bearer " 前缀）
     * @return 登录用户；签名非法/已过期/格式错误时返回 null（由拦截器统一转 401）
     */
    @SuppressWarnings("unchecked")
    public UserContext.LoginUser parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return new UserContext.LoginUser(
                    Long.valueOf(claims.getSubject()),
                    claims.get(CLAIM_USERNAME, String.class),
                    (List<String>) claims.get(CLAIM_ROLES, List.class));
        } catch (JwtException | IllegalArgumentException e) {
            // 过期(ExpiredJwtException)/签名不符(SignatureException)/格式非法 —— 统一视为未认证
            return null;
        }
    }
}
