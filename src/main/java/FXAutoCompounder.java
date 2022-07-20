import commands.AutoCompounder;
import commands.Base64Encryptor;
import picocli.CommandLine;

@CommandLine.Command(name = "FxAutoCompounder", aliases = { "frenchxcore" },
        subcommands = { AutoCompounder.class, Base64Encryptor.class, CommandLine.HelpCommand.class },
        description = "FrenchXCore $FX Auto-Compounder\nTo restake automatically your validator and/or delegator rewards and commissions with FrenchXCore validator.\n!! USE AT YOUR OWN RISK !!\n",
        header = "(C) 2022 FrenchXCore - https://twitter.com/FrenchXCore1"
)
public class FXAutoCompounder implements Runnable {

    private @CommandLine.Spec CommandLine.Model.CommandSpec spec;

    @Override
    public void run() {
        throw new CommandLine.ParameterException(spec.commandLine(), "Specify a subcommand");
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new FXAutoCompounder()).execute(args);
        System.exit(exitCode);
    }

}
