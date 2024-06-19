package butterfly.music.activity.localmusic;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import butterfly.music.R;
import butterfly.music.activity.ListActivity;
import butterfly.music.activity.browser.album.AlbumBrowserActivity;
import butterfly.music.activity.browser.artist.ArtistBrowserActivity;
import butterfly.music.activity.search.SearchActivity;
import butterfly.music.dialog.MessageDialog;
import butterfly.music.dialog.ScannerDialog;
import butterfly.music.fragment.musiclist.MusicListFragment;
import butterfly.music.service.AppPlayerService;
import butterfly.music.store.MusicStore;
import butterfly.music.util.PlayerUtil;
import butterfly.player.lifecycle.PlayerViewModel;

public class LocalMusicActivity extends ListActivity {
    private MusicListFragment mMusicListFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_local_music); // 设置布局
        initPlayerClient(); // 初始化播放器客户端
        // 获取当前的Fragment
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.musicListContainer);
        if (fragment instanceof MusicListFragment) {
            mMusicListFragment = (MusicListFragment) fragment; // 如果是MusicListFragment类型，直接赋值
        } else {
            // 否则，创建一个新的MusicListFragment并添加到FragmentManager
            mMusicListFragment = MusicListFragment.newInstance(MusicStore.MUSIC_LIST_LOCAL_MUSIC);
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.musicListContainer, mMusicListFragment, "MusicList")
                    .commit();
        }
        // 如果没有权限，本地音乐为空，并且应该显示请求权限的理由，那么扫描音乐
        if (noPermission() && localMusicIsEmpty() && shouldShowRequestPermissionRationale()) {
            scanMusic();
        }
        // 设置“歌曲”按钮为选中状态
        Button btnSongs = findViewById(R.id.btnFavorite);
        btnSongs.setSelected(true);
    }

    public void onNavigate(View view) {
        Class<?> activityClass;
        // 根据点击的按钮，选择要启动的Activity
        switch (view.getId()) {
            case R.id.btnFavorite:
                activityClass = LocalMusicActivity.class;
                break;
            case R.id.btnAlbums:
                activityClass = AlbumBrowserActivity.class;
                break;
            case R.id.btnArtists:
                activityClass = ArtistBrowserActivity.class;
                break;
            default:
                return;
        }
        // 创建一个新的Intent，启动对应的Activity
        Intent intent = new Intent(this, activityClass);
        startActivity(intent);
        finish();  // 关闭当前界面
    }

    private void initPlayerClient() {
        ViewModelProvider viewModelProvider = new ViewModelProvider(this);
        PlayerViewModel playerViewModel = viewModelProvider.get(PlayerViewModel.class);
        PlayerUtil.initPlayerViewModel(this, playerViewModel, AppPlayerService.class);
        setPlayerClient(playerViewModel.getPlayerClient());
    }

    public void finishSelf(View view) {
        finish();
    }

    public void onOptionMenuClicked(View view) {
        int id = view.getId();
        if (id == R.id.btnSearch) {
            SearchActivity.start(this, SearchActivity.Type.MUSIC_LIST, MusicStore.MUSIC_LIST_LOCAL_MUSIC);
        } else if (id == R.id.btnSort) {
            mMusicListFragment.showSortDialog();
        } else if (id == R.id.btnScan) {
            scanMusic();
        }
    }

    private boolean noPermission() {
        int result;
        if (Build.VERSION.SDK_INT >= 33) {
            result = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO);
        } else {
            result = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        return result == PackageManager.PERMISSION_DENIED;
    }

    private boolean localMusicIsEmpty() {
        return MusicStore.getInstance().getLocalMusicList().getSize() < 1;
    }

    private boolean shouldShowRequestPermissionRationale() {
        if (Build.VERSION.SDK_INT >= 33) {
            return ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_MEDIA_AUDIO);
        }
        return ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_EXTERNAL_STORAGE);
    }

    private void scanMusic() {
        MessageDialog messageDialog = new MessageDialog.Builder(this)
                .setMessage(R.string.message_scan_local_music)
                .setPositiveButtonClickListener((dialog, which) -> {
                    ScannerDialog scannerDialog = ScannerDialog.newInstance(localMusicIsEmpty());
                    scannerDialog.show(getSupportFragmentManager(), "scanMusic");
                })
                .build();

        messageDialog.show(getSupportFragmentManager(), "messageScanMusic");
    }
}