package libs;

import cosmos.tx.v1beta1.TxOuterClass;
import eu.frenchxcore.tools.Bech32;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.crypto.DeterministicHierarchy;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.HDKeyDerivation;
import org.bitcoinj.crypto.HDPath;
import org.bitcoinj.wallet.DeterministicSeed;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Arrays;
import java.util.Base64;

public class CryptoUtils {

    public static final String SALT = "!%Fr3nchXc0re+Aut0C0mp0und3r!$";

    /**
     * Hierarchical Deterministic path for FunctionX
     */
    private static final String HD_PATH = "44H/118H/0H/0/0";

    private final static String ALGORITHM = "AES/CBC/PKCS5Padding";
    private static Cipher CIPHER;
    private static IvParameterSpec IV_SPEC = new IvParameterSpec(new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0});
    private static SecretKeySpec ROOT_KEY = null;
    private static MessageDigest SHA256 = null;

    static {
        try {
            CIPHER = Cipher.getInstance("AES/CBC/PKCS5PADDING");
            SHA256 = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            CIPHER = null;
            SHA256 = null;
            e.printStackTrace();
        } catch (NoSuchPaddingException e) {
            CIPHER = null;
            SHA256 = null;
            e.printStackTrace();
        }
    }

    public static String getSHA(String input) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return Base64.getEncoder().encodeToString(md.digest(input.getBytes(StandardCharsets.UTF_8)));
    }

    public static String EncryptAndEncode(String password, String value) throws InvalidKeyException, IllegalBlockSizeException, BadPaddingException, NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeySpecException {
        CIPHER.init(Cipher.ENCRYPT_MODE, generateKey(password), IV_SPEC);
        return Base64.getEncoder().encodeToString(CIPHER.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }

    public static String DecodeAndDecrypt(String password, String value) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException, InvalidKeySpecException, InvalidAlgorithmParameterException {
        CIPHER.init(Cipher.DECRYPT_MODE, generateKey(password), IV_SPEC);
        return new String(CIPHER.doFinal(Base64.getDecoder().decode(value)));
    }

    public static String HashAndEncode(String value) {
        SHA256.reset();
        return Base64.getEncoder().encodeToString(SHA256.digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static SecretKeySpec generateKey(String password) throws NoSuchAlgorithmException, InvalidKeySpecException {
        if (ROOT_KEY == null) {
            /* Create factory for secret keys. */
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            /* PBEKeySpec class implements KeySpec interface. */
            KeySpec spec = new PBEKeySpec(password.toCharArray(), SALT.getBytes(), 65536, 256);
            SecretKey tmp = factory.generateSecret(spec);
            ROOT_KEY = new SecretKeySpec(tmp.getEncoded(), "AES");
        }
        return ROOT_KEY;
    }

    public static DeterministicSeed generateSeed(String mnemonic) {
        DeterministicSeed seed = null;
        if (mnemonic != null) {
            mnemonic = mnemonic.trim().replaceAll("\\s+", " ").toLowerCase();
        }
        try {
            seed = new DeterministicSeed(mnemonic, null, "", 0);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return seed;
    }

    public static DeterministicKey generateRootKey(DeterministicSeed seed) {
        DeterministicKey rootKey = HDKeyDerivation.createMasterPrivateKey(seed.getSeedBytes());
        return rootKey;
    }

    public static DeterministicHierarchy generateHierarchy(DeterministicKey rootKey) {
        return new DeterministicHierarchy(rootKey);
    }

    public static DeterministicKey generateKey(DeterministicHierarchy dh, int index) {
        HDPath hdPath = HDPath.parsePath(HD_PATH);
        DeterministicKey dk = dh.get(hdPath, true, true);
        int i = 0;
        while (i < index) {
            dk = dh.deriveNextChild(hdPath, true, false, true);
            i++;
        }
        return dk;
    }

    public static String generateAddress(String prefix, DeterministicKey key) throws Exception {
        return Bech32.ConvertAndEncode(prefix, key.getPubKeyHash());
    }

    public static byte[] signTransaction(TxOuterClass.SignDoc signDoc, DeterministicKey key) {
        ECKey eckey = ECKey.fromPrivate(key.getPrivKey().toByteArray());
        Sha256Hash msgHash = Sha256Hash.of(signDoc.toByteArray());
        ECKey.ECDSASignature _s = eckey.sign(msgHash);
        byte[] signature = new byte[64];
        Arrays.fill(signature, (byte) 0);
        byte[] _sR = _s.r.toByteArray();
        if (_sR.length > 32) {
            System.arraycopy(_sR, _sR.length-32, signature, 0, 32);
        } else {
            System.arraycopy(_sR, 0, signature, 32-_sR.length, _sR.length);
        }
        byte[] _sS = _s.s.toByteArray();
        if (_sS.length > 32) {
            System.arraycopy(_sS, _sS.length-32, signature, 32, 32);
        } else {
            System.arraycopy(_sS, 0, signature, 64-_sS.length, _sS.length);
        }
        return signature;
    }

}
