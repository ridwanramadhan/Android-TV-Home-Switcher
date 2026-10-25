package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import org.junit.Test;

public class MosqueRotationTest {
    @Test
    public void shufflePlaysEveryPhotoOnce() {
        List<String> source = Arrays.asList("a", "b", "c", "d");
        List<String> deck = MosqueRotation.shuffled(source, null, new Random(4));
        assertEquals(4, deck.size());
        assertEquals(new HashSet<>(source), new HashSet<>(deck));
    }

    @Test
    public void reshuffleDoesNotImmediatelyRepeatPrevious() {
        List<String> source = Arrays.asList("a", "b", "c");
        for (int seed = 0; seed < 50; seed++) {
            assertNotEquals("b", MosqueRotation.shuffled(source, "b", new Random(seed)).get(0));
        }
    }
}
