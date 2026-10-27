package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.view.KeyEvent;

import java.util.Collections;
import java.util.Map;

/**
 * The app-key maps and volume handling shared by {@link HomeKeyService} and {@link InputKeyService}.
 * Each service keeps its own settings/input key rules; only the duplicated wiring lives here.
 */
final class KeyBindings {
    private static final KeyBindings EMPTY = new KeyBindings(
            Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), false);

    private final Map<Integer, String> appKeys;
    private final Map<Integer, String> appScancodes;
    private final Map<Integer, String> holdAppKeys;
    private final Map<Integer, String> holdAppScancodes;
    private final boolean consumeVolumeKeys;

    private KeyBindings(Map<Integer, String> appKeys, Map<Integer, String> appScancodes,
                        Map<Integer, String> holdAppKeys, Map<Integer, String> holdAppScancodes,
                        boolean consumeVolumeKeys) {
        this.appKeys = appKeys;
        this.appScancodes = appScancodes;
        this.holdAppKeys = holdAppKeys;
        this.holdAppScancodes = holdAppScancodes;
        this.consumeVolumeKeys = consumeVolumeKeys;
    }

    static KeyBindings empty() {
        return EMPTY;
    }

    /** Reads app_keys, consume_volume_keys and key_filter from the same config both services already load. */
    static KeyBindings from(Config config) {
        return new KeyBindings(
                RemoteKeys.parseAppKeys(config.appKeys),
                RemoteKeys.parseAppScancodes(config.appKeys),
                RemoteKeys.parseHoldAppKeys(config.appKeys),
                RemoteKeys.parseHoldAppScancodes(config.appKeys),
                config.consumeVolumeKeys);
    }

    boolean consumesVolumeKeys() {
        return consumeVolumeKeys;
    }

    boolean handleVolume(AccessibilityService service, KeyEvent event) {
        return consumeVolumeKeys && VolumeKeys.handle(service, event);
    }

    /** A scancode entry is more specific than any keycode, so it goes first. */
    boolean handleScancode(AccessibilityService service, KeyEvent event) {
        return RemoteKeys.handle(service, event, appScancodes.get(event.getScanCode()),
                holdAppScancodes.get(event.getScanCode()));
    }

    boolean handleKeycode(AccessibilityService service, KeyEvent event) {
        return RemoteKeys.handle(service, event, appKeys.get(event.getKeyCode()),
                holdAppKeys.get(event.getKeyCode()));
    }
}
