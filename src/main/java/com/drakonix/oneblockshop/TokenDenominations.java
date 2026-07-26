package com.drakonix.oneblockshop;

// Pure, no Minecraft/NeoForge classes - see WalletDenominationTest. Kept separate from Wallet.java
// itself because merely loading that class triggers NeoForge registry initialization (its
// DeferredRegister/AttachmentType static fields), which throws outside a real ModLauncher launch -
// same limitation PricingSeedTest documents for why it can't touch most of this mod's other classes.
final class TokenDenominations
{
    // Descending so greedy decomposition always picks the biggest token that fits first -
    // correct and optimal for a canonical system like powers of two (unlike arbitrary
    // denominations, greedy here can never be non-optimal).
    static final long[] DESCENDING = {8192L, 4096L, 2048L, 1024L, 512L, 256L, 128L, 64L, 32L, 16L, 8L, 4L, 2L, 1L};

    private TokenDenominations() {}

    // counts[i] is how many DESCENDING[i] tokens to mint for the given amount. Greedy is optimal
    // here specifically because DESCENDING is a canonical system (powers of two) - every amount
    // decomposes to exactly its binary representation, one bit per denomination, which is what
    // "solves" change-making for free.
    static long[] decompose(long amount)
    {
        long[] counts = new long[DESCENDING.length];
        for (int i = 0; i < DESCENDING.length; i++)
        {
            counts[i] = amount / DESCENDING[i];
            amount %= DESCENDING[i];
        }
        return counts;
    }
}
