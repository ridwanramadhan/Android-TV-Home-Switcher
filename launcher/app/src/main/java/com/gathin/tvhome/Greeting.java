package com.gathin.tvhome;

import java.util.Locale;

/**
 * The day-part greeting rules, kept out of MainActivity so they can be unit tested.
 *
 * <p>Indonesian: Pagi 00–10, Siang 10–15, Sore 15–18, Malam 18–24. Spain (es-ES) and Catalan:
 * "Buenas tardes" into the evening, "Buenas noches" from 21. Latin American Spanish (es-MX, es-AR, …)
 * keeps the default hours.
 */
final class Greeting {
    private Greeting() {
    }

    static int resourceFor(Locale locale, int hour) {
        String lang = locale.getLanguage();
        String country = locale.getCountry();
        if ("in".equals(lang) || "id".equals(lang)) {
            if (hour < 10) return R.string.good_morning;
            if (hour < 15) return R.string.good_afternoon;
            if (hour < 18) return R.string.good_evening;
            return R.string.good_night;
        }
        if (("es".equals(lang) && "ES".equals(country)) || "ca".equals(lang)) {
            if (hour < 6) return R.string.good_night;
            if (hour < 12) return R.string.good_morning;
            if (hour < 21) return R.string.good_afternoon;
            return R.string.good_night;
        }
        if (hour < 5) return R.string.good_night;
        if (hour < 12) return R.string.good_morning;
        if (hour < 17) return R.string.good_afternoon;
        if (hour < 22) return R.string.good_evening;
        return R.string.good_night;
    }
}
