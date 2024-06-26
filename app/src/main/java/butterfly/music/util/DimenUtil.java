package butterfly.music.util;

import android.content.res.Resources;

import androidx.annotation.DimenRes;
import androidx.annotation.NonNull;

import com.google.common.base.Preconditions;

/**
 * 尺寸工具。
 */
public final class DimenUtil {
    private DimenUtil() {
        throw new AssertionError();
    }
    public static int getDimenPx(@NonNull Resources resources, @DimenRes int dimenRes) {
        Preconditions.checkNotNull(resources);
        return resources.getDimensionPixelSize(dimenRes);
    }
}
