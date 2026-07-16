package com.java2nb.novel.core.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java2nb.novel.core.bean.UserDetails;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * @author 11797
 */
@Component
@Slf4j
public class JwtTokenUtil {

    private static final String CLAIM_KEY_USERNAME = "sub";
    private static final String CLAIM_KEY_CREATED = "created";
    @Value("${jwt.secret}")
    private String secret;
    @Value("${jwt.expiration}")
    private Long expiration;

    /**
     * Tạo token JWT bằng khóa chịu trách nhiệm ký
     */
    private String generateToken(Map<String, Object> claims) {
        return Jwts.builder()
                .setClaims(claims)
                .setExpiration(generateExpirationDate())
                .signWith(SignatureAlgorithm.HS512, secret)
                .compact();
    }

    /**
     * Lấy payload JWT từ token
     */
    private Claims getClaimsFromToken(String token) {
        Claims claims = null;
        try {
            claims = Jwts.parser()
                    .setSigningKey(secret)
                    .parseClaimsJws(token)
                    .getBody();
        } catch (Exception e) {
            log.info("Xác thực định dạng JWT thất bại: {}", token);
        }
        return claims;
    }

    /**
     * Thời hạn token
     */
    private Date generateExpirationDate() {
        return new Date(System.currentTimeMillis() + expiration * 1000);
    }

    /**
     * Lấy thông tin người dùng từ token
     */
    public UserDetails getUserDetailsFromToken(String token) {
        if(isTokenExpired(token)){
            return null;
        }
        UserDetails userDetail;
        try {
            Claims claims = getClaimsFromToken(token);
             userDetail = new ObjectMapper().readValue(claims.getSubject(),UserDetails.class);
        } catch (Exception e) {
            log.error(e.getMessage(),e);
            userDetail = null;
        }
        return userDetail;
    }


    /**
     * Kiểm tra token đã hết hiệu lực hay chưa
     */
    private boolean isTokenExpired(String token) {
        Date expiredDate = getExpiredDateFromToken(token);
        if(expiredDate == null){
            return true;
        }else {
            return expiredDate.before(new Date());
        }
    }

    /**
     * Lấy thời gian hết hạn từ token
     */
    private Date getExpiredDateFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims != null ? claims.getExpiration() : null;
    }

    /**
     * Tạo token theo thông tin người dùng
     */
    @SneakyThrows
    public String generateToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>(2);
        claims.put(CLAIM_KEY_USERNAME, new ObjectMapper().writeValueAsString(userDetails));
        claims.put(CLAIM_KEY_CREATED, new Date());
        return generateToken(claims);
    }

    /**
     * Kiểm tra token có thể làm mới hay không
     */
    public boolean canRefresh(String token) {
        return !isTokenExpired(token);
    }

    /**
     *Làm mới tokenn
     */
    public String refreshToken(String token) {
        Claims claims = getClaimsFromToken(token);
        claims.put(CLAIM_KEY_CREATED, new Date());
        return generateToken(claims);
    }


}
