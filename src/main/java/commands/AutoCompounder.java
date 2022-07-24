package commands;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import cosmos.base.query.v1beta1.Pagination;
import cosmos.tx.signing.v1beta1.Signing;
import cosmos.tx.v1beta1.ServiceOuterClass;
import cosmos.tx.v1beta1.TxOuterClass;
import eu.frenchxcore.api.CosmosGrpcApi;
import eu.frenchxcore.tools.LocalExecutor;
import libs.CryptoUtils;
import picocli.CommandLine;
import utils.CryptoUnit;
import utils.FxAccount;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@CommandLine.Command(name = "autocompound", aliases = { "ac" }, description = "To run your FX auto-compounder configuration")
public class AutoCompounder implements Callable<Boolean> {

    private static final Long GAS_COST = 4000000000000L;
    private static final Double GAS_COEFF = 1.2;

    /**
     * The FX node IP used for transactions
     */
    @CommandLine.Option(names = { "-n", "--node-ip" }, defaultValue = "167.86.101.244", description = "The FX node IP address")
    private String nodeIP;

    /**
     * the FX node gRPC port (Cosmos gRPC) used for transactions
     */
    @CommandLine.Option(names = { "-p", "--node-port" }, defaultValue = "9090", description = "The FX node RPC (Cosmos gRPC)", type = Integer.class)
    private Integer nodeRpcPort;

    /**
     * The chain name used (must be compatible with the node you're connected to)
     */
    private final static String CHAIN_ID = "fxcore";

    /**
     * The FX validator address if you wish to withdraw commissions
     */
    @CommandLine.Option(names = { "-v" , "--validators" }, split=":", description = "The FX validator addresses on which commissions will be withdrawn (separated with a ':').")
    private List<String> fxValidatorAddresses = null;

    private final static String FRENCHXCORE_VAL = "fxvaloper1z67rkadwrp2nf4zwxpktpqnw969plelyjj5alt";

    /**
     * All FX delegator addresses which will automatically be auto-compounded (i.e. redelegate their rewards to the specified FX validator)
     * Note: FX validator self-bound delegator address will be automatically added if 'validatorOwner' or 'withdrawCommissions' is set to 'true'.
     */
    @CommandLine.Option(names = { "-d" , "--delegators" }, split = ":", description = "The FX delegator addresses to autocompound (separated with a ':').")
    private List<String> fxDelegatorAddresses = new ArrayList<>();

    /**
     * The minimum balance (including rewards, commission or both) required to provoke autocompound
     */
    @CommandLine.Option(names = { "-m" , "--minimum-withdraw" }, defaultValue = "100", description = "The minimum cumulated $FX rewards (and commission) to withdraw (min=10)")
    private BigDecimal minimumWithdraw;

    /**
     * The minimum $FX to keep unstaked on each FX delegator address
     */
    @CommandLine.Option(names = { "-k" , "--keep-unstaked" }, defaultValue = "5", description = "The minimum $FX to keep unstaked on each FX delegator address (min=2)")
    private BigDecimal keepUnstaked;

    /**
     * The period (in seconds) when balance will be rechecked for auto-compound
     */
    @CommandLine.Option(names = { "-t" , "--recheck-period" }, defaultValue = "300", description = "The period (in seconds) when balance will be rechecked for auto-compound (max=86400).")
    private Integer recheckPeriodSeconds;

    /**
     * All the necessary Base64-encoded encrypted seedphrases
     * The fotware will automatically detect to which validators or delegators they are attached.
     */
    @CommandLine.Option(names = { "-s" , "--seed" }, required = true, split = ":", description = "All your Base64-encoded encrypted seedphrases (separated with a ':').")
    private List<String> encryptedSeedPhrases;

    /**
     * Internal variable to compute average gains per day
     */
    private BigDecimal oneDayPeriods;

    /**
     * The seedphrase encryption password (previously used with '--encrypt' command)
     */
    private String password;

    private CosmosGrpcApi client;
    private BigDecimal totalBalance = BigDecimal.ZERO;
    private BigDecimal commissionFee = BigDecimal.ZERO;
    private BigDecimal allRewards = BigDecimal.ZERO;
    private BigDecimal allDelegations = BigDecimal.ZERO;
    private BigDecimal pendingRewardsAndCommission;
    private BigDecimal avgEarningPerDay = null;
    private final List<CryptoUnit> cryptoUnits = new ArrayList<>();
    private final Set<FxAccount> accounts = new LinkedHashSet<>();

    public static class Autocompound implements Runnable {

        private final AutoCompounder ac;

        public Autocompound(AutoCompounder ac) {
            this.ac = ac;
        }

        @Override
        public void run() {
            try {
                // Update all pending commission fees and rewards for all accounts
                if (this.ac.updateCommissionFeesAndRewards()) {
                    // Display overall pending commission fees and rewards cumulated for all accounts
                    System.out.println(
                            new Date() + " : " +
                                    "UnstakedBalance=" + this.ac.totalBalance.setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + " $FX" +
                                    " - Pending(R&C)=" + this.ac.pendingRewardsAndCommission.setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + " $FX " +
                                    " - Delegations=" + this.ac.allDelegations.setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + " $FX " +
                                    (this.ac.avgEarningPerDay != null ? "(" + this.ac.avgEarningPerDay.setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + " $FX/day)" : "")
                    );
                    List<FxAccount> accountsToProcess = new ArrayList<>();
                    this.ac.accounts.forEach(_account -> {
                        BigDecimal accountPendingCommissionAndRewards = this.ac.computeCommissionAndRewards(_account);
                        if (accountPendingCommissionAndRewards.compareTo(this.ac.minimumWithdraw) > 0) {
                            accountsToProcess.add(_account);
                        }
                    });
                    this.ac.withdrawAndRestake(accountsToProcess);
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    @Override
    public Boolean call() throws Exception {
        try {
            this.client = CosmosGrpcApi.getInstance(this.nodeIP, this.nodeRpcPort, LocalExecutor.getInstance().get());
            this.client.tmGetNodeInfo().get();
        } catch (Exception ex) {
            throw new IllegalArgumentException("!!! ERROR !!! There was an error while trying to connect to the FXCore mainnet node.");
        }
        try {
            this.decryptPrivateKeysWithPassword();
        } catch (Exception ex) {
            throw new IllegalArgumentException("!!! ERROR !!! There was an error processing your seedphrase.");
        }
        if (this.fxValidatorAddresses != null && !this.fxValidatorAddresses.isEmpty()) {
            for (String fxValidatorAddress : this.fxValidatorAddresses) {
                FxAccount account;
                try {
                    account = FxAccount.generateSelfBound(fxValidatorAddress, this.cryptoUnits);
                } catch (Exception ex) {
                    throw new IllegalArgumentException("!!! ERROR !!! There was an error while adding your validator self-bound delegator address.");
                }
                if (!account.getFxValidatorAddress().equals(fxValidatorAddress)) {
                    throw new IllegalArgumentException("!!! ERROR !!! The FX Validator address you specified does not match your seedphrase.");
                }
                this.accounts.add(account);
                System.out.printf("NOTICE: FX validator address '%s' was automatically added for commission fee withdrawals.%n", account.getFxValidatorAddress());
                System.out.printf("NOTICE: FX validator self-bound address '%s' was automatically added to list of specified delegators for rewards withdrawals.%n", account.getFxDelegatorAddress());
            }
        }
        List<String> _fxDelegatorAddresses = new ArrayList<>();
        this.fxDelegatorAddresses.stream().map(String::toLowerCase).forEach(_fxDelegatorAddresses::add);
        this.fxDelegatorAddresses = null;
        if (!addDelegatorAddresses(_fxDelegatorAddresses)) {
            throw new IllegalArgumentException("!!! ERROR !!! At least one of the specified delegator addresses does not match your private key.");
        }
        if (this.accounts.isEmpty()) {
            throw new IllegalArgumentException("!!! ERROR  !!! At least one delegator or validator must be specified.");
        }
        if (this.minimumWithdraw.compareTo(BigDecimal.valueOf(10)) < 0) {
            throw new IllegalArgumentException("!!! ERROR  !!! The minimum withdraw must be greater or equal to 10 $FX.");
        }
        if (this.keepUnstaked.compareTo(BigDecimal.valueOf(2)) < 0) {
            throw new IllegalArgumentException("!!! ERROR  !!! The keep unstaked must be greater or equal to 2 $FX.");
        }
        if (this.recheckPeriodSeconds > 86400) {
            throw new IllegalArgumentException("!!! ERROR  !!! The recheckPeriod (-t, --recheck-period) must be less than 86400 seconds.");
        }
        this.oneDayPeriods = new BigDecimal(86400, MathContext.UNLIMITED).divide(BigDecimal.valueOf(this.recheckPeriodSeconds), RoundingMode.HALF_UP);
        ScheduledFuture<?> future = Executors.newScheduledThreadPool(1).scheduleAtFixedRate(this.getThread(), 0, this.recheckPeriodSeconds, TimeUnit.SECONDS);
        System.out.println("Press 'Q'+[ENTER] to stop Fr3nchXC0re $FX Auto-Compounder...");
        boolean quit = false;
        String q;
        do {
            Scanner scanner = new Scanner(System.in);
            if (scanner.hasNext() && !(q = scanner.nextLine()).isBlank()) {
                quit = (q.toUpperCase().contains("Q"));
            }
        } while (!quit);
        future.cancel(true);
        return true;
    }

    public Runnable getThread() {
        return new Autocompound(this);
    }

    private void decryptPrivateKeysWithPassword() {
        if (this.encryptedSeedPhrases != null) {
            try {
                String password = "";
                if (System.console() == null) {
                    try (BufferedReader _br = new BufferedReader(new InputStreamReader(System.in))) {
                        System.out.println();
                        while (password.isBlank()) {
                            System.out.print("Please enter your root password [then press ENTER]: ");
                            password = _br.readLine();
                        }
                    }
                } else {
                    System.out.println();
                    while (password.isBlank()) {
                        System.out.print("Please enter your root password [then press ENTER]: ");
                        password = String.valueOf(System.console().readPassword());
                    }
                }
                for (String encryptedSeedPhrase : this.encryptedSeedPhrases) {
                    CryptoUnit _cu = new CryptoUnit();
                    _cu.dk = CryptoUtils.generateRootKey(CryptoUtils.generateSeed(CryptoUtils.DecodeAndDecrypt(CryptoUtils.SALT + password, encryptedSeedPhrase)));
                    _cu.dh = CryptoUtils.generateHierarchy(_cu.dk);
                    this.cryptoUnits.add(_cu);
                }
            } catch (Exception e) {
                throw new IllegalArgumentException("!!! ERROR !!! Cannot decrypt private keys with the provided password");
            }
        } else {
            throw new IllegalArgumentException("!!! ERROR !!! You forgot to specify the seedphrase(s).");
        }
    }

    private boolean updateCommissionFeesAndRewards() {
        // Let's query the $FX balance for each FX delegator address
        AtomicBoolean err = new AtomicBoolean(false);
        this.accounts.forEach(fxAccount -> {
            try {
                cosmos.bank.v1beta1.QueryOuterClass.QueryBalanceResponse _balance = this.client.bankQueryBalance(fxAccount.getFxDelegatorAddress(), "FX").get();
                fxAccount.setBalance(new BigDecimal(_balance.getBalance().getAmount()).movePointLeft(18));
            } catch (Exception ex) {
                err.set(true);
                ex.printStackTrace();
            }
        });
        if (err.get()) {
            return false;
        }

        // Compute the overall $FX balance for all FX delegator addresses
        this.totalBalance = BigDecimal.ZERO;
        this.accounts.stream().map(FxAccount::getBalance).reduce(BigDecimal::add).ifPresent(_b -> this.totalBalance = _b);

        // If commissions are to be withdrawn, let's query the validator pending commission fees
        this.commissionFee = BigDecimal.ZERO;
        this.accounts.stream().filter(FxAccount::isValidator).forEach(_validator -> {
            try {
                cosmos.distribution.v1beta1.QueryOuterClass.QueryValidatorCommissionResponse _commission = client.distributionQueryValidatorCommission(_validator.getFxValidatorAddress()).get();
                Optional<BigDecimal> _commissionFee = _commission.getCommission().getCommissionList().stream()
                        .filter(_d -> _d.getDenom().equals("FX"))
                        .map(_d -> new BigInteger(_d.getAmount()))
                        .reduce(BigInteger::add)
                        .map(_d -> new BigDecimal(_d).movePointLeft(36));
                _validator.setCommissionFee(_commissionFee);
                _commissionFee.ifPresent(_c -> this.commissionFee = this.commissionFee.add(_c));
            } catch (Exception ex) {
                err.set(true);
                ex.printStackTrace();
            }
        });
        if (err.get()) {
            return false;
        }

        // Let's query the $FX pending rewards for each FX delegator address
        this.allRewards = BigDecimal.ZERO;
        this.allDelegations = BigDecimal.ZERO;
        this.accounts.forEach(fxAccount -> {
            try {
                fxAccount.getRewards().clear();
                Map<String/*fxvaloper*/, Optional<BigDecimal>> vRewards = new HashMap<>();
                this.client.stakingQueryDelegatorValidators(fxAccount.getFxDelegatorAddress()).get().getValidatorsList().forEach(_v -> {
                    try {
                        cosmos.distribution.v1beta1.QueryOuterClass.QueryDelegationRewardsResponse _rewards = client.distributionQueryDelegationRewards(fxAccount.getFxDelegatorAddress(), _v.getOperatorAddress()).get();
                        Optional<BigDecimal> _b = _rewards.getRewardsList().stream()
                                .filter(_r -> _r.getDenom().equals("FX"))
                                .map(_r -> new BigDecimal(_r.getAmount()).movePointLeft(36))
                                .reduce(BigDecimal::add);
                        vRewards.put(_v.getOperatorAddress(), _b);
                        _b.ifPresent((rewards) -> this.allRewards = this.allRewards.add(rewards));
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        err.set(true);
                    }
                });
                fxAccount.getRewards().putAll(vRewards);
            } catch (Exception ex) {
                err.set(true);
                ex.printStackTrace();
            }
            try {
                Map<String/*fxvaloper*/, Optional<BigDecimal>> vDelegations = new HashMap<>();
                ByteString pageKey = ByteString.EMPTY;
                do {
                    Pagination.PageRequest pr = Pagination.PageRequest.newBuilder().setKey(pageKey).build();
                    cosmos.staking.v1beta1.QueryOuterClass.QueryDelegatorDelegationsResponse _delegations = client.stakingQueryDelegatorDelegations(fxAccount.getFxDelegatorAddress(), pr).get();
                    Optional<BigDecimal> _b = _delegations.getDelegationResponsesList().stream()
                            .map(_r -> new BigDecimal(_r.getDelegation().getShares()).movePointLeft(36))
                            .reduce(BigDecimal::add);
                    vDelegations.put(fxAccount.getFxValidatorAddress(), _b);
                    _b.ifPresent((delegations) -> this.allDelegations = this.allDelegations.add(delegations));
                    pageKey = _delegations.getPagination().getNextKey();
                } while (!pageKey.isEmpty());
            } catch (Exception ex) {
                ex.printStackTrace();
                err.set(true);
            }
        });
        if (err.get()) {
            return false;
        }

        BigDecimal oldValue = this.pendingRewardsAndCommission;
        this.pendingRewardsAndCommission = this.commissionFee;
        this.pendingRewardsAndCommission = this.pendingRewardsAndCommission.add(this.allRewards);
        if (oldValue != null) {
            this.avgEarningPerDay = this.pendingRewardsAndCommission.subtract(oldValue).multiply(oneDayPeriods);
        }
        return true;
    }

    private BigDecimal computeCommissionAndRewards(FxAccount account) {
        BigDecimal pending = BigDecimal.ZERO;
        if (account.isValidator()) {
            pending = pending.add(account.getCommissionFee().isPresent() ? account.getCommissionFee().get() : BigDecimal.ZERO);
        }
        Optional<BigDecimal> _rewards = account.getRewards().values().stream().filter(Optional::isPresent).map(Optional::get).reduce(BigDecimal::add);
        pending = pending.add(_rewards.orElse(BigDecimal.ZERO)).subtract(this.keepUnstaked);
        return pending;
    }

    private void withdrawAndRestake(List<FxAccount> accounts) {
        long expectedGas = this.simulateGas(accounts);
        long consumedGas = 0;
        if (expectedGas > 0) {
            consumedGas = this.executeTransaction(accounts, expectedGas);
        }
    }

    private long simulateGas(List<FxAccount> accounts) {
        System.out.println("   " + new Date() + " : Simulating gas for transaction");
        AtomicLong totalGas = new AtomicLong(0L);
        Map<FxAccount, cosmos.auth.v1beta1.Auth.BaseAccount> baseAccounts = new LinkedHashMap<>();
        AtomicBoolean txToProcess = new AtomicBoolean(false);
        TxOuterClass.TxBody.Builder bodyBuilder = TxOuterClass.TxBody.newBuilder()
                .setMemo("FrenchXCore AutoCompounder")
                .setTimeoutHeight(0);
        accounts.forEach(_account -> {
            cosmos.auth.v1beta1.QueryOuterClass.QueryAccountResponse _accountResponse = null;
            try {
                _accountResponse = client.authQueryAccount(_account.getFxDelegatorAddress()).get();
                if (_accountResponse != null) {
                    cosmos.auth.v1beta1.Auth.BaseAccount _baseAccount = null;
                    if (_accountResponse.getAccount().is(cosmos.auth.v1beta1.Auth.BaseAccount.class)) {
                        _baseAccount = _accountResponse.getAccount().unpack(cosmos.auth.v1beta1.Auth.BaseAccount.class);
                        baseAccounts.put(_account, _baseAccount);
                    } else {
                        throw new Exception();
                    }
                }
            } catch (Exception ex) {
                throw new IllegalArgumentException("!!! ERROR !!! Account query failed for FxAccount "
                        + (_account.isValidator() ?
                        "'" + _account.getFxValidatorAddress() + "'/'" + _account.getFxDelegatorAddress() + "'" :
                        "'" + _account.getFxDelegatorAddress() + "'"));
            }
        });
        Set<FxAccount> signaturesRequired = new LinkedHashSet<>();
        baseAccounts.entrySet().forEach(_eBaseAccount -> {
            FxAccount _account = _eBaseAccount.getKey();
            cosmos.auth.v1beta1.Auth.BaseAccount _baseAccount = _eBaseAccount.getValue();
            AtomicReference<BigDecimal> _amountToRestake = new AtomicReference<>(BigDecimal.ZERO);
            if (_account.isValidator() && _account.getCommissionFee().isPresent() && _account.getCommissionFee().get().compareTo(BigDecimal.ONE) > 0) {
                cosmos.distribution.v1beta1.Tx.MsgWithdrawValidatorCommission msgWithdrawValidatorCommission = cosmos.distribution.v1beta1.Tx.MsgWithdrawValidatorCommission.newBuilder()
                        .setValidatorAddress(_account.getFxValidatorAddress())
                        .build();
                bodyBuilder.addMessages(Any.pack(msgWithdrawValidatorCommission, ""));
                System.out.println("   " + new Date() + " : TX : Adding Withdraw validator commission for validator '" + _account.getFxValidatorAddress() + "' : " + _account.getCommissionFee().get().setScale(4, RoundingMode.HALF_UP).toPlainString() + " $FX.");
                signaturesRequired.add(_account);
                _amountToRestake.set(_amountToRestake.get().add(_account.getCommissionFee().get()));
            }
            _account.getRewards().entrySet().forEach(_eValidatorRewards -> {
                _eValidatorRewards.getValue().filter(_rewards -> {
                    return _rewards.compareTo(BigDecimal.ONE) > 0;
                }).ifPresent(_rewards -> {
                    cosmos.distribution.v1beta1.Tx.MsgWithdrawDelegatorReward msgWithdrawRewards = cosmos.distribution.v1beta1.Tx.MsgWithdrawDelegatorReward.newBuilder()
                            .setDelegatorAddress(_account.getFxDelegatorAddress())
                            .setValidatorAddress(_eValidatorRewards.getKey())
                            .build();
                    bodyBuilder.addMessages(Any.pack(msgWithdrawRewards, ""));
                    System.out.println("   " + new Date() + " : TX : Adding Withdraw delegator rewards for delegator '" + _account.getFxDelegatorAddress() + "' on validator '" + _eValidatorRewards.getKey() + "' : " + _rewards.setScale(4, RoundingMode.HALF_UP).toPlainString() + " $FX.");
                    signaturesRequired.add(_account);
                    _amountToRestake.set(_amountToRestake.get().add(_rewards));
                });
            });
            _amountToRestake.set(_amountToRestake.get().subtract(this.keepUnstaked));
            if (_amountToRestake.get().compareTo(this.minimumWithdraw) > 0) {
                cosmos.staking.v1beta1.Tx.MsgDelegate msgDelegate = cosmos.staking.v1beta1.Tx.MsgDelegate.newBuilder()
                    .setDelegatorAddress(_account.getFxDelegatorAddress())
                    .setValidatorAddress(FRENCHXCORE_VAL)
                    .setAmount(cosmos.base.v1beta1.CoinOuterClass.Coin.newBuilder()
                            .setAmount(_amountToRestake.get().movePointRight(18).toBigInteger().toString())
                            .setDenom("FX")
                            .build())
                    .build();
                bodyBuilder.addMessages(Any.pack(msgDelegate, ""));
                System.out.println("   " + new Date() + " : TX : Adding Delegate for delegator '" + _account.getFxDelegatorAddress() + "' on validator '" + FRENCHXCORE_VAL + "' : " + _amountToRestake.get().setScale(4, RoundingMode.HALF_UP).toPlainString() + " $FX.");
                txToProcess.set(true);
            }
        });

        if (txToProcess.get()) {
            TxOuterClass.TxBody body = bodyBuilder.build();
            TxOuterClass.AuthInfo.Builder _fakeAuthInfoBuilder = TxOuterClass.AuthInfo.newBuilder()
                    .setFee(TxOuterClass.Fee.newBuilder().addAmount(cosmos.base.v1beta1.CoinOuterClass.Coin.newBuilder().setDenom("FX").setAmount("0").build()).setGasLimit(0).build());
            TxOuterClass.Tx.Builder _simTxBuilder = TxOuterClass.Tx.newBuilder();
            baseAccounts.entrySet().forEach(_eBaseAccount -> {
                TxOuterClass.SignerInfo _fakeSignerInfo = TxOuterClass.SignerInfo.newBuilder()
                        .setModeInfo(TxOuterClass.ModeInfo.newBuilder().setSingle(TxOuterClass.ModeInfo.Single.newBuilder().setMode(Signing.SignMode.SIGN_MODE_UNSPECIFIED).build()).build())
                        .setPublicKey(Any.pack(cosmos.crypto.secp256k1.Keys.PubKey.newBuilder().setKey(ByteString.EMPTY).build(), ""))
                        .setSequence(_eBaseAccount.getValue().getSequence())
                        .build();
                _fakeAuthInfoBuilder.addSignerInfos(_fakeSignerInfo);
                _simTxBuilder.addSignatures(ByteString.EMPTY);
            });
            TxOuterClass.Tx _simTx = _simTxBuilder.setBody(body)
                    .setAuthInfo(_fakeAuthInfoBuilder.build())
                    .build();

            try {
                totalGas.addAndGet(this.client.txSimulate(_simTx).get().getGasInfo().getGasUsed());
                System.out.println("Total gas required : " + totalGas.get() + " --> " + BigDecimal.valueOf(GAS_COST*totalGas.get()).movePointLeft(18).setScale(4, RoundingMode.HALF_UP).toPlainString() + " $FX");
            } catch (Exception ex) {
                throw new IllegalArgumentException("!!! ERROR !!! Could not simulate Tx for gas : " + ex.getMessage());
            }
        }
        return totalGas.get();
    }

    private long executeTransaction(List<FxAccount> accounts, long expectedGas) {
        System.out.println("   " + new Date() + " : executing transaction");
        AtomicLong realGas = new AtomicLong(0L);
        AtomicBoolean firstTransaction = new AtomicBoolean(true);
        BigDecimal bGasPrice = BigDecimal.valueOf(expectedGas).multiply(BigDecimal.valueOf(GAS_COEFF)).setScale(0, RoundingMode.HALF_UP);
        BigDecimal bGasFee = BigDecimal.valueOf(GAS_COST).multiply(bGasPrice).stripTrailingZeros();
        Map<FxAccount, cosmos.auth.v1beta1.Auth.BaseAccount> baseAccounts = new LinkedHashMap<>();
        AtomicBoolean txToProcess = new AtomicBoolean(false);
        TxOuterClass.TxBody.Builder bodyBuilder = TxOuterClass.TxBody.newBuilder()
                .setMemo("FrenchXCore AutoCompounder : " + new Date().toString())
                .setTimeoutHeight(0);
        accounts.forEach(_account -> {
            cosmos.auth.v1beta1.QueryOuterClass.QueryAccountResponse _accountResponse = null;
            try {
                _accountResponse = client.authQueryAccount(_account.getFxDelegatorAddress()).get();
                if (_accountResponse != null) {
                    cosmos.auth.v1beta1.Auth.BaseAccount _baseAccount = null;
                    if (_accountResponse.getAccount().is(cosmos.auth.v1beta1.Auth.BaseAccount.class)) {
                        _baseAccount = _accountResponse.getAccount().unpack(cosmos.auth.v1beta1.Auth.BaseAccount.class);
                        baseAccounts.put(_account, _baseAccount);
                    } else {
                        throw new Exception();
                    }
                }
            } catch (Exception ex) {
                throw new IllegalArgumentException("!!! ERROR !!! Account query failed for FxAccount "
                        + (_account.isValidator() ?
                        "'" + _account.getFxValidatorAddress() + "'/'" + _account.getFxDelegatorAddress() + "'" :
                        "'" + _account.getFxDelegatorAddress() + "'"));
            }
        });
        Set<FxAccount> signaturesRequired = new LinkedHashSet<>();
        baseAccounts.entrySet().forEach(_eBaseAccount -> {
            FxAccount _account = _eBaseAccount.getKey();
            cosmos.auth.v1beta1.Auth.BaseAccount _baseAccount = _eBaseAccount.getValue();
            AtomicReference<BigDecimal> _amountToRestake = new AtomicReference<>(BigDecimal.ZERO);
            if (_account.isValidator() && _account.getCommissionFee().isPresent() && _account.getCommissionFee().get().compareTo(BigDecimal.ONE) > 0) {
                cosmos.distribution.v1beta1.Tx.MsgWithdrawValidatorCommission msgWithdrawValidatorCommission = cosmos.distribution.v1beta1.Tx.MsgWithdrawValidatorCommission.newBuilder()
                        .setValidatorAddress(_account.getFxValidatorAddress())
                        .build();
                bodyBuilder.addMessages(Any.pack(msgWithdrawValidatorCommission, ""));
                System.out.println("   " + new Date() + " : TX : Adding Withdraw validator commission for validator '" + _account.getFxValidatorAddress() + "' : " + _account.getCommissionFee().get().setScale(4, RoundingMode.HALF_UP).toPlainString() + " $FX.");
                signaturesRequired.add(_account);
                _amountToRestake.set(_amountToRestake.get().add(_account.getCommissionFee().get()));
            }
            _account.getRewards().entrySet().forEach(_eValidatorRewards -> {
                _eValidatorRewards.getValue().filter(_rewards -> {
                    return _rewards.compareTo(BigDecimal.ONE) > 0;
                }).ifPresent(_rewards -> {
                    cosmos.distribution.v1beta1.Tx.MsgWithdrawDelegatorReward msgWithdrawRewards = cosmos.distribution.v1beta1.Tx.MsgWithdrawDelegatorReward.newBuilder()
                            .setDelegatorAddress(_account.getFxDelegatorAddress())
                            .setValidatorAddress(_eValidatorRewards.getKey())
                            .build();
                    bodyBuilder.addMessages(Any.pack(msgWithdrawRewards, ""));
                    System.out.println("   " + new Date() + " : TX : Adding Withdraw delegator rewards for delegator '" + _account.getFxDelegatorAddress() + "' on validator '" + _eValidatorRewards.getKey() + "' : " + _rewards.setScale(4, RoundingMode.HALF_UP).toPlainString() + " $FX.");
                    signaturesRequired.add(_account);
                    _amountToRestake.set(_amountToRestake.get().add(_rewards));
                });
            });
            _amountToRestake.set(_amountToRestake.get().subtract(this.keepUnstaked));
            if (firstTransaction.get()) {
                firstTransaction.set(false);
                _amountToRestake.set(_amountToRestake.get().subtract(bGasFee.movePointLeft(18)));
            }
            if (_amountToRestake.get().compareTo(this.minimumWithdraw) > 0) {
                cosmos.staking.v1beta1.Tx.MsgDelegate msgDelegate = cosmos.staking.v1beta1.Tx.MsgDelegate.newBuilder()
                        .setDelegatorAddress(_account.getFxDelegatorAddress())
                        .setValidatorAddress(FRENCHXCORE_VAL)
                        .setAmount(cosmos.base.v1beta1.CoinOuterClass.Coin.newBuilder()
                                .setAmount(_amountToRestake.get().movePointRight(18).toBigInteger().toString())
                                .setDenom("FX")
                                .build())
                        .build();
                bodyBuilder.addMessages(Any.pack(msgDelegate, ""));
                System.out.println("   " + new Date() + " : TX : Adding Delegate for delegator '" + _account.getFxDelegatorAddress() + "' on validator '" + FRENCHXCORE_VAL + "' : " + _amountToRestake.get().setScale(4, RoundingMode.HALF_UP).toPlainString() + " $FX.");
                txToProcess.set(true);
            }
        });

        if (txToProcess.get()) {
            TxOuterClass.Tx signedTx = null;
            TxOuterClass.Tx.Builder _txBuilder = TxOuterClass.Tx.newBuilder();
            TxOuterClass.AuthInfo.Builder _authInfoBuilder = TxOuterClass.AuthInfo.newBuilder()
                    .setFee(TxOuterClass.Fee.newBuilder()
                            .addAmount(cosmos.base.v1beta1.CoinOuterClass.Coin.newBuilder().setDenom("FX").setAmount(Long.toString(bGasFee.longValue())).build())
                            .setGasLimit(bGasPrice.longValue())
                            .build()
                    );
            for (FxAccount _account : signaturesRequired) {
                cosmos.crypto.secp256k1.Keys.PubKey publicKeyFrom = cosmos.crypto.secp256k1.Keys.PubKey.newBuilder()
                        .setKey(ByteString.copyFrom(_account.getPrivateKey().getPubKey()))
                        .build();
                TxOuterClass.SignerInfo _signerInfo = TxOuterClass.SignerInfo.newBuilder()
                        .setModeInfo(TxOuterClass.ModeInfo.newBuilder().setSingle(TxOuterClass.ModeInfo.Single.newBuilder().setMode(Signing.SignMode.SIGN_MODE_DIRECT).build()).build())
                        .setPublicKey(Any.pack(publicKeyFrom, ""))
                        .setSequence(baseAccounts.get(_account).getSequence())
                        .build();
                _authInfoBuilder.addSignerInfos(_signerInfo);
            }
            cosmos.tx.v1beta1.TxOuterClass.TxBody body = bodyBuilder.build();
            for (FxAccount _account : signaturesRequired) {
                TxOuterClass.SignDoc signDoc = TxOuterClass.SignDoc.newBuilder()
                        .setBodyBytes(body.toByteString())
                        .setAuthInfoBytes(_authInfoBuilder.build().toByteString())
                        .setChainId(CHAIN_ID)
                        .setAccountNumber(baseAccounts.get(_account).getAccountNumber())
                        .build();
                // Sign transaction
                byte[] signature = CryptoUtils.signTransaction(signDoc, _account.getPrivateKey());
                _txBuilder.addSignatures(ByteString.copyFrom(signature));
            }
            signedTx = _txBuilder
                    .setBody(body)
                    .setAuthInfo(_authInfoBuilder.build())
                    .build();
            ServiceOuterClass.BroadcastTxResponse bTxResponse = null;
            try {
                bTxResponse = client.txBroadcastTx(
                        ServiceOuterClass.BroadcastMode.BROADCAST_MODE_SYNC,
                        signedTx.toByteArray()).get();
                realGas.addAndGet(bTxResponse.getTxResponse().getGasUsed());
            } catch (Exception ex) {
                throw new IllegalArgumentException("!!! ERROR !!! Could not simulate Tx for gas : " + ex.getMessage());
            }
            try {
                if (bTxResponse != null) {
                    System.out.printf("   Transaction hash: %s%n", bTxResponse.getTxResponse().getTxhash());
                    System.out.printf("   Block height    : %s%n", bTxResponse.getTxResponse().getHeight());
                    System.out.printf("   Transaction log : %s%n", bTxResponse.getTxResponse().getRawLog());
                    System.out.printf("   Transaction fee : %s $FX%n", BigDecimal.valueOf(bTxResponse.getTxResponse().getGasUsed()).multiply(BigDecimal.valueOf(GAS_COST)).movePointLeft(18).stripTrailingZeros().toPlainString());
                }
            } catch (Exception ex) {
            }
        }
        return realGas.get();
    }

    private boolean addDelegatorAddresses(List<String> _fxDelegatorAddresses) throws Exception {
        boolean ret = true;
        for (String fxDelegatorAddress : _fxDelegatorAddresses) {
            FxAccount account = FxAccount.generateDelegator(fxDelegatorAddress, this.cryptoUnits);
            if (!account.getFxDelegatorAddress().equals(fxDelegatorAddress)) {
                throw new IllegalArgumentException("!!! ERROR !!! The FX delegator address does not match the guessed index.");
            }
            if (!FxAccount.exists(this.accounts, account.getFxDelegatorAddress())) {
                this.accounts.add(account);
                System.out.printf("NOTICE: Address '%s' (index %d) was added to the list of accounts.%n", fxDelegatorAddress, account.getCryptoUnit().lastIndex);
            } else {
                System.out.printf("WARNING: Address '%s' (index %d) already in the list of accounts.%n", fxDelegatorAddress, account.getCryptoUnit().lastIndex);
            }
        }
        return ret;
    }

}
