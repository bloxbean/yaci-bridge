package com.bloxbean.cardano.yaci.bridge;

import com.bloxbean.cardano.yaci.core.common.Constants;
import com.bloxbean.cardano.yaci.core.protocol.chainsync.messages.Tip;
import com.bloxbean.cardano.yaci.helper.TipFinder;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Quick JVM test to verify TipFinder works outside native image.
 */
class TipFinderTest {

    @Test
    void testTipFinderMainnet() {
        TipFinder tipFinder = new TipFinder("backbone.cardano.iog.io", 3001,
                Constants.WELL_KNOWN_MAINNET_POINT, Constants.MAINNET_PROTOCOL_MAGIC);
        try {
            Tip tip = tipFinder.find().block(Duration.ofSeconds(30));
            assertNotNull(tip);
            System.out.println("Tip: slot=" + tip.getPoint().getSlot() +
                    ", hash=" + tip.getPoint().getHash().substring(0, 16) + "..." +
                    ", block=" + tip.getBlock());
            assertTrue(tip.getPoint().getSlot() > 0);
            assertTrue(tip.getBlock() > 0);
        } finally {
            tipFinder.shutdown();
        }
    }
}
