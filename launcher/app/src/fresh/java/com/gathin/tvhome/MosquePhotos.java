package com.gathin.tvhome;

import java.util.Collections;
import java.util.List;

/**
 * The fresh build ships no mosque gallery: the photographs live only in the widgets flavor, so resource
 * shrinking keeps them out of the fresh APK. See {@code src/widgets/java/.../MosquePhotos.java}.
 */
final class MosquePhotos {
    private MosquePhotos() {
    }

    static List<MosquePhoto> all() {
        return Collections.emptyList();
    }
}
