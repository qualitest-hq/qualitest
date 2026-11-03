package com.qualitest.flow.script.tool;

import org.bouncycastle.asn1.gm.GMNamedCurves;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.generators.ECKeyPairGenerator;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECKeyGenerationParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Signature;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测 ScriptCryptoSupport：摘要 / HMAC 已知向量，RSA 多种密钥格式，AES-GCM 与分组模式，国密 SM2/SM3/SM4。
 * 边界：纯 Java，无 Spring；RSA / SM2 密钥在类初始化时随机生成。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=ScriptCryptoSupportTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ScriptCryptoSupportTest {

    /** HMAC 已知值测试使用的原文 */
    private static final String FOX = "The quick brown fox jumps over the lazy dog";

    /** RSA 2048 密钥对，供 JDK 侧交叉验签 */
    private static KeyPair rsaKeyPair;
    /** RSA 私钥：PKCS#8 PEM */
    private static String rsaPrivatePkcs8Pem;
    /** RSA 私钥：PKCS#1 PEM */
    private static String rsaPrivatePkcs1Pem;
    /** RSA 私钥：PKCS#8 裸 Base64 */
    private static String rsaPrivateBare;
    /** RSA 公钥：X.509 PEM */
    private static String rsaPublicPem;
    /** RSA 公钥：X.509 裸 Base64 */
    private static String rsaPublicBare;

    /** SM2 私钥 d 值，64 位 hex */
    private static String sm2PrivateHex;
    /** SM2 公钥未压缩点，04 开头 hex */
    private static String sm2PublicHex;

    /** 生成本类共用的 RSA 与 SM2 密钥，并导出为各种格式 */
    @BeforeAll
    static void generateKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        rsaKeyPair = generator.generateKeyPair();
        byte[] pkcs8 = rsaKeyPair.getPrivate().getEncoded();
        byte[] pkcs1 = PrivateKeyInfo.getInstance(pkcs8).parsePrivateKey().toASN1Primitive().getEncoded();
        rsaPrivatePkcs8Pem = pem("PRIVATE KEY", pkcs8);
        rsaPrivatePkcs1Pem = pem("RSA PRIVATE KEY", pkcs1);
        rsaPrivateBare = Base64.getEncoder().encodeToString(pkcs8);
        rsaPublicPem = pem("PUBLIC KEY", rsaKeyPair.getPublic().getEncoded());
        rsaPublicBare = Base64.getEncoder().encodeToString(rsaKeyPair.getPublic().getEncoded());

        X9ECParameters curve = GMNamedCurves.getByName("sm2p256v1");
        ECDomainParameters domain = new ECDomainParameters(curve.getCurve(), curve.getG(), curve.getN(), curve.getH());
        ECKeyPairGenerator sm2Generator = new ECKeyPairGenerator();
        sm2Generator.init(new ECKeyGenerationParameters(domain, new SecureRandom()));
        AsymmetricCipherKeyPair sm2Pair = sm2Generator.generateKeyPair();
        sm2PrivateHex = String.format("%064x", ((ECPrivateKeyParameters) sm2Pair.getPrivate()).getD());
        sm2PublicHex = HexFormat.of().formatHex(((ECPublicKeyParameters) sm2Pair.getPublic()).getQ().getEncoded(false));
    }

    /** DER 包装为每行 64 字符的 PEM 文本 */
    private static String pem(String type, byte[] der) {
        return "-----BEGIN " + type + "-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(der)
                + "\n-----END " + type + "-----\n";
    }

    /**
     * 前提：对 "abc" 计算 SHA-1 / SHA-256 / SM3，SHA-256 另取 base64。
     * 期望：输出等于各算法对 "abc" 的已知摘要值。
     */
    @Test
    @Order(1)
    @DisplayName("摘要输出已知值")
    void digests_matchKnownVectors() {
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", ScriptCryptoSupport.digest("SHA-1", "abc", null));
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                ScriptCryptoSupport.digest("SHA-256", "abc", "hex"));
        assertEquals("ungWv48Bz+pBQUDeXa4iI7ADYaOWF3qctBD/YfIAFa0=",
                ScriptCryptoSupport.digest("SHA-256", "abc", "base64"));
        assertEquals("66c7f0f462eeedd9d1f2d46bdc10e4e24167c4875cf2f7a2297da02b8f4ba8e0",
                ScriptCryptoSupport.sm3("abc", null));
    }

    /**
     * 前提：key="key"，对 fox 句子计算 HMAC-SHA1 / SHA256 / SHA512。
     * 期望：输出等于已知 HMAC 值；不支持的输出编码报错。
     */
    @Test
    @Order(2)
    @DisplayName("HMAC 输出已知值")
    void hmac_matchKnownVectors() {
        assertEquals("de7c9b85b8b78aa6bc8a7a36f70a90701c9db4d9",
                ScriptCryptoSupport.hmac("HmacSHA1", FOX, "key", null));
        assertEquals("f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8",
                ScriptCryptoSupport.hmac("HmacSHA256", FOX, "key", null));
        assertEquals("b42af09057bac1e2d41708e48a902e09b5ff7f12ab428a4fe86653c73dd248fb"
                        + "82f948a549f7b791a5b41915ee4d1ec3935357e4e2317250d0372afa2ebeeb3a",
                ScriptCryptoSupport.hmac("HmacSHA512", FOX, "key", "hex"));
        assertThrows(IllegalArgumentException.class,
                () -> ScriptCryptoSupport.hmac("HmacSHA256", FOX, "key", "binary"));
    }

    /**
     * 前提：PKCS#8 PEM、PKCS#1 PEM、裸 Base64 三种私钥分别签名。
     * 期望：签名均能被 JDK 用原公钥验过；rsaVerify 对 PEM / 裸公钥均返回 true，篡改原文返回 false。
     */
    @Test
    @Order(3)
    @DisplayName("RSA 三种私钥格式签名可验")
    void rsaSign_acceptsAllPrivateKeyFormats() throws Exception {
        for (String privateKey : new String[]{rsaPrivatePkcs8Pem, rsaPrivatePkcs1Pem, rsaPrivateBare}) {
            String sign = ScriptCryptoSupport.rsaSign("a=1&b=2", privateKey, null);
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(rsaKeyPair.getPublic());
            verifier.update("a=1&b=2".getBytes(StandardCharsets.UTF_8));
            assertTrue(verifier.verify(Base64.getDecoder().decode(sign)));
            assertTrue(ScriptCryptoSupport.rsaVerify("a=1&b=2", sign, rsaPublicPem, null));
            assertTrue(ScriptCryptoSupport.rsaVerify("a=1&b=2", sign, rsaPublicBare, "SHA256withRSA"));
            assertFalse(ScriptCryptoSupport.rsaVerify("a=1&b=3", sign, rsaPublicPem, null));
        }
    }

    /**
     * 前提：PEM 以字面量 \n 存成单行（如 JSON 素材）。
     * 期望：仍可解析并签名。
     */
    @Test
    @Order(4)
    @DisplayName("RSA 兼容字面量 \\n 的单行 PEM")
    void rsaSign_acceptsEscapedNewlinePem() {
        String singleLine = rsaPrivatePkcs8Pem.replace("\n", "\\n");
        String sign = ScriptCryptoSupport.rsaSign("x", singleLine, null);
        assertTrue(ScriptCryptoSupport.rsaVerify("x", sign, rsaPublicPem, null));
    }

    /**
     * 前提：默认 OAEP、OAEP256、PKCS1 三种填充加密。
     * 期望：私钥解密还原明文。
     */
    @Test
    @Order(5)
    @DisplayName("RSA 加解密往返")
    void rsaEncrypt_roundTrip() {
        for (String padding : new String[]{null, "OAEP256", "PKCS1"}) {
            String cipher = ScriptCryptoSupport.rsaEncrypt("身份证号", rsaPublicPem, padding);
            assertEquals("身份证号", ScriptCryptoSupport.rsaDecrypt(cipher, rsaPrivatePkcs1Pem, padding));
        }
    }

    /**
     * 前提：32 字节 UTF-8 key、12 字节 nonce、aad=transaction 加密。
     * 期望：密文为 Base64(密文 + 16 字节 tag)，可被 JDK 直接解开；函数解密还原；aad 不同则解密失败。
     */
    @Test
    @Order(6)
    @DisplayName("AES-GCM 密文格式与解密")
    void aesGcm_formatAndRoundTrip() throws Exception {
        String key = "0123456789abcdef0123456789abcdef";
        String nonce = "abcdefghijkl";
        String plain = "{\"out_refund_no\":\"R001\"}";
        String cipherText = ScriptCryptoSupport.aesGcmEncrypt(plain, key, nonce, "transaction");

        byte[] raw = Base64.getDecoder().decode(cipherText);
        assertEquals(plain.getBytes(StandardCharsets.UTF_8).length + 16, raw.length);
        Cipher jdk = Cipher.getInstance("AES/GCM/NoPadding");
        jdk.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES"),
                new GCMParameterSpec(128, nonce.getBytes(StandardCharsets.UTF_8)));
        jdk.updateAAD("transaction".getBytes(StandardCharsets.UTF_8));
        assertEquals(plain, new String(jdk.doFinal(raw), StandardCharsets.UTF_8));

        assertEquals(plain, ScriptCryptoSupport.aesGcmDecrypt(cipherText, key, nonce, "transaction"));
        assertThrows(IllegalArgumentException.class,
                () -> ScriptCryptoSupport.aesGcmDecrypt(cipherText, key, nonce, "other"));
    }

    /**
     * 前提：AES-CBC（base64 key、默认全 0 IV）与 AES-ECB（hex 输出）。
     * 期望：均能往返；不支持的模式报错。
     */
    @Test
    @Order(7)
    @DisplayName("AES CBC / ECB 往返")
    void aesBlock_roundTrip() {
        String key = Base64.getEncoder().encodeToString("1234567890abcdef".getBytes(StandardCharsets.UTF_8));
        Map<String, Object> cbc = Map.of("keyEncoding", "base64");
        Map<String, Object> ecb = Map.of("keyEncoding", "base64", "mode", "ECB", "encoding", "hex");
        String cbcCipher = ScriptCryptoSupport.aesEncrypt("hello 支付宝", key, cbc);
        String ecbCipher = ScriptCryptoSupport.aesEncrypt("hello 支付宝", key, ecb);
        assertEquals("hello 支付宝", ScriptCryptoSupport.aesDecrypt(cbcCipher, key, cbc));
        assertEquals("hello 支付宝", ScriptCryptoSupport.aesDecrypt(ecbCipher, key, ecb));
        assertThrows(IllegalArgumentException.class,
                () -> ScriptCryptoSupport.aesEncrypt("x", key, Map.of("keyEncoding", "base64", "mode", "CTR")));
    }

    /**
     * 前提：hex 密钥，SM2 分别以 der 与 rs 格式签名。
     * 期望：同格式验签通过；rs 签名为 64 字节；跨格式或改 userId 验签失败。
     */
    @Test
    @Order(8)
    @DisplayName("SM2 der / rs 签名验签")
    void sm2Sign_formats() {
        String der = ScriptCryptoSupport.sm2Sign("data", sm2PrivateHex, null);
        assertTrue(ScriptCryptoSupport.sm2Verify("data", der, sm2PublicHex, null));

        Map<String, Object> rs = Map.of("format", "rs", "encoding", "hex");
        String plain = ScriptCryptoSupport.sm2Sign("data", sm2PrivateHex, rs);
        assertEquals(128, plain.length());
        assertTrue(ScriptCryptoSupport.sm2Verify("data", plain, sm2PublicHex.substring(2), rs));

        assertFalse(ScriptCryptoSupport.sm2Verify("data", der, sm2PublicHex, Map.of("format", "rs")));
        assertFalse(ScriptCryptoSupport.sm2Verify("data", der, sm2PublicHex, Map.of("userId", "other")));
    }

    /**
     * 前提：SM2 以 C1C3C2（默认）与 C1C2C3 加密。
     * 期望：同模式解密还原；去掉 04 前缀的密文也能解密。
     */
    @Test
    @Order(9)
    @DisplayName("SM2 加解密往返")
    void sm2Encrypt_roundTrip() {
        String c1c3c2 = ScriptCryptoSupport.sm2Encrypt("国密明文", sm2PublicHex, Map.of("encoding", "hex"));
        assertEquals("国密明文", ScriptCryptoSupport.sm2Decrypt(c1c3c2, sm2PrivateHex, Map.of("encoding", "hex")));
        assertEquals("国密明文",
                ScriptCryptoSupport.sm2Decrypt(c1c3c2.substring(2), sm2PrivateHex, Map.of("encoding", "hex")));

        Map<String, Object> c1c2c3 = Map.of("mode", "C1C2C3");
        String legacy = ScriptCryptoSupport.sm2Encrypt("国密明文", sm2PublicHex, c1c2c3);
        assertEquals("国密明文", ScriptCryptoSupport.sm2Decrypt(legacy, sm2PrivateHex, c1c2c3));
    }

    /**
     * 前提：hex 密钥（默认 keyEncoding），SM4 ECB 与 CBC（hex IV）加密。
     * 期望：均能往返；NoPadding 下非 16 字节倍数报错。
     */
    @Test
    @Order(10)
    @DisplayName("SM4 ECB / CBC 往返")
    void sm4_roundTrip() {
        String key = "0123456789abcdeffedcba9876543210";
        Map<String, Object> ecb = Map.of("mode", "ECB");
        Map<String, Object> cbc = Map.of("iv", "00112233445566778899aabbccddeeff", "ivEncoding", "hex");
        assertEquals("国密 SM4", ScriptCryptoSupport.sm4Decrypt(ScriptCryptoSupport.sm4Encrypt("国密 SM4", key, ecb), key, ecb));
        assertEquals("国密 SM4", ScriptCryptoSupport.sm4Decrypt(ScriptCryptoSupport.sm4Encrypt("国密 SM4", key, cbc), key, cbc));
        assertThrows(IllegalArgumentException.class,
                () -> ScriptCryptoSupport.sm4Encrypt("abc", key, Map.of("mode", "ECB", "padding", "NoPadding")));
    }
}
