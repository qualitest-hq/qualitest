package com.qualitest.flow.script.tool;

import com.qualitest.flow.script.ScriptValueConverter;
import org.graalvm.polyglot.proxy.ProxyExecutable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static com.qualitest.flow.script.ScriptArgs.object;
import static com.qualitest.flow.script.ScriptArgs.optStr;
import static com.qualitest.flow.script.ScriptArgs.opts;
import static com.qualitest.flow.script.ScriptArgs.requireArgs;
import static com.qualitest.flow.script.ScriptArgs.str;
import static com.qualitest.flow.script.ScriptArgs.toJava;

/**
 * 脚本宿主共用的无状态工具函数表（Script 节点 {@code ctx} 与接口脚本 {@code api} 同名挂载）。
 * <p>
 * 包含 JSON、Base64、URL、XML、签名原文拼接、摘要、HMAC、RSA、AES、国密 SM2/SM3/SM4。
 */
public final class ScriptToolMembers {

    /** 函数名 → 脚本可调用函数；类加载时构建，只读 */
    private static final Map<String, ProxyExecutable> TOOLS = build();

    /** 工具类，不允许实例化 */
    private ScriptToolMembers() {
    }

    /** 全部工具函数名 */
    public static Set<String> names() {
        return TOOLS.keySet();
    }

    /** 按名称取工具函数；不存在返回 null */
    public static ProxyExecutable get(String name) {
        return TOOLS.get(name);
    }

    /** 注册全部工具函数：每个函数负责取参、校验并调用对应算法 */
    private static Map<String, ProxyExecutable> build() {
        Map<String, ProxyExecutable> tools = new LinkedHashMap<>();

        // 编码与报文
        tools.put("jsonParse", args -> ScriptValueConverter.jsonParse(str(args, 0, "jsonParse")));
        tools.put("jsonStringify", args -> {
            requireArgs(args, 1, "jsonStringify");
            return ScriptValueConverter.jsonStringify(ScriptValueConverter.guestToJava(args[0]));
        });
        tools.put("base64Encode", args -> ScriptCodecSupport.base64Encode(str(args, 0, "base64Encode")));
        tools.put("base64Decode", args -> ScriptCodecSupport.base64Decode(str(args, 0, "base64Decode")));
        tools.put("urlEncode", args -> ScriptCodecSupport.urlEncode(str(args, 0, "urlEncode")));
        tools.put("urlDecode", args -> ScriptCodecSupport.urlDecode(str(args, 0, "urlDecode")));
        tools.put("xmlParse", args -> ScriptValueConverter.toScriptValue(
                ScriptCodecSupport.xmlParse(str(args, 0, "xmlParse"))));
        tools.put("xmlStringify", args -> {
            requireArgs(args, 1, "xmlStringify");
            return ScriptCodecSupport.xmlStringify(toJava(args[0]), opts(args, 1, "xmlStringify"));
        });
        tools.put("signContent", args -> ScriptCodecSupport.signContent(
                object(args, 0, "signContent"), opts(args, 1, "signContent")));

        // 摘要与 HMAC：末参可选输出编码 hex / base64
        putDigest(tools, "md5", "MD5");
        putDigest(tools, "sha1", "SHA-1");
        putDigest(tools, "sha256", "SHA-256");
        putDigest(tools, "sha512", "SHA-512");
        tools.put("sm3", args -> ScriptCryptoSupport.sm3(str(args, 0, "sm3"), optStr(args, 1)));
        putHmac(tools, "hmacSha1", "HmacSHA1");
        putHmac(tools, "hmacSha256", "HmacSHA256");
        putHmac(tools, "hmacSha512", "HmacSHA512");

        // RSA：末参可选签名算法或填充方式
        tools.put("rsaSign", args -> ScriptCryptoSupport.rsaSign(
                str(args, 0, "rsaSign"), str(args, 1, "rsaSign"), optStr(args, 2)));
        tools.put("rsaVerify", args -> ScriptCryptoSupport.rsaVerify(
                str(args, 0, "rsaVerify"), str(args, 1, "rsaVerify"), str(args, 2, "rsaVerify"), optStr(args, 3)));
        tools.put("rsaEncrypt", args -> ScriptCryptoSupport.rsaEncrypt(
                str(args, 0, "rsaEncrypt"), str(args, 1, "rsaEncrypt"), optStr(args, 2)));
        tools.put("rsaDecrypt", args -> ScriptCryptoSupport.rsaDecrypt(
                str(args, 0, "rsaDecrypt"), str(args, 1, "rsaDecrypt"), optStr(args, 2)));

        // AES：GCM 末参可选 aad；分组模式末参为 options
        tools.put("aesGcmEncrypt", args -> ScriptCryptoSupport.aesGcmEncrypt(
                str(args, 0, "aesGcmEncrypt"), str(args, 1, "aesGcmEncrypt"), str(args, 2, "aesGcmEncrypt"),
                optStr(args, 3)));
        tools.put("aesGcmDecrypt", args -> ScriptCryptoSupport.aesGcmDecrypt(
                str(args, 0, "aesGcmDecrypt"), str(args, 1, "aesGcmDecrypt"), str(args, 2, "aesGcmDecrypt"),
                optStr(args, 3)));
        tools.put("aesEncrypt", args -> ScriptCryptoSupport.aesEncrypt(
                str(args, 0, "aesEncrypt"), str(args, 1, "aesEncrypt"), opts(args, 2, "aesEncrypt")));
        tools.put("aesDecrypt", args -> ScriptCryptoSupport.aesDecrypt(
                str(args, 0, "aesDecrypt"), str(args, 1, "aesDecrypt"), opts(args, 2, "aesDecrypt")));

        // 国密 SM2 / SM4：末参为 options
        tools.put("sm2Sign", args -> ScriptCryptoSupport.sm2Sign(
                str(args, 0, "sm2Sign"), str(args, 1, "sm2Sign"), opts(args, 2, "sm2Sign")));
        tools.put("sm2Verify", args -> ScriptCryptoSupport.sm2Verify(
                str(args, 0, "sm2Verify"), str(args, 1, "sm2Verify"), str(args, 2, "sm2Verify"),
                opts(args, 3, "sm2Verify")));
        tools.put("sm2Encrypt", args -> ScriptCryptoSupport.sm2Encrypt(
                str(args, 0, "sm2Encrypt"), str(args, 1, "sm2Encrypt"), opts(args, 2, "sm2Encrypt")));
        tools.put("sm2Decrypt", args -> ScriptCryptoSupport.sm2Decrypt(
                str(args, 0, "sm2Decrypt"), str(args, 1, "sm2Decrypt"), opts(args, 2, "sm2Decrypt")));
        tools.put("sm4Encrypt", args -> ScriptCryptoSupport.sm4Encrypt(
                str(args, 0, "sm4Encrypt"), str(args, 1, "sm4Encrypt"), opts(args, 2, "sm4Encrypt")));
        tools.put("sm4Decrypt", args -> ScriptCryptoSupport.sm4Decrypt(
                str(args, 0, "sm4Decrypt"), str(args, 1, "sm4Decrypt"), opts(args, 2, "sm4Decrypt")));

        return Collections.unmodifiableMap(tools);
    }

    /**
     * 注册摘要函数 {@code name(data, encoding?)}。
     *
     * @param algorithm JCA 摘要算法名
     */
    private static void putDigest(Map<String, ProxyExecutable> tools, String name, String algorithm) {
        tools.put(name, args -> ScriptCryptoSupport.digest(algorithm, str(args, 0, name), optStr(args, 1)));
    }

    /**
     * 注册 HMAC 函数 {@code name(data, secret, encoding?)}。
     *
     * @param algorithm JCA Mac 算法名
     */
    private static void putHmac(Map<String, ProxyExecutable> tools, String name, String algorithm) {
        tools.put(name, args -> ScriptCryptoSupport.hmac(
                algorithm, str(args, 0, name), str(args, 1, name), optStr(args, 2)));
    }
}
