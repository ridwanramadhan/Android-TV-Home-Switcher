package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;

final class MosqueRotation {
    private MosqueRotation() {}

    static <T> List<T> shuffled(List<T> source, T previous, Random random) {
        List<T> deck = new ArrayList<>(source);
        Collections.shuffle(deck, random);
        if (deck.size() > 1 && Objects.equals(previous, deck.get(0))) {
            Collections.swap(deck, 0, 1);
        }
        return deck;
    }
}
