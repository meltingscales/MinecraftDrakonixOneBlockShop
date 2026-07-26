package com.drakonix.oneblockshop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

// TokenDenominations.decompose is pure (no Player/ItemStack, no NeoForge registries) specifically
// so it's testable without a real launch - see PricingSeedTest for why plain JUnit can't touch
// most of this mod's other classes (Wallet included - its DeferredRegister/AttachmentType static
// fields throw outside ModLauncher, which is exactly why this logic lives in a separate class).
class WalletDenominationTest
{
    @ParameterizedTest
    @ValueSource(longs = {0L, 1L, 2L, 3L, 37L, 200L, 8192L, 8193L, 16383L, 1_000_000L})
    void decomposeReconstructsTheOriginalAmount(long amount)
    {
        long[] counts = TokenDenominations.decompose(amount);
        long total = 0L;
        for (int i = 0; i < TokenDenominations.DESCENDING.length; i++)
            total += counts[i] * TokenDenominations.DESCENDING[i];
        assertEquals(amount, total);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, 1L, 37L, 200L, 8193L, 1_000_000L})
    void decomposeUsesEveryDenominationAtMostOnceExceptTheLargest(long amount)
    {
        long[] counts = TokenDenominations.decompose(amount);
        // Every denomination below the largest is a single binary digit of the remainder after
        // the largest is divided out - at most 1, never more. Only the largest (8192) can repeat,
        // since there's no bigger denomination left to absorb a large amount.
        for (int i = 1; i < TokenDenominations.DESCENDING.length; i++)
            assertTrue(counts[i] <= 1, "denomination " + TokenDenominations.DESCENDING[i] + " used " + counts[i] + " times for amount " + amount);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, 1L, 37L, 200L, 8192L, 8193L, 1_000_000L})
    void decomposeIsMinimalTokenCount(long amount)
    {
        long[] counts = TokenDenominations.decompose(amount);
        long tokenCount = 0L;
        for (long count : counts)
            tokenCount += count;
        // Closed-form optimum for this canonical system: as many 8192s as fit, plus one token
        // per set bit of whatever's left (every remaining denomination is a distinct power of
        // two, so the remainder's binary popcount is exactly the fewest tokens that can express
        // it). Anything less than this would mean some amount isn't representable at all.
        long optimal = amount / 8192L + Long.bitCount(amount % 8192L);
        assertEquals(optimal, tokenCount);
    }
}
