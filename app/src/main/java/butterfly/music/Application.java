package butterfly.music;

import androidx.multidex.MultiDexApplication;

import com.tencent.mmkv.MMKV;

import butterfly.music.store.MusicStore;
import butterfly.music.util.NightModeUtil;

public class Application extends MultiDexApplication {
    @Override
    public void onCreate() {
        super.onCreate();

        MMKV.initialize(this);
        NightModeUtil.applyNightMode(this);
        MusicStore.init(this);
    }
}
