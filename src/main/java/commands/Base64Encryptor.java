package commands;

import libs.CryptoUtils;
import picocli.CommandLine;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "encrypt",
        aliases = { "enc" },
        description = "Encrypt an FX seedphrase using salted AES-256 and the provided password, and returns the Base64 representation."
)
public class Base64Encryptor implements Callable<Integer> {

    @Override
    public Integer call() {
        int ret = 0;
        String encryptedValue;
        System.out.println("FrenchXCoreBot - Encryption module...");
        System.out.println();
        try {
            boolean exit = false;
            System.out.println("You can exit any time by providing an empty input...");
            while (!exit) {
                if (System.console() == null) {
                    String password;
                    try (BufferedReader _br = new BufferedReader(new InputStreamReader(System.in))) {
                        System.out.print("Enter root password [then press ENTER]: ");
                        password = _br.readLine();
                        if (!password.isBlank()) {
                            System.out.print("Enter root password again [then press ENTER]: ");
                            String password2 = _br.readLine();
                            if (password.trim().isBlank() || password2.trim().isBlank()) {
                                exit = true;
                            } else if (!password.equals(password2)) {
                                System.out.println("Provided passwords do not match. Please try again.");
                            } else {
                                System.out.println();
                                String value = "";
                                while (value.isBlank()) {
                                    System.out.print("Enter seedphrase to encrypt [then press ENTER]: ");
                                    value = _br.readLine().toLowerCase();
                                    encryptedValue = CryptoUtils.EncryptAndEncode(CryptoUtils.SALT + password, value);
                                    System.out.println("Encrypted value is : " + encryptedValue);
                                }
                                exit = true;
                            }
                        } else {
                            ret = -1;
                            exit = true;
                        }
                    } catch (Exception e) {
                        System.out.println("\nAn exception occurred.");
                        ret = -1;
                    }
                } else {
                    System.out.println();
                    System.out.print("Enter root password [then press ENTER]: ");
                    String password = String.valueOf(System.console().readPassword());
                    if (!password.isBlank()) {
                        System.out.println();
                        System.out.print("Enter root password again [then press ENTER]: ");
                        String password2 = String.valueOf(System.console().readPassword());
                        if (password.trim().isBlank() || password2.trim().isBlank()) {
                            exit = true;
                        } else if (!password.equals(password2)) {
                            System.out.println("Provided passwords do not match. Please try again.");
                        } else {
                            System.out.println();
                            String value = "";
                            while (value.isBlank()) {
                                System.out.print("Enter value to encrypt [then press ENTER]: ");
                                value = String.valueOf(System.console().readPassword());
                                encryptedValue = CryptoUtils.EncryptAndEncode(CryptoUtils.SALT + password, value);
                                System.out.println("Encrypted value is : " + encryptedValue);
                            }
                            exit = true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("An exception occurred.");
            ret = -1;
        }
        return ret;
    }

}
