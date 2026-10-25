package com.gathin.tvhome;

import android.content.Context;
import android.util.AttributeSet;
import android.view.TextureView;

/**
 * A TextureView that scales to fill its container completely without letterboxing,
 * integrating seamlessly into the Android TV View hierarchy.
 */
public class FullScreenTextureView extends TextureView {
    public FullScreenTextureView(Context context) {
        super(context);
    }

    public FullScreenTextureView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public FullScreenTextureView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = getDefaultSize(0, widthMeasureSpec);
        int height = getDefaultSize(0, heightMeasureSpec);
        setMeasuredDimension(width, height);
    }
}
