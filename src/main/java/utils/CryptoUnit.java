package utils;

import libs.CryptoUtils;
import org.bitcoinj.crypto.DeterministicHierarchy;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.HDPath;

import java.util.ArrayList;
import java.util.List;

public class CryptoUnit {

    public DeterministicHierarchy dh;
    public DeterministicKey dk;
    public int lastIndex = -1;

    public static CryptoUnit findValidator(List<CryptoUnit> cryptoUnits, String fxValidatorAddress) throws Exception {
        CryptoUnit ret = null;
        for (CryptoUnit _cu : cryptoUnits) {
            DeterministicKey _dk = CryptoUtils.generateKey(_cu.dh, 0);
            if (CryptoUtils.generateAddress("fxvaloper", _dk).toLowerCase().equals(fxValidatorAddress)) {
                ret = _cu;
                break;
            }
        }
        return ret;
    }

    public static CryptoUnit findDelegator(List<CryptoUnit> cryptoUnits, String fxDelegatorAddress) throws Exception {
        CryptoUnit ret = null;
        for (CryptoUnit _cu : cryptoUnits) {
            _cu.lastIndex = -1;
            List<String> potentialDelegatorAddresses = new ArrayList<>();
            for (int index = 0 ; index < 512 ; index++) {
                if (CryptoUtils.generateAddress("fx", _cu.dh.get(HDPath.parsePath("44H/118H/0H/0/"+index), true, true)).toLowerCase().equals(fxDelegatorAddress)) {
                    ret = _cu;
                    _cu.lastIndex = index;
                    break;
                }
            }
        }
        return ret;
    }

}
