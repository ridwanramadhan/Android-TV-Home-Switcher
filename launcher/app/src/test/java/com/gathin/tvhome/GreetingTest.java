package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

import java.util.Locale;

public class GreetingTest {
    private static final Locale INDONESIAN = new Locale("in", "ID");
    private static final Locale SPAIN = new Locale("es", "ES");
    private static final Locale US = Locale.US;

    @Test public void indonesianDayParts() {
        assertEquals(R.string.good_morning, Greeting.resourceFor(INDONESIAN, 9));
        assertEquals(R.string.good_afternoon, Greeting.resourceFor(INDONESIAN, 10));
        assertEquals(R.string.good_evening, Greeting.resourceFor(INDONESIAN, 15));
        assertEquals(R.string.good_night, Greeting.resourceFor(INDONESIAN, 18));
    }

    @Test public void spainKeepsAfternoonUntilNine() {
        assertEquals(R.string.good_morning, Greeting.resourceFor(SPAIN, 6));
        assertEquals(R.string.good_afternoon, Greeting.resourceFor(SPAIN, 20));
        assertEquals(R.string.good_night, Greeting.resourceFor(SPAIN, 21));
    }

    @Test public void defaultDayParts() {
        assertEquals(R.string.good_night, Greeting.resourceFor(US, 4));
        assertEquals(R.string.good_morning, Greeting.resourceFor(US, 5));
        assertEquals(R.string.good_afternoon, Greeting.resourceFor(US, 12));
        assertEquals(R.string.good_evening, Greeting.resourceFor(US, 17));
        assertEquals(R.string.good_night, Greeting.resourceFor(US, 22));
    }
}
