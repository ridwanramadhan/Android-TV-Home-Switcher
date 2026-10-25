package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

public class MosquePhotosTest {
    @Test
    public void bundledGalleryHasThirtyCompleteUniqueEntries() {
        List<MosquePhoto> photos = MosquePhotos.all();
        assertEquals(30, photos.size());
        Set<Integer> drawables = new HashSet<>();
        Set<String> sources = new HashSet<>();
        for (MosquePhoto photo : photos) {
            assertTrue(photo.drawableRes != 0);
            assertFalse(photo.mosque.trim().isEmpty());
            assertFalse(photo.location.trim().isEmpty());
            assertFalse(photo.creator.trim().isEmpty());
            assertFalse(photo.license.trim().isEmpty());
            assertTrue(photo.sourceUrl.startsWith("https://commons.wikimedia.org/wiki/File:"));
            assertTrue(drawables.add(photo.drawableRes));
            assertTrue(sources.add(photo.sourceUrl));
        }
    }
}
