package utils;

import libs.CryptoUtils;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.HDPath;
import java.math.BigDecimal;
import java.util.*;

public class FxAccount {

    private String fxValidatorAddress = null;
    private String fxDelegatorAddress;
    private CryptoUnit cryptoUnit;
    private DeterministicKey privateKey;
    private BigDecimal balance = BigDecimal.ZERO;
    private Map<String/*fxvaloper*/, Optional<BigDecimal>> rewards = new HashMap<>();
    private Map<String/*fxvaloper*/, Optional<BigDecimal>> delegations = new HashMap<>();
    private Optional<BigDecimal> commissionFee = Optional.of(BigDecimal.ZERO);

    public static FxAccount generateSelfBound(String fxValidatorAddress, List<CryptoUnit> cryptoUnits) throws Exception {
        FxAccount ret = new FxAccount();
        ret.cryptoUnit = CryptoUnit.findValidator(cryptoUnits, fxValidatorAddress);
        ret.privateKey = CryptoUtils.generateKey(ret.cryptoUnit.dh, 0);
        ret.fxDelegatorAddress = CryptoUtils.generateAddress("fx", ret.privateKey).toLowerCase();
        ret.fxValidatorAddress = CryptoUtils.generateAddress("fxvaloper", ret.privateKey).toLowerCase();
        return ret;
    }

    public static FxAccount generateDelegator(String fxDelegatorAddress, List<CryptoUnit> cryptoUnits) throws Exception {
        FxAccount ret = new FxAccount();
        ret.cryptoUnit = CryptoUnit.findDelegator(cryptoUnits, fxDelegatorAddress);
        ret.privateKey = ret.cryptoUnit.dh.get(HDPath.parsePath("44H/118H/0H/0/"+ret.cryptoUnit.lastIndex), true, true);
        ret.fxDelegatorAddress = CryptoUtils.generateAddress("fx", ret.privateKey).toLowerCase();
        return ret;
    }

    public boolean isValidator() {
        return this.fxValidatorAddress != null;
    }

    public static boolean exists(Collection<FxAccount> accounts, String fxDelegatorAddress) {
        for (FxAccount account : accounts) {
            if (account.fxDelegatorAddress.equals(fxDelegatorAddress.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    public static boolean includesValidator(Collection<FxAccount> accounts) {
        return accounts.stream().anyMatch(_acc -> _acc.isValidator());
    }

    public static Optional<FxAccount> getsValidator(Collection<FxAccount> accounts) {
        return accounts.stream().filter(_acc -> _acc.isValidator()).findFirst();
    }

    public String getFxValidatorAddress() {
        return fxValidatorAddress;
    }

    public String getFxDelegatorAddress() {
        return fxDelegatorAddress;
    }

    public DeterministicKey getPrivateKey() {
        return privateKey;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public Map<String, Optional<BigDecimal>> getRewards() {
        return rewards;
    }

    public Map<String, Optional<BigDecimal>> getDelegations() {
        return delegations;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public Optional<BigDecimal> getCommissionFee() {
        return commissionFee;
    }

    public void setCommissionFee(Optional<BigDecimal> commissionFee) {
        this.commissionFee = commissionFee;
    }

    public CryptoUnit getCryptoUnit() {
        return cryptoUnit;
    }
}