package com.qualitest.flow.script.tool;

import org.bouncycastle.asn1.DERNull;
import org.bouncycastle.asn1.gm.GMNamedCurves;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.pkcs.RSAPrivateKey;
import org.bouncycastle.asn1.pkcs.RSAPublicKey;
import org.bouncycastle.asn1.sec.ECPrivateKey;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.Certificate;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.BlockCipher;
import org.bouncycastle.crypto.BufferedBlockCipher;
import org.bouncycastle.crypto.CipherParameters;
import org.bouncycastle.crypto.DefaultBufferedBlockCipher;
import org.bouncycastle.crypto.digests.SM3Digest;
import org.bouncycastle.crypto.engines.SM2Engine;
import org.bouncycastle.crypto.engines.SM4Engine;
import org.bouncycastle.crypto.modes.CBCBlockCipher;
import org.bouncycastle.crypto.paddings.PKCS7Padding;
import org.bouncycastle.crypto.paddings.PaddedBufferedBlockCipher;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithID;
import org.bouncycastle.crypto.params.ParametersWithIV;
import org.bouncycastle.crypto.params.ParametersWithRandom;
import org.bouncycastle.crypto.signers.DSAEncoding;
import org.bouncycastle.crypto.signers.PlainDSAEncoding;
import org.bouncycastle.crypto.signers.SM2Signer;
import org.bouncycastle.crypto.signers.StandardDSAEncoding;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 脚本加密工具：摘要、HMAC、RSA、AES、国密 SM2/SM3/SM4。
 * <p>
 * 入参与出参均为字符串，明文按 UTF-8 编码；二进制结果按 {@code hex}（小写）或 {@code base64} 输出。
 * 密钥支持 PEM、裸 Base64，国密密钥另支持 hex。国密使用 BouncyCastle 轻量级 API，不注册 JCA Provider。
 */
public final class ScriptCryptoSupport {

    /** 编码：小写十六进制 */
    private static final String ENC_HEX = "hex";
    /** 编码：标准 Base64 */
    private static final String ENC_BASE64 = "base64";
    /** 编码：UTF-8 原文（仅用于密钥 / IV 输入） */
    private static final String ENC_UTF8 = "utf8";

    /** SM2 签名默认用户标识 */
    private static final String SM2_DEFAULT_USER_ID = "1234567812345678";

    /** RSA 算法标识，用于把 PKCS#1 密钥包装为 PKCS#8 / X.509 结构 */
    private static final AlgorithmIdentifier RSA_ALGORITHM =
            new AlgorithmIdentifier(PKCSObjectIdentifiers.rsaEncryption, DERNull.INSTANCE);
    /** SM2 推荐曲线 sm2p256v1 */
    private static final X9ECParameters SM2_CURVE = GMNamedCurves.getByName("sm2p256v1");
    /** SM2 曲线域参数，用于构造公私钥参数 */
    private static final ECDomainParameters SM2_DOMAIN = new ECDomainParameters(
            SM2_CURVE.getCurve(), SM2_CURVE.getG(), SM2_CURVE.getN(), SM2_CURVE.getH());
    /** SM2 签名与加密使用的随机源 */
    private static final SecureRandom RANDOM = new SecureRandom();
    /** 纯十六进制字符串，用于识别 hex 格式的国密密钥 */
    private static final Pattern HEX_PATTERN = Pattern.compile("^[0-9a-fA-F]+$");
    /** AES / SM4 分组长度（字节），也是 CBC 默认全 0 IV 的长度 */
    private static final int BLOCK_SIZE = 16;

    /** 工具类，不允许实例化 */
    private ScriptCryptoSupport() {
    }

    // ---------------------------------------------------------------- 摘要与 HMAC

    /**
     * 计算消息摘要。
     *
     * @param algorithm JCA 摘要算法名，如 MD5、SHA-256
     * @param encoding  输出编码 hex / base64，默认 hex
     */
    public static String digest(String algorithm, String data, String encoding) {
        try {
            MessageDigest md = MessageDigest.getInstance(algorithm);
            return encode(md.digest(utf8(data)), encoding, ENC_HEX);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(algorithm + " 摘要失败: " + e.getMessage(), e);
        }
    }

    /**
     * 计算 SM3 摘要。
     *
     * @param encoding 输出编码 hex / base64，默认 hex
     */
    public static String sm3(String data, String encoding) {
        byte[] input = utf8(data);
        SM3Digest digest = new SM3Digest();
        digest.update(input, 0, input.length);
        byte[] out = new byte[digest.getDigestSize()];
        digest.doFinal(out, 0);
        return encode(out, encoding, ENC_HEX);
    }

    /**
     * 计算 HMAC，密钥按 UTF-8 编码。
     *
     * @param algorithm JCA Mac 算法名，如 HmacSHA256
     * @param encoding  输出编码 hex / base64，默认 hex
     */
    public static String hmac(String algorithm, String data, String secret, String encoding) {
        try {
            Mac mac = Mac.getInstance(algorithm);
            mac.init(new SecretKeySpec(utf8(secret), algorithm));
            return encode(mac.doFinal(utf8(data)), encoding, ENC_HEX);
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException(algorithm + " 失败: " + e.getMessage(), e);
        }
    }

    // ---------------------------------------------------------------- RSA

    /**
     * RSA 私钥签名，返回 Base64。
     *
     * @param privateKey PKCS#8 / PKCS#1 PEM，或裸 Base64
     * @param algorithm  签名算法，默认 SHA256withRSA
     */
    public static String rsaSign(String data, String privateKey, String algorithm) {
        try {
            Signature signer = Signature.getInstance(defaultIfBlank(algorithm, "SHA256withRSA"));
            signer.initSign(parseRsaPrivateKey(privateKey));
            signer.update(utf8(data));
            return Base64.getEncoder().encodeToString(signer.sign());
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("rsaSign 失败: " + e.getMessage(), e);
        }
    }

    /**
     * RSA 公钥验签。
     *
     * @param sign      Base64 签名
     * @param publicKey X.509 公钥 PEM、证书 PEM、PKCS#1 公钥 PEM，或裸 Base64
     * @param algorithm 签名算法，默认 SHA256withRSA
     */
    public static boolean rsaVerify(String data, String sign, String publicKey, String algorithm) {
        try {
            Signature verifier = Signature.getInstance(defaultIfBlank(algorithm, "SHA256withRSA"));
            verifier.initVerify(parseRsaPublicKey(publicKey));
            verifier.update(utf8(data));
            return verifier.verify(decode(sign, ENC_BASE64, ENC_BASE64));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("rsaVerify 失败: " + e.getMessage(), e);
        }
    }

    /**
     * RSA 公钥加密，返回 Base64。
     *
     * @param padding OAEP（默认，SHA-1）/ OAEP256 / PKCS1
     */
    public static String rsaEncrypt(String plain, String publicKey, String padding) {
        try {
            Cipher cipher = rsaCipher(padding, Cipher.ENCRYPT_MODE, parseRsaPublicKey(publicKey));
            return Base64.getEncoder().encodeToString(cipher.doFinal(utf8(plain)));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("rsaEncrypt 失败: " + e.getMessage(), e);
        }
    }

    /**
     * RSA 私钥解密 Base64 密文，返回 UTF-8 明文。
     *
     * @param padding OAEP（默认，SHA-1）/ OAEP256 / PKCS1
     */
    public static String rsaDecrypt(String cipherText, String privateKey, String padding) {
        try {
            Cipher cipher = rsaCipher(padding, Cipher.DECRYPT_MODE, parseRsaPrivateKey(privateKey));
            return new String(cipher.doFinal(decode(cipherText, ENC_BASE64, ENC_BASE64)), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("rsaDecrypt 失败: " + e.getMessage(), e);
        }
    }

    /**
     * 按填充方式创建并初始化 RSA Cipher。
     *
     * @param padding OAEP（默认，SHA-1）/ OAEP256（SHA-256 + MGF1-SHA256）/ PKCS1
     * @param mode    Cipher.ENCRYPT_MODE 或 Cipher.DECRYPT_MODE
     */
    private static Cipher rsaCipher(String padding, int mode, Key key) throws GeneralSecurityException {
        String name = defaultIfBlank(padding, "OAEP").trim().toUpperCase(Locale.ROOT);
        switch (name) {
            case "OAEP" -> {
                Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-1AndMGF1Padding");
                cipher.init(mode, key);
                return cipher;
            }
            case "OAEP256" -> {
                Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPPadding");
                cipher.init(mode, key, new OAEPParameterSpec(
                        "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT));
                return cipher;
            }
            case "PKCS1" -> {
                Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
                cipher.init(mode, key);
                return cipher;
            }
            default -> throw new IllegalArgumentException("不支持的 RSA 填充: " + padding + "（可选 OAEP / OAEP256 / PKCS1）");
        }
    }

    /** 解析 RSA 私钥：PKCS#8 / PKCS#1 PEM，或裸 Base64（自动识别 PKCS#8 / PKCS#1）。 */
    private static PrivateKey parseRsaPrivateKey(String text) throws GeneralSecurityException {
        KeyMaterial material = readKey(text, "privateKey");
        byte[] pkcs8;
        if ("RSA PRIVATE KEY".equals(material.type())) {
            pkcs8 = rsaPkcs1ToPkcs8(material.der());
        } else if (material.type() == null || "PRIVATE KEY".equals(material.type())) {
            pkcs8 = isPkcs8(material.der()) ? material.der() : rsaPkcs1ToPkcs8(material.der());
        } else {
            throw new IllegalArgumentException("不支持的 RSA 私钥类型: " + material.type());
        }
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
    }

    /** 解析 RSA 公钥：X.509 公钥 / 证书 / PKCS#1 公钥，PEM 或裸 Base64。 */
    private static PublicKey parseRsaPublicKey(String text) throws GeneralSecurityException {
        SubjectPublicKeyInfo info = toPublicKeyInfo(readKey(text, "publicKey"), true);
        try {
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(info.getEncoded()));
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("RSA 公钥编码失败: " + e.getMessage(), e);
        }
    }

    /** 判断 DER 是否为 PKCS#8 私钥结构 */
    private static boolean isPkcs8(byte[] der) {
        try {
            PrivateKeyInfo.getInstance(der);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** 将 PKCS#1 RSA 私钥 DER 包装为 PKCS#8 DER */
    private static byte[] rsaPkcs1ToPkcs8(byte[] der) {
        try {
            return new PrivateKeyInfo(RSA_ALGORITHM, RSAPrivateKey.getInstance(der)).getEncoded();
        } catch (Exception e) {
            throw new IllegalArgumentException("无法解析 RSA 私钥（支持 PKCS#8 / PKCS#1）: " + e.getMessage(), e);
        }
    }

    /**
     * 从密钥材料中取出 X.509 公钥结构：支持公钥 PEM、证书 PEM；裸 Base64 时依次尝试公钥、证书、PKCS#1 公钥。
     *
     * @param allowRsaPkcs1 是否接受 PKCS#1 RSA 公钥（国密公钥传 false）
     */
    private static SubjectPublicKeyInfo toPublicKeyInfo(KeyMaterial material, boolean allowRsaPkcs1) {
        String type = material.type();
        byte[] der = material.der();
        if ("CERTIFICATE".equals(type)) {
            return Certificate.getInstance(der).getSubjectPublicKeyInfo();
        }
        if ("PUBLIC KEY".equals(type)) {
            return SubjectPublicKeyInfo.getInstance(der);
        }
        if ("RSA PUBLIC KEY".equals(type) && allowRsaPkcs1) {
            return rsaPkcs1PublicInfo(der);
        }
        if (type != null) {
            throw new IllegalArgumentException("不支持的公钥类型: " + type);
        }
        // 裸 Base64：依次尝试 X.509 公钥、证书、PKCS#1 公钥
        try {
            return SubjectPublicKeyInfo.getInstance(der);
        } catch (RuntimeException ignored) {
            // 继续尝试证书
        }
        try {
            return Certificate.getInstance(der).getSubjectPublicKeyInfo();
        } catch (RuntimeException ignored) {
            // 继续尝试 PKCS#1
        }
        if (allowRsaPkcs1) {
            return rsaPkcs1PublicInfo(der);
        }
        throw new IllegalArgumentException("无法解析公钥（支持 X.509 公钥 / 证书）");
    }

    /** 将 PKCS#1 RSA 公钥 DER 包装为 X.509 公钥结构 */
    private static SubjectPublicKeyInfo rsaPkcs1PublicInfo(byte[] der) {
        try {
            return new SubjectPublicKeyInfo(RSA_ALGORITHM, RSAPublicKey.getInstance(der));
        } catch (Exception e) {
            throw new IllegalArgumentException("无法解析 RSA 公钥（支持 X.509 / 证书 / PKCS#1）: " + e.getMessage(), e);
        }
    }

    // ---------------------------------------------------------------- AES

    /**
     * AES-GCM 加密，返回 Base64(密文 + 16 字节 tag)。
     *
     * @param key   UTF-8 密钥（16 / 24 / 32 字节）
     * @param nonce UTF-8 随机串
     * @param aad   附加数据，可空
     */
    public static String aesGcmEncrypt(String plain, String key, String nonce, String aad) {
        try {
            Cipher cipher = aesGcmCipher(Cipher.ENCRYPT_MODE, key, nonce, aad);
            return Base64.getEncoder().encodeToString(cipher.doFinal(utf8(plain)));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("aesGcmEncrypt 失败: " + e.getMessage(), e);
        }
    }

    /**
     * AES-GCM 解密 Base64(密文 + tag)，返回 UTF-8 明文；tag 校验失败抛异常。
     *
     * @param key   UTF-8 密钥（16 / 24 / 32 字节）
     * @param nonce UTF-8 随机串
     * @param aad   附加数据，可空
     */
    public static String aesGcmDecrypt(String cipherText, String key, String nonce, String aad) {
        try {
            Cipher cipher = aesGcmCipher(Cipher.DECRYPT_MODE, key, nonce, aad);
            return new String(cipher.doFinal(decode(cipherText, ENC_BASE64, ENC_BASE64)), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("aesGcmDecrypt 失败: " + e.getMessage(), e);
        }
    }

    /** 创建 AES-GCM Cipher：128 位 tag，key / nonce / aad 按 UTF-8，aad 为空时不写入 */
    private static Cipher aesGcmCipher(int mode, String key, String nonce, String aad) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(utf8(key), "AES"), new GCMParameterSpec(128, utf8(nonce)));
        if (aad != null && !aad.isEmpty()) {
            cipher.updateAAD(utf8(aad));
        }
        return cipher;
    }

    /**
     * AES 分组加密（CBC / ECB）。
     *
     * @param opts mode（CBC 默认 / ECB）、iv、ivEncoding（默认 utf8）、keyEncoding（默认 utf8）、
     *             padding（PKCS5Padding 默认 / NoPadding）、encoding（密文编码，默认 base64）；
     *             CBC 未传 iv 时使用 16 字节全 0
     */
    public static String aesEncrypt(String data, String key, Map<String, Object> opts) {
        return blockCipher("AES", true, data, key, opts, ENC_UTF8);
    }

    /**
     * AES 分组解密（CBC / ECB），返回 UTF-8 明文。
     *
     * @param data 密文，按 opts.encoding 解码（默认 base64）
     * @param opts mode（CBC 默认 / ECB）、iv、ivEncoding（默认 utf8）、keyEncoding（默认 utf8）、
     *             padding（PKCS5Padding 默认 / NoPadding）、encoding（密文编码，默认 base64）；
     *             CBC 未传 iv 时使用 16 字节全 0
     */
    public static String aesDecrypt(String data, String key, Map<String, Object> opts) {
        return blockCipher("AES", false, data, key, opts, ENC_UTF8);
    }

    // ---------------------------------------------------------------- 国密

    /**
     * SM4 分组加密（CBC / ECB）。
     *
     * @param key  16 字节密钥，默认按 hex 解析
     * @param opts mode（CBC 默认 / ECB）、iv、ivEncoding（默认 utf8）、keyEncoding（默认 hex）、
     *             padding（PKCS5Padding 默认 / NoPadding）、encoding（密文编码，默认 base64）；
     *             CBC 未传 iv 时使用 16 字节全 0
     */
    public static String sm4Encrypt(String data, String key, Map<String, Object> opts) {
        return blockCipher("SM4", true, data, key, opts, ENC_HEX);
    }

    /**
     * SM4 分组解密（CBC / ECB），返回 UTF-8 明文。
     *
     * @param data 密文，按 opts.encoding 解码（默认 base64）
     * @param key  16 字节密钥，默认按 hex 解析
     * @param opts mode（CBC 默认 / ECB）、iv、ivEncoding（默认 utf8）、keyEncoding（默认 hex）、
     *             padding（PKCS5Padding 默认 / NoPadding）、encoding（密文编码，默认 base64）；
     *             CBC 未传 iv 时使用 16 字节全 0
     */
    public static String sm4Decrypt(String data, String key, Map<String, Object> opts) {
        return blockCipher("SM4", false, data, key, opts, ENC_HEX);
    }

    /**
     * SM2 签名。
     *
     * @param privateKey hex 私钥 d，或 PKCS#8 / SEC1 PEM、裸 Base64
     * @param opts       userId（默认 1234567812345678）、format（der 默认 / rs）、encoding（默认 base64）
     */
    public static String sm2Sign(String data, String privateKey, Map<String, Object> opts) {
        SM2Signer signer = sm2Signer(true, new ParametersWithRandom(sm2PrivateKey(privateKey), RANDOM), data, opts);
        try {
            return encode(signer.generateSignature(), ScriptToolOptions.rawString(opts, "encoding"), ENC_BASE64);
        } catch (Exception e) {
            throw new IllegalArgumentException("sm2Sign 失败: " + e.getMessage(), e);
        }
    }

    /**
     * SM2 验签；签名格式不符或内容被篡改时返回 false。
     *
     * @param sign      签名，按 opts.encoding 解码（默认 base64）
     * @param publicKey hex 公钥（04 开头 / 64 字节 XY / 压缩），或 X.509 公钥 / 证书 PEM、裸 Base64
     * @param opts      userId（默认 1234567812345678）、format（der 默认 / rs）、encoding（默认 base64）
     */
    public static boolean sm2Verify(String data, String sign, String publicKey, Map<String, Object> opts) {
        byte[] signature = decode(sign, ScriptToolOptions.rawString(opts, "encoding"), ENC_BASE64);
        SM2Signer signer = sm2Signer(false, sm2PublicKey(publicKey), data, opts);
        try {
            return signer.verifySignature(signature);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * 按签名格式与 userId 初始化 SM2 签名器，并写入 UTF-8 原文。
     *
     * @param forSigning true 为签名，false 为验签
     * @param key        签名时为带随机源的私钥，验签时为公钥
     */
    private static SM2Signer sm2Signer(boolean forSigning, CipherParameters key, String data, Map<String, Object> opts) {
        SM2Signer signer = new SM2Signer(sm2Encoding(opts));
        signer.init(forSigning, new ParametersWithID(key, utf8(sm2UserId(opts))));
        byte[] input = utf8(data);
        signer.update(input, 0, input.length);
        return signer;
    }

    /**
     * SM2 公钥加密，密文以 04 开头。
     *
     * @param publicKey hex 公钥（04 开头 / 64 字节 XY / 压缩），或 X.509 公钥 / 证书 PEM、裸 Base64
     * @param opts      mode（C1C3C2 默认 / C1C2C3）、encoding（默认 base64）
     */
    public static String sm2Encrypt(String plain, String publicKey, Map<String, Object> opts) {
        SM2Engine engine = new SM2Engine(sm2Mode(opts));
        engine.init(true, new ParametersWithRandom(sm2PublicKey(publicKey), RANDOM));
        byte[] input = utf8(plain);
        try {
            return encode(engine.processBlock(input, 0, input.length), ScriptToolOptions.rawString(opts, "encoding"), ENC_BASE64);
        } catch (Exception e) {
            throw new IllegalArgumentException("sm2Encrypt 失败: " + e.getMessage(), e);
        }
    }

    /**
     * SM2 私钥解密，返回 UTF-8 明文；密文缺少 04 前缀时自动补齐。
     *
     * @param privateKey hex 私钥 d，或 PKCS#8 / SEC1 PEM、裸 Base64
     * @param opts       mode（C1C3C2 默认 / C1C2C3）、encoding（密文编码，默认 base64）
     */
    public static String sm2Decrypt(String cipherText, String privateKey, Map<String, Object> opts) {
        byte[] input = decode(cipherText, ScriptToolOptions.rawString(opts, "encoding"), ENC_BASE64);
        if (input.length > 0 && input[0] != 0x04) {
            byte[] prefixed = new byte[input.length + 1];
            prefixed[0] = 0x04;
            System.arraycopy(input, 0, prefixed, 1, input.length);
            input = prefixed;
        }
        SM2Engine engine = new SM2Engine(sm2Mode(opts));
        engine.init(false, sm2PrivateKey(privateKey));
        try {
            return new String(engine.processBlock(input, 0, input.length), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalArgumentException("sm2Decrypt 失败: " + e.getMessage(), e);
        }
    }

    /** 解析 SM2 签名格式：der（ASN.1 DER，默认）或 rs（64 字节 R||S） */
    private static DSAEncoding sm2Encoding(Map<String, Object> opts) {
        String format = ScriptToolOptions.string(opts, "format", "der").trim().toLowerCase(Locale.ROOT);
        return switch (format) {
            case "der" -> StandardDSAEncoding.INSTANCE;
            case "rs" -> PlainDSAEncoding.INSTANCE;
            default -> throw new IllegalArgumentException("不支持的 SM2 签名格式: " + format + "（可选 der / rs）");
        };
    }

    /** 解析 SM2 密文分段顺序：C1C3C2（默认）或 C1C2C3 */
    private static SM2Engine.Mode sm2Mode(Map<String, Object> opts) {
        String mode = ScriptToolOptions.string(opts, "mode", "C1C3C2").trim().toUpperCase(Locale.ROOT);
        return switch (mode) {
            case "C1C3C2" -> SM2Engine.Mode.C1C3C2;
            case "C1C2C3" -> SM2Engine.Mode.C1C2C3;
            default -> throw new IllegalArgumentException("不支持的 SM2 密文顺序: " + mode + "（可选 C1C3C2 / C1C2C3）");
        };
    }

    /** 取 SM2 签名用户标识；未传时用默认值 1234567812345678，传空串则按空串参与计算 */
    private static String sm2UserId(Map<String, Object> opts) {
        String userId = ScriptToolOptions.rawString(opts, "userId");
        return userId != null ? userId : SM2_DEFAULT_USER_ID;
    }

    /** 解析 SM2 私钥：hex d 值，或 PKCS#8 / SEC1（EC PRIVATE KEY）PEM、裸 Base64。 */
    private static ECPrivateKeyParameters sm2PrivateKey(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("privateKey 不能为空");
        }
        String trimmed = text.trim();
        // 纯 hex 且不超过 33 字节：按私钥 d 值解析
        if (HEX_PATTERN.matcher(trimmed).matches() && trimmed.length() <= 66) {
            return new ECPrivateKeyParameters(new BigInteger(trimmed, 16), SM2_DOMAIN);
        }
        KeyMaterial material = readKey(trimmed, "privateKey");
        ECPrivateKey ecKey;
        try {
            if ("EC PRIVATE KEY".equals(material.type())) {
                ecKey = ECPrivateKey.getInstance(material.der());
            } else if (material.type() == null || "PRIVATE KEY".equals(material.type())) {
                ecKey = isPkcs8(material.der())
                        ? ECPrivateKey.getInstance(PrivateKeyInfo.getInstance(material.der()).parsePrivateKey())
                        : ECPrivateKey.getInstance(material.der());
            } else {
                throw new IllegalArgumentException("不支持的 SM2 私钥类型: " + material.type());
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("无法解析 SM2 私钥: " + e.getMessage(), e);
        }
        return new ECPrivateKeyParameters(ecKey.getKey(), SM2_DOMAIN);
    }

    /** 解析 SM2 公钥：hex 点坐标，或 X.509 公钥 / 证书 PEM、裸 Base64。 */
    private static ECPublicKeyParameters sm2PublicKey(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("publicKey 不能为空");
        }
        String trimmed = text.trim();
        byte[] point;
        if (HEX_PATTERN.matcher(trimmed).matches()) {
            // 128 位 hex 为不带前缀的 X||Y，补 04 表示未压缩点
            point = HexFormat.of().parseHex(trimmed.length() == 128 ? "04" + trimmed : trimmed);
        } else {
            point = toPublicKeyInfo(readKey(trimmed, "publicKey"), false).getPublicKeyData().getBytes();
        }
        try {
            return new ECPublicKeyParameters(SM2_DOMAIN.getCurve().decodePoint(point), SM2_DOMAIN);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("无法解析 SM2 公钥: " + e.getMessage(), e);
        }
    }

    // ---------------------------------------------------------------- 分组密码

    /**
     * AES / SM4 分组加解密的公共流程：解析模式、填充、密钥、IV 与数据编码，再交给具体算法处理。
     *
     * @param algorithm          AES 或 SM4
     * @param encrypt            true 加密（输入 UTF-8 明文），false 解密（输入按 encoding 解码的密文）
     * @param defaultKeyEncoding 未传 keyEncoding 时的密钥编码
     */
    private static String blockCipher(String algorithm, boolean encrypt, String data, String key,
                                      Map<String, Object> opts, String defaultKeyEncoding) {
        String fn = algorithm.toLowerCase(Locale.ROOT) + (encrypt ? "Encrypt" : "Decrypt");
        String mode = ScriptToolOptions.string(opts, "mode", "CBC").trim().toUpperCase(Locale.ROOT);
        if (!"CBC".equals(mode) && !"ECB".equals(mode)) {
            throw new IllegalArgumentException(fn + " 不支持的模式: " + mode + "（可选 CBC / ECB）");
        }
        boolean padding = usePadding(ScriptToolOptions.rawString(opts, "padding"), fn);
        byte[] keyBytes = decode(key, ScriptToolOptions.rawString(opts, "keyEncoding"), defaultKeyEncoding);
        byte[] iv = null;
        if ("CBC".equals(mode)) {
            String ivText = ScriptToolOptions.rawString(opts, "iv");
            iv = ivText == null ? new byte[BLOCK_SIZE] : decode(ivText, ScriptToolOptions.rawString(opts, "ivEncoding"), ENC_UTF8);
        }
        String dataEncoding = ScriptToolOptions.rawString(opts, "encoding");
        byte[] input = encrypt ? utf8(data) : decode(data, dataEncoding, ENC_BASE64);
        byte[] output;
        try {
            output = "SM4".equals(algorithm)
                    ? sm4Process(encrypt, mode, padding, keyBytes, iv, input)
                    : aesProcess(encrypt, mode, padding, keyBytes, iv, input);
        } catch (Exception e) {
            throw new IllegalArgumentException(fn + " 失败: " + e.getMessage(), e);
        }
        return encrypt ? encode(output, dataEncoding, ENC_BASE64) : new String(output, StandardCharsets.UTF_8);
    }

    /**
     * 解析填充选项：未传或 PKCS5 / PKCS7 返回 true，NoPadding 返回 false。
     *
     * @param fn 函数名，用于错误提示
     */
    private static boolean usePadding(String padding, String fn) {
        if (padding == null || padding.isBlank()) {
            return true;
        }
        String normalized = padding.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "PKCS5PADDING", "PKCS7PADDING", "PKCS5", "PKCS7" -> true;
            case "NOPADDING", "NONE" -> false;
            default -> throw new IllegalArgumentException(fn + " 不支持的填充: " + padding + "（可选 PKCS5Padding / NoPadding）");
        };
    }

    /**
     * 用 JDK Cipher 执行 AES CBC / ECB。
     *
     * @param iv CBC 的 IV；ECB 传 null
     */
    private static byte[] aesProcess(boolean encrypt, String mode, boolean padding, byte[] key, byte[] iv, byte[] input)
            throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/" + mode + "/" + (padding ? "PKCS5Padding" : "NoPadding"));
        int cipherMode = encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE;
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        if (iv != null) {
            cipher.init(cipherMode, keySpec, new IvParameterSpec(iv));
        } else {
            cipher.init(cipherMode, keySpec);
        }
        return cipher.doFinal(input);
    }

    /**
     * 用 BouncyCastle SM4 引擎执行 CBC / ECB，填充为 PKCS#7。
     *
     * @param iv CBC 的 IV；ECB 传 null
     */
    private static byte[] sm4Process(boolean encrypt, String mode, boolean padding, byte[] key, byte[] iv, byte[] input)
            throws Exception {
        BlockCipher engine = "CBC".equals(mode) ? CBCBlockCipher.newInstance(new SM4Engine()) : new SM4Engine();
        BufferedBlockCipher cipher = padding
                ? new PaddedBufferedBlockCipher(engine, new PKCS7Padding())
                : new DefaultBufferedBlockCipher(engine);
        CipherParameters params = new KeyParameter(key);
        if (iv != null) {
            params = new ParametersWithIV(params, iv);
        }
        cipher.init(encrypt, params);
        byte[] out = new byte[cipher.getOutputSize(input.length)];
        int length = cipher.processBytes(input, 0, input.length, out, 0);
        length += cipher.doFinal(out, length);
        return Arrays.copyOf(out, length);
    }

    // ---------------------------------------------------------------- 编码与密钥读取

    /**
     * 按编码输出二进制。
     *
     * @param encoding        hex / base64，空则用 defaultEncoding
     * @param defaultEncoding 默认编码
     */
    private static String encode(byte[] bytes, String encoding, String defaultEncoding) {
        String enc = normalizeEncoding(encoding, defaultEncoding);
        return switch (enc) {
            case ENC_HEX -> HexFormat.of().formatHex(bytes);
            case ENC_BASE64 -> Base64.getEncoder().encodeToString(bytes);
            default -> throw new IllegalArgumentException("不支持的输出编码: " + encoding + "（可选 hex / base64）");
        };
    }

    /**
     * 按编码解析字符串为二进制；base64 解码忽略换行等空白。
     *
     * @param encoding        hex / base64 / utf8，空则用 defaultEncoding
     * @param defaultEncoding 默认编码
     */
    private static byte[] decode(String text, String encoding, String defaultEncoding) {
        if (text == null) {
            throw new IllegalArgumentException("待解码内容不能为空");
        }
        String enc = normalizeEncoding(encoding, defaultEncoding);
        if (ENC_UTF8.equals(enc)) {
            return utf8(text);
        }
        if (!ENC_HEX.equals(enc) && !ENC_BASE64.equals(enc)) {
            throw new IllegalArgumentException("不支持的编码: " + encoding + "（可选 hex / base64 / utf8）");
        }
        try {
            return ENC_HEX.equals(enc)
                    ? HexFormat.of().parseHex(text.trim())
                    : Base64.getMimeDecoder().decode(text.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("按 " + enc + " 解码失败: " + e.getMessage(), e);
        }
    }

    /** 编码名归一为小写；空值取默认编码，utf-8 视同 utf8 */
    private static String normalizeEncoding(String encoding, String defaultEncoding) {
        String enc = encoding == null || encoding.isBlank() ? defaultEncoding : encoding.trim().toLowerCase(Locale.ROOT);
        return "utf-8".equals(enc) ? ENC_UTF8 : enc;
    }

    /**
     * 读取 PEM 或裸 Base64 密钥；兼容 JSON 中以字面量 \n 保存的换行；拒绝加密的 PEM。
     *
     * @param name 参数名，用于错误提示
     */
    private static KeyMaterial readKey(String text, String name) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
        String trimmed = text.replace("\\n", "\n").trim();
        // 非 PEM：整体按 Base64 解码为 DER
        if (!trimmed.startsWith("-----BEGIN ")) {
            return new KeyMaterial(null, decode(trimmed, ENC_BASE64, ENC_BASE64));
        }
        int typeEnd = trimmed.indexOf("-----", 11);
        int bodyEnd = typeEnd < 0 ? -1 : trimmed.indexOf("-----END", typeEnd + 5);
        if (typeEnd < 0 || bodyEnd < 0) {
            throw new IllegalArgumentException(name + " PEM 格式不完整");
        }
        String type = trimmed.substring(11, typeEnd).trim();
        String body = trimmed.substring(typeEnd + 5, bodyEnd);
        if (body.contains("Proc-Type:") || type.startsWith("ENCRYPTED")) {
            throw new IllegalArgumentException(name + " 不支持加密的 PEM，请先导出为明文私钥");
        }
        return new KeyMaterial(type, decode(body, ENC_BASE64, ENC_BASE64));
    }

    /** PEM 类型（裸 Base64 时为 null）与 DER 内容 */
    private record KeyMaterial(String type, byte[] der) {
    }

    /** 空白时返回默认值 */
    private static String defaultIfBlank(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    /** 文本按 UTF-8 转字节；null 按字符串 "null" 处理 */
    private static byte[] utf8(String text) {
        return String.valueOf(text).getBytes(StandardCharsets.UTF_8);
    }
}
