package com.drakonix.oneblockshop;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

// Structural validation of pricing/seed_prices.json only - no Minecraft classes involved, on
// purpose. Plain JUnit (this project's `test` task) can't touch net.minecraft.world.level.block
// classes: NeoForge patches BlockBehaviour.Properties to consult FML's LoadingModList, which is
// only populated when the game is actually launched through ModLauncher (runClient/runServer/
// runGameTestServer) - Bootstrap.bootStrap() throws NPE outside that environment. So this test
// can't confirm "minecraft:dirt" resolves to a real Item; it catches the failure modes a
// hand-edited JSON file actually has - malformed JSON, bad ID shape, non-positive prices,
// duplicate keys - which is still real coverage for a file with no other validation today.
class PricingSeedTest
{
    private static final Pattern ITEM_ID = Pattern.compile("^[a-z0-9_.-]+:[a-z0-9_./-]+$");

    private JsonObject loadSeedPricesJson() throws IOException
    {
        try (InputStream stream = getClass().getResourceAsStream("/pricing/seed_prices.json"))
        {
            assertTrue(stream != null, "pricing/seed_prices.json must be on the test classpath");
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test
    void fileParsesAndIsNonEmpty() throws IOException
    {
        JsonObject json = loadSeedPricesJson();
        assertFalse(json.entrySet().isEmpty(), "seed_prices.json should have at least one entry");
    }

    @Test
    void everyKeyLooksLikeAnItemId() throws IOException
    {
        for (Map.Entry<String, JsonElement> entry : loadSeedPricesJson().entrySet())
            assertTrue(ITEM_ID.matcher(entry.getKey()).matches(),
                    "'" + entry.getKey() + "' doesn't look like a namespace:path item ID");
    }

    @Test
    void everyPriceIsAPositiveWholeNumber() throws IOException
    {
        for (Map.Entry<String, JsonElement> entry : loadSeedPricesJson().entrySet())
        {
            JsonElement value = entry.getValue();
            assertTrue(value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber(),
                    "'" + entry.getKey() + "' price isn't a number");
            long price = value.getAsLong();
            assertTrue(price > 0, "'" + entry.getKey() + "' has a non-positive price: " + price);
        }
    }
}
