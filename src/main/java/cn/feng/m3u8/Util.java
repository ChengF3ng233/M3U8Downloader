package cn.feng.m3u8;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.security.spec.AlgorithmParameterSpec;

class Util {
    public static final Logger logger = LogManager.getLogger("Download Thread");
    private static final String ILLEGAL_CHARACTERS = "[<>:\"/\\|?*]";

    public static String sanitizeFileName(String fileName) {
        if (fileName == null) {
            return null;
        }

        return fileName.replaceAll(ILLEGAL_CHARACTERS, "_");
    }

    public static byte[] hexStringToByteArray(String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }

        if (s.startsWith("0x")) {
            s = s.substring(2);
        }

        int len = s.length();
        if ((len & 1) == 1) {
            s = "0" + s;
            len++;
        }

        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    public static void mkdirs(File... dirs) {
        for (File dir : dirs) {
            if (!dir.exists()) dir.mkdir();
        }
    }

    public static byte[] decryptTS(byte[] sSrc, byte[] sKey, byte[] iv) throws Exception {
        Cipher cipher;
        SecretKeySpec keySpec = new SecretKeySpec(sKey, "AES");
        cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        AlgorithmParameterSpec paramSpec = new IvParameterSpec(iv);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, paramSpec);
        return cipher.doFinal(sSrc);
    }
}
