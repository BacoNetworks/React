package flavor.pie.react;

import com.google.common.reflect.TypeToken;
import ninja.leaping.configurate.objectmapping.Setting;
import ninja.leaping.configurate.objectmapping.serialize.ConfigSerializable;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.service.economy.Currency;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.format.TextColors;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

@ConfigSerializable
public class Config {
    public final static TypeToken<Config> type = TypeToken.of(Config.class);

    @Setting public String text = "&4[&6The first person to type &4&l%phrase% &6wins 250 BacoBits!&4]";
    @Setting public String textMath = "&4[&6The first person to type how much &4&l%num1% &6%math% &4&l%num2% &6is &6wins 250 BacoBits!&4]";
    @Setting public int delay = 10;
    @Setting("max-delay") public int maxDelay = 0;
    @Setting("min-players") public int minPlayers = 0;
    @Setting public RewardsBlock rewards = new RewardsBlock();

    @ConfigSerializable
    public static class RewardsBlock {
        @Setting public EconomySection economy = new EconomySection();
        @Setting public List<String> commands = Collections.emptyList();
    }

    @ConfigSerializable
    public static class EconomySection {
        @Setting("currency") private String currencyString;
        private Currency currency;
        public Currency getCurrency() {
            if (currency == null) {
                currency = Sponge.getRegistry().getType(Currency.class, currencyString).get();
            }
            return currency;
        }
        @Setting public BigDecimal amount = BigDecimal.ZERO;
    }
}
