package butterfly.music.activity.navigation;



import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.ViewModelProvider;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.google.common.base.Preconditions;

import java.util.Collections;

import butterfly.api.SearchOnlineActivity;
import butterfly.music.GlideApp;
import butterfly.music.R;
import butterfly.music.activity.BaseActivity;
import butterfly.music.activity.history.HistoryViewModel;
import butterfly.music.databinding.ActivityNavigationBinding;
import butterfly.music.dialog.PlaylistDialog;
import butterfly.music.dialog.ScannerDialog;
import butterfly.music.service.AppPlayerService;
import butterfly.music.store.HistoryEntity;
import butterfly.music.store.Music;
import butterfly.music.util.DimenUtil;
import butterfly.music.util.MusicListUtil;
import butterfly.music.util.PlayerUtil;
import butterfly.player.lifecycle.PlayerViewModel;
import butterfly.player.playlist.Playlist;

public class NavigationActivity extends BaseActivity {
    private static final String KEY_SCAN_LOCAL_MUSIC = "scan_local_music";
    private NavigationViewModel mNavigationViewModel;
    private HistoryViewModel mHistoryViewModel;
    private int mIconCornerRadius;
    private ActivityNavigationBinding mBinding;
    private PlayerViewModel mPlayerViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = DataBindingUtil.setContentView(this, R.layout.activity_navigation);
        initAllViewModel();
        // 设置播放器客户端
        setPlayerClient(mPlayerViewModel.getPlayerClient());
        mBinding.setNavViewModel(mNavigationViewModel);
        mBinding.setLifecycleOwner(this);
        // 观察
        observerPlayingMusicItem();
        observerHistoryMusic();
        observeFavoriteMusic();
        // 扫描本地音乐
        if (shouldScanLocalMusic()) {
            scanLocalMusic();
        }
        mIconCornerRadius = DimenUtil.getDimenPx(getResources(), R.dimen.album_icon_corner_radius);
        // 获取热歌按钮，并设置点击事件
        Button btnStartSearch = findViewById(R.id.btnStartSearch);
        btnStartSearch.setOnClickListener(v -> startSearchActivity());
    }

    private void initAllViewModel() {
        // 创建ViewModelProvider
        ViewModelProvider viewModelProvider = new ViewModelProvider(this);
        // 获取NavigationViewModel
        mNavigationViewModel = viewModelProvider.get(NavigationViewModel.class);
        mPlayerViewModel = viewModelProvider.get(PlayerViewModel.class);
        PlayerUtil.initPlayerViewModel(this, mPlayerViewModel, AppPlayerService.class);
        initNavigationViewModel();
        mHistoryViewModel = viewModelProvider.get(HistoryViewModel.class);
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

    //最爱的音乐
    private void observeFavoriteMusic() {
        mNavigationViewModel.getFavoriteMusic().observe(this, favorites -> {
            if (favorites != null && !favorites.isEmpty()) {
                for (int i = 0; i < favorites.size() && i < 3; i++) {
                    loadFavoriteMusicIcon(favorites.get(i), i);
                }
            }
        });
    }

    private void loadFavoriteMusicIcon(Music favorite, int index) {
        ImageView imageView;
        TextView titleView;
        TextView artistAlbumView;

        switch (index) {
            case 0:
                imageView = findViewById(R.id.favoriteImageView1);
                titleView = findViewById(R.id.favoriteTitle1);
                artistAlbumView = findViewById(R.id.favoriteArtistAlbum1);
                break;
            case 1:
                imageView = findViewById(R.id.favoriteImageView2);
                titleView = findViewById(R.id.favoriteTitle2);
                artistAlbumView = findViewById(R.id.favoriteArtistAlbum2);
                break;
            case 2:
                imageView = findViewById(R.id.favoriteImageView3);
                titleView = findViewById(R.id.favoriteTitle3);
                artistAlbumView = findViewById(R.id.favoriteArtistAlbum3);
                break;
            default:
                return;
        }

        String iconUri = favorite.getUri();
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

        titleView.setText(favorite.getTitle());
        artistAlbumView.setText(favorite.getArtist() + " - " + favorite.getAlbum());

        imageView.setOnClickListener(v -> playFavoriteMusic(favorite));
        titleView.setOnClickListener(v -> playFavoriteMusic(favorite));
        artistAlbumView.setOnClickListener(v -> playFavoriteMusic(favorite));
    }


    private void playFavoriteMusic(Music favorite) {
        Playlist playlist = MusicListUtil.asPlaylist("Favorite", Collections.singletonList(favorite), 0);
        mPlayerViewModel.setPlaylist(playlist, 0, true);
    }


    //网络调用
    private void startSearchActivity() {
        Intent intent = new Intent(this, SearchOnlineActivity.class);
        startActivity(intent);
    }
}