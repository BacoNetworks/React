package flavor.pie.react;

import com.google.common.reflect.TypeToken;
import com.google.inject.Inject;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.hocon.HoconConfigurationLoader;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import ninja.leaping.configurate.objectmapping.serialize.TypeSerializers;
import org.slf4j.Logger;
import org.spongepowered.api.Game;
import org.spongepowered.api.GameState;
import org.spongepowered.api.command.CommandException;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.command.args.GenericArguments;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.config.DefaultConfig;
import org.spongepowered.api.data.DataRegistration;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.filter.Getter;
import org.spongepowered.api.event.filter.cause.First;
import org.spongepowered.api.event.filter.data.Has;
import org.spongepowered.api.event.game.GameReloadEvent;
import org.spongepowered.api.event.game.state.GameInitializationEvent;
import org.spongepowered.api.event.game.state.GamePreInitializationEvent;
import org.spongepowered.api.event.game.state.GameStartedServerEvent;
import org.spongepowered.api.event.message.MessageChannelEvent;
import org.spongepowered.api.event.network.ClientConnectionEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.plugin.PluginContainer;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.service.economy.EconomyService;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.serializer.TextSerializers;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Plugin(id="react", name="React", description="A little game to be played in chat.", authors="pie_flavor",
        version="1.3.2-SNAPSHOT")
public class React {
    @Inject
    Game game;
    @Inject
    PluginContainer container;
    @Inject @DefaultConfig(sharedRoot = true)
    Path path;
    @Inject @DefaultConfig(sharedRoot = true)
    ConfigurationLoader<CommentedConfigurationNode> loader;
    @Inject
    Logger logger;
    Config config;
    boolean inGame;
    boolean MathGame = false;
    String current;
    Task task;
    Random random;
    Instant started;

    @Listener
    public void preInit(GamePreInitializationEvent e) {
        TypeSerializers.getDefaultSerializers().registerType(TypeToken.of(BigDecimal.class), new BigDecimalSerializer());
        DataRegistration.builder()
                .dataClass(ReactData.class)
                .immutableClass(ReactData.Immutable.class)
                .builder(new ReactData.Builder())
                .manipulatorId("react_data")
                .dataName("ReactData")
                .buildAndRegister(container);
    }

    @Listener
    public void GameStarted(GameStartedServerEvent e) throws IOException, ObjectMappingException {
        random = new Random();
        loadConfig();
    }

    @Listener
    public void init(GameInitializationEvent e) {
        CommandSpec wins = CommandSpec.builder()
                .arguments(GenericArguments.playerOrSource(Text.of("player")))
                .executor(this::wins)
                .description(Text.of("Gets a player's win count"))
                .build();
        game.getCommandManager().register(this, wins, "wins");
    }

    private void loadConfig() throws IOException, ObjectMappingException {
        if (!Files.exists(path)) {
            game.getAssetManager().getAsset(this, "default.conf").get().copyToFile(path);
        }
        ConfigurationNode root = loader.load();
        updateConfig(root);
        config = root.getValue(Config.type);
        if (task != null) {
            task.cancel();
        }
        scheduleGame();
    }

    private void updateConfig(ConfigurationNode root) throws IOException, ObjectMappingException {
        int version = root.getNode("version").getInt();
        if (version < 3) {
            HoconConfigurationLoader assetLoader = HoconConfigurationLoader.builder()
                    .setURL(game.getAssetManager().getAsset(this, "default.conf").get().getUrl()).build();
            ConfigurationNode assetRoot = assetLoader.load();
            if (version < 2) {
                root.getNode("rewards").setValue(assetRoot.getNode("rewards").getValue());
                root.getNode("version").setValue(2);
            }
            root.getNode("min-players").setValue(assetRoot.getNode("min-players").getValue());
            root.getNode("version").setValue(3);
            loader.save(root);
        }
    }

    @Listener
    public void reload(GameReloadEvent e) throws IOException, ObjectMappingException {
        loadConfig();
    }

    private void newGame() {
        if (!game.getState().equals(GameState.SERVER_STARTED) || config.minPlayers > game.getServer().getOnlinePlayers().size()) {
            return;
        }
        inGame = true;
        Text fullText;
        if (!MathGame) {
            WordGenerator generator = new WordGenerator();
            current = generator.newWord(getRandomNumber(5, 20));
            fullText = TextSerializers.FORMATTING_CODE.deserialize(config.text.replace("%phrase%", current));
            MathGame = true;
        } else {
            int num1 = getRandomNumber(100, 1000);
            int num2 = getRandomNumber(1, 2000);
            int funmath = getRandomNumber(0, 4);
            int fun;
            String math;
            boolean switched = false;
            if (funmath < 2) {
                fun = num1 + num2;
                math = "+";
            } else if (funmath == 2) {
                if (num2 > num1) {
                    fun = num2 - num1;
                    switched = true;
                } else {
                    fun = num1 - num2;
                }
                math = "-";
            } else {
                num2 = getRandomNumber(2, 10);
                fun = num1 * num2;
                math = "*";
            }
            current = String.valueOf(fun);
            if (switched) {
                fullText = TextSerializers.FORMATTING_CODE.deserialize(config.textMath.replace("%num1%", String.valueOf(num2)).replace("%num2%", String.valueOf(num1)).replace("%math%", math));
            } else {
                fullText = TextSerializers.FORMATTING_CODE.deserialize(config.textMath.replace("%num1%", String.valueOf(num1)).replace("%num2%", String.valueOf(num2)).replace("%math%", math));
            }
            MathGame = false;
        }
        game.getServer().getBroadcastChannel().send(fullText);
        started = Instant.now();
        scheduleGame();
    }

    private void scheduleGame() {
        task = Task.builder()
                .execute(this::newGame)
                .delay(getDelay(), TimeUnit.SECONDS)
                .submit(this);
    }

    public int getRandomNumber(int min, int max) {
        return (int) ((Math.random() * (max - min)) + min);
    }

    private static final Pattern ASTERISK = Pattern.compile("^\\*");
    private static final Pattern WINNER = Pattern.compile("$winner", Pattern.LITERAL);

    @Listener
    public void chat(MessageChannelEvent.Chat e, @First Player p) {
        String chat = e.getRawMessage().toPlain().trim();
        if (inGame && chat.equalsIgnoreCase(current)) {
            game.getServer().getBroadcastChannel().send(
                    Text.of(p.getName()+" has won! Time: "+
                            (Instant.now().getEpochSecond() - started.getEpochSecond()) +" seconds!"));
            inGame = false;
            config.rewards.commands.forEach(s -> game.getCommandManager().process(
                            s.startsWith("*") ? game.getServer().getConsole() : p,
                            WINNER.matcher(ASTERISK.matcher(s).replaceAll(""))
                                    .replaceAll(Matcher.quoteReplacement(p.getName()))));
            game.getServiceManager().provide(EconomyService.class).ifPresent(svc -> {
                if (config.rewards.economy.amount.compareTo(BigDecimal.ZERO) > 0) {
                    svc.getOrCreateAccount(p.getUniqueId()).ifPresent(acc ->
                            acc.deposit(config.rewards.economy.getCurrency(), config.rewards.economy.amount,
                                    game.getCauseStackManager().getCurrentCause()));
                }
            });
            p.offer(ReactKeys.GAMES_WON, p.get(ReactKeys.GAMES_WON).get() + 1);
        }

    }

    private void disable() {
        game.getEventManager().unregisterPluginListeners(this);
        game.getCommandManager().getOwnedBy(this).forEach(game.getCommandManager()::removeMapping);
    }

    @Listener
    public void onJoin(ClientConnectionEvent.Join e,
                       @Getter("getTargetEntity") @Has(value = ReactData.class, inverse = true) Player p) {
        ReactData data = p.getOrCreate(ReactData.class).get();
        p.offer(data);
    }

    public CommandResult wins(CommandSource src, CommandContext args) throws CommandException {
        Player p = args.<Player>getOne("player").get();
        long wins = p.get(ReactKeys.GAMES_WON).get();
        src.sendMessage(Text.of("Player ", p.getName(), " has won ", wins, " times."));
        return CommandResult.builder()
                .successCount(1)
                .queryResult(wins > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) wins)
                .build();
    }

    private int getDelay() {
        if (config.maxDelay <= config.delay) {
            return config.delay;
        } else {
            return random.nextInt(config.maxDelay - config.delay + 1) + config.delay;
        }
    }
}
