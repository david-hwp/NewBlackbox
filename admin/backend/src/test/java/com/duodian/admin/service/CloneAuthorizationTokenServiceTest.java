package com.duodian.admin.service;

import com.duodian.admin.config.CloneAuthorizationProperties;
import com.duodian.admin.entity.Shop;
import com.duodian.admin.entity.User;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDateTime;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class CloneAuthorizationTokenServiceTest {

    @Test
    void signsVerifiableTokenWithoutShopDisplayFields() throws Exception {
        KeyPair keyPair = generateKeyPair();
        CloneAuthorizationProperties properties = new CloneAuthorizationProperties();
        properties.setPrivateKey(Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded()));
        CloneAuthorizationTokenService service = new CloneAuthorizationTokenService(properties);
        Shop shop = new Shop();
        shop.setUserId(7L);
        shop.setShopName("不应进入Token的店铺名");
        shop.setShopId("shop-secret-id");
        shop.setPackageName("com.jd.mrd.jingming");
        shop.setCloneInstanceId("CLN1-13800000007-com.jd.mrd.jingming-N1-U3-Rabcd1234");
        shop.setLocalVirtualUserId(3);
        shop.setCredentialVersion(2);
        shop.setAuthStartAt(LocalDateTime.now().minusMinutes(1));
        shop.setAuthExpireAt(LocalDateTime.now().plusDays(30));
        shop.setAuthorizationJti("jti-1");
        User user = new User();
        user.setId(7L);
        user.setPhone("13800000007");

        String token = service.signToken(shop, user);

        String[] parts = token.split("\\.");
        assertThat(parts).hasSize(3);
        assertThat(verify(parts[0] + "." + parts[1], parts[2], keyPair.getPublic().getEncoded())).isTrue();
        String claims = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        assertThat(claims).contains("\"typ\":\"clone_auth\"");
        assertThat(claims).contains("\"serverUserId\":7");
        assertThat(claims).contains("\"phone\":\"13800000007\"");
        assertThat(claims).contains("\"cloneInstanceId\":\"CLN1-13800000007-com.jd.mrd.jingming-N1-U3-Rabcd1234\"");
        assertThat(claims).doesNotContain("不应进入Token的店铺名");
        assertThat(claims).doesNotContain("shop-secret-id");
    }

    @Test
    void signsTokenWithExplicitLocalVirtualUserIdWithoutMutatingShop() throws Exception {
        KeyPair keyPair = generateKeyPair();
        CloneAuthorizationProperties properties = new CloneAuthorizationProperties();
        properties.setPrivateKey(Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded()));
        CloneAuthorizationTokenService service = new CloneAuthorizationTokenService(properties);
        Shop shop = new Shop();
        shop.setUserId(7L);
        shop.setPackageName("com.jd.mrd.jingming");
        shop.setCloneInstanceId("CLN1-13800000007-com.jd.mrd.jingming-N1-U3-Rabcd1234");
        shop.setLocalVirtualUserId(3);
        shop.setCredentialVersion(2);
        shop.setAuthStartAt(LocalDateTime.now().minusMinutes(1));
        shop.setAuthExpireAt(LocalDateTime.now().plusDays(30));
        shop.setAuthorizationJti("jti-1");
        User user = new User();
        user.setId(7L);
        user.setPhone("13800000007");

        String token = service.signToken(shop, user, 9);

        String[] parts = token.split("\\.");
        assertThat(parts).hasSize(3);
        assertThat(verify(parts[0] + "." + parts[1], parts[2], keyPair.getPublic().getEncoded())).isTrue();
        String claims = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        assertThat(claims).contains("\"localVirtualUserId\":9");
        assertThat(shop.getLocalVirtualUserId()).isEqualTo(3);
    }

    private KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(keyPair.getPrivate().getEncoded()));
        return keyPair;
    }

    private boolean verify(String signingInput, String signaturePart, byte[] keyBytes) throws Exception {
        RSAPublicKey publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(keyBytes));
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initVerify(publicKey);
        signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
        return signature.verify(Base64.getUrlDecoder().decode(signaturePart));
    }
}
