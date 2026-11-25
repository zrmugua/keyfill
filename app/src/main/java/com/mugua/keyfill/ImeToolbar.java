package com.mugua.keyfill;

import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

interface ImeToolbar {
    LinearLayout bar();

    int cardHeight();

    int availableWidth(boolean hasPinned, int pinnedGapPx);

    Drawable cardBackground(boolean pinned, boolean dark);

    View attach(ViewGroup candidate);
}
