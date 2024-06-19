package butterfly.music.activity.browser.album;

import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import java.util.List;

import butterfly.music.R;
import butterfly.music.activity.ListActivity;
import butterfly.music.activity.browser.artist.ArtistBrowserActivity;
import butterfly.music.activity.detail.album.AlbumDetailActivity;
import butterfly.music.activity.localmusic.LocalMusicActivity;
import butterfly.music.service.AppPlayerService;
import butterfly.music.util.PlayerUtil;
import butterfly.player.lifecycle.PlayerViewModel;

public class AlbumBrowserActivity extends ListActivity {
    private RecyclerView rvAlbumBrowser;
    private AlbumBrowserViewModel mViewModel;
    private PlayerViewModel mPlayerViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_album_browser);
        ViewModelProvider provider = new ViewModelProvider(this);
        // 获取
        mViewModel = provider.get(AlbumBrowserViewModel.class);
        mPlayerViewModel = provider.get(PlayerViewModel.class);
        // 初始化
        PlayerUtil.initPlayerViewModel(this, mPlayerViewModel, AppPlayerService.class);
        rvAlbumBrowser = findViewById(R.id.rvAlbumBrowser);
        Button btnSongs = findViewById(R.id.btnAlbums);
        btnSongs.setSelected(true);
        initRecyclerView();
    }

    private void initRecyclerView() {
        rvAlbumBrowser.setLayoutManager(new LinearLayoutManager(this)); // 设置布局管理器
        List<String> allAlbum = mViewModel.getAllAlbum().getValue(); // 获取所有专辑
        assert allAlbum != null;
        AlbumBrowserAdapter adapter = new AlbumBrowserAdapter(allAlbum); // 创建适配器
        rvAlbumBrowser.setAdapter(adapter); // 设置适配器
        // 观察所有专辑的变化，如果有变化则更新适配器
        mViewModel.getAllAlbum().observe(this, adapter::setAllAlbum);
        // 观察正在播放的音乐项，如果有变化则更新标记位置
        mPlayerViewModel.getPlayingMusicItem().observe(this, musicItem -> {
            if (musicItem == null) {
                adapter.clearMark();
                return;
            }
            List<String> albumList = mViewModel.getAllAlbum().getValue();
            adapter.setMarkPosition(albumList.indexOf(musicItem.getAlbum()));
        });
        // 设置点击事件，点击后导航到专辑详情页面
        adapter.setOnItemClickListener((position, viewId, view, holder) ->
                navigateToAlbumDetail(mViewModel.getAlbum(position))
        );
    }

    public void finishSelf(View view) {
        finish();
    }
    public void onNavigate(View view) {
        Class<?> activityClass;
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
        Intent intent = new Intent(this, activityClass);
        startActivity(intent);
        finish();  // 关闭当前界面
    }

    public void navigateToAlbumDetail(String albumName) {
        AlbumDetailActivity.start(this, albumName);
    }
}