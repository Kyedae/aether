package dev.aether.modules.pest.helpers;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PestBallsackShredderTest {
    @Test
    void estimatesMultipleKillsFromTrackedEntities() {
        PestBallsackShredder.Result result =
                PestBallsackShredder.estimateResult(8, 8, 8);

        assertEquals(8, result.estimatedKilled());
        assertEquals(0, result.estimatedRemaining());
    }

    @Test
    void usesFreshTabDropWhenItDetectsMoreKills() {
        PestBallsackShredder.Result result =
                PestBallsackShredder.estimateResult(8, 3, 1);

        assertEquals(7, result.estimatedKilled());
        assertEquals(1, result.estimatedRemaining());
    }

    @Test
    void treatsMissingPestTabLineAsNoPestsRemaining() {
        PestBallsackShredder.Result result =
                PestBallsackShredder.estimateResult(5, 4, -1);

        assertEquals(5, result.estimatedKilled());
        assertEquals(0, result.estimatedRemaining());
    }
}
