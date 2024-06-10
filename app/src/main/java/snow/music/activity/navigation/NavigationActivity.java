package snow.music.activity.navigation;

import static java.lang.Math.log;

import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.ViewModelProvider;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.google.common.base.Preconditions;

import java.util.Collections;

import snow.music.GlideApp;
import snow.music.R;
import snow.music.activity.BaseActivity;
import snow.music.activity.history.HistoryViewModel;
import snow.music.databinding.ActivityNavigationBinding;
import snow.music.dialog.PlaylistDialog;
import snow.music.dialog.ScannerDialog;
import snow.music.service.AppPlayerService;
import snow.music.store.HistoryEntity;
import snow.music.util.DimenUtil;
import snow.music.util.MusicListUtil;
import snow.music.util.PlayerUtil;
import snow.player.lifecycle.PlayerViewModel;
import snow.player.playlist.Playlist;

public class NavigationActivity extends BaseActivity {
    private static final String KEY_SCAN_LOCAL_MUSIC = "scan_local_music";

    private ActivityNavigationBinding mBinding;
    private PlayerViewModel mPlayerViewModel;
    private NavigationViewModel mNavigationViewModel;
    private HistoryViewModel mHistoryViewModel;
    private int mIconCornerRadius;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mBinding = DataBindingUtil.setContentView(this, R.layout.activity_navigation);

        initAllViewModel();
        setPlayerClient(mPlayerViewModel.getPlayerClient());

        mBinding.setNavViewModel(mNavigationViewModel);
        mBinding.setLifecycleOwner(this);

        observerPlayingMusicItem();
        observerHistoryMusic(); // 观察历史数据

        if (shouldScanLocalMusic()) {
            scanLocalMusic();
        }

        mIconCornerRadius = DimenUtil.getDimenPx(getResources(), R.dimen.album_icon_corner_radius);
    }

    private void initAllViewModel() {
        ViewModelProvider viewModelProvider = new ViewModelProvider(this);

        mPlayerViewModel = viewModelProvider.get(PlayerViewModel.class);
        mNavigationViewModel = viewModelProvider.get(NavigationViewModel.class);
        mHistoryViewModel = viewModelProvider.get(HistoryViewModel.class);

        PlayerUtil.initPlayerViewModel(this, mPlayerViewModel, AppPlayerService.class);
        initNavigationViewModel();
    }

    private void observerHistoryMusic() {
        mHistoryViewModel.getHistory().observe(this, history -> {
            if (history != null && !history.isEmpty()) {
                for (int i = 0; i < history.size() && i < 3; i++) {
                    loadHistoryMusicIcon(history.get(i), i);
                }
            }
        });
    }
    private void loadHistoryMusicIcon(HistoryEntity historyEntity, int index) {
        ImageView imageView;
        switch (index) {
            case 0:
                imageView = findViewById(R.id.imageView2);
                break;
            case 1:
                imageView = findViewById(R.id.imageView4);
                break;
            case 2:
                imageView = findViewById(R.id.imageView5);
                break;
            default:
                return;
        }

        String iconUri = historyEntity.getMusic().getUri();
        Log.d("NavigationActivity", "Loading Historymusic icon from URI: " + historyEntity.getMusic());
        if (iconUri != null) {
            GlideApp.with(this)
                    .load(iconUri)
                    .placeholder(R.mipmap.ic_album_default_icon_big)
                    .transform(new CenterCrop(), new RoundedCorners(mIconCornerRadius))
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .into(imageView);
        } else {
            imageView.setImageResource(R.mipmap.ic_album_default_icon_big);
        }

        imageView.setOnClickListener(v -> playMusic(historyEntity));
    }

    private void playMusic(HistoryEntity historyEntity) {
        Playlist playlist = MusicListUtil.asPlaylist(""/*empty*/, Collections.singletonList(historyEntity.getMusic()), 0);
        mPlayerViewModel.setPlaylist(playlist, 0, true);
    }

    private void initNavigationViewModel() {
        if (mNavigationViewModel.isInitialized()) {
            return;
        }

        mNavigationViewModel.init(mPlayerViewModel);
    }

    private void observerPlayingMusicItem() {
        mPlayerViewModel.getPlayingMusicItem()
                .observe(this, musicItem -> {
                    if (musicItem == null) {
                        mBinding.ivDisk.setImageResource(R.mipmap.ic_album_default_icon_big);
                        return;
                    }

                    loadMusicIcon(musicItem.getUri());
                });
    }

    private void loadMusicIcon(String musicUri) {
        Log.d("NavigationActivity", "Loading music icon from URI: " + musicUri);
        GlideApp.with(this)
                .load(musicUri)
                .placeholder(R.mipmap.ic_album_default_icon_big)
                .transform(new CenterCrop(), new RoundedCorners(mIconCornerRadius))
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(mBinding.ivDisk);
    }

    private boolean shouldScanLocalMusic() {
        SharedPreferences preferences = getPreferences(MODE_PRIVATE);
        return preferences.getBoolean(KEY_SCAN_LOCAL_MUSIC, true);
    }

    private void scanLocalMusic() {
        getPreferences(MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_SCAN_LOCAL_MUSIC, false)
                .apply();

        ScannerDialog scannerDialog = ScannerDialog.newInstance(true, true);
        scannerDialog.show(getSupportFragmentManager(), "scannerDialog");
    }

    public void showPlaylist(View view) {
        Preconditions.checkNotNull(view);

        PlaylistDialog.newInstance()
                .show(getSupportFragmentManager(), "Playlist");
    }
}