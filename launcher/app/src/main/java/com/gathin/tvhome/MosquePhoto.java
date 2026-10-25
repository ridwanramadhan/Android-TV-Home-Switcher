package com.gathin.tvhome;

final class MosquePhoto {
    final int drawableRes;
    final String mosque, location, creator, license, sourceUrl;

    MosquePhoto(int drawableRes, String mosque, String location, String creator,
                String license, String sourceUrl) {
        this.drawableRes = drawableRes;
        this.mosque = mosque;
        this.location = location;
        this.creator = creator;
        this.license = license;
        this.sourceUrl = sourceUrl;
    }
}
