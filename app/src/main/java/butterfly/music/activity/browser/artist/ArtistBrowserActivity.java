package butterfly.music.activity.browser.artist;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import butterfly.music.R;
import butterfly.music.activity.ListActivity;
import butterfly.music.activity.browser.album.AlbumBrowserActivity;
import butterfly.music.activity.detail.artist.ArtistDetailActivity;
import butterfly.music.activity.localmusic.LocalMusicActivity;
import butterfly.music.service.AppPlayerService;
import butterfly.music.util.PlayerUtil;
import butterfly.player.lifecycle.PlayerViewModel;

public class ArtistBrowserActivity extends ListActivity {
    private RecyclerView rvArtistBrowser;
    private ArtistBrowserViewModel mViewModel;
    private PlayerViewModel mPlayerViewModel;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_artist_browser);
        ViewModelProvider provider = new ViewModelProvider(this);
        // 获取ViewModel实例
        mViewModel = provider.get(ArtistBrowserViewModel.class);
        mPlayerViewModel = provider.get(PlayerViewModel.class);
        // 初始化播放器ViewModel
        PlayerUtil.initPlayerViewModel(this, mPlayerViewModel, AppPlayerService.class);
        Button btnSongs = findViewById(R.id.btnArtists);
        btnSongs.setSelected(true);
        rvArtistBrowser = findViewById(R.id.rvArtistBrowser);
        // 初始化RecyclerView
        initRecyclerView();
    }

    private void initRecyclerView() {
        rvArtistBrowser.setLayoutManager(new LinearLayoutManager(this));
        List<String> allArtist = mViewModel.getAllArtist().getValue();
        assert allArtist != null;
        ArtistBrowserAdapter adapter = new ArtistBrowserAdapter(allArtist);
        rvArtistBrowser.setAdapter(adapter);
        // 观察所有艺术家的变化，如果有变化则更新适配器
        mViewModel.getAllArtist().observe(this, adapter::setAllArtist);
        // 观察正在播放的音乐项，如果有变化则更新标记位置
        mPlayerViewModel.getPlayingMusicItem().observe(this, musicItem -> {
            if (musicItem == null) {
                adapter.clearMark();
                return;
            }
            List<String> artistList = mViewModel.getAllArtist().getValue();
            adapter.setMarkPosition(artistList.indexOf(musicItem.getArtist()));
        });
        // 设置点击事件，点击后导航到艺术家详情页面
        adapter.setOnItemClickListener((position, viewId, view, holder) ->
                navigateToArtistDetail(mViewModel.getArtist(position))
        );
    }

    public void onNavigate(View view) {
        Class<?> activityClass;
        // 根据点击的按钮ID，决定跳转到哪个Activity
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
        // 创建Intent并启动对应的Activity
        Intent intent = new Intent(this, activityClass);
        startActivity(intent);
        finish();  // 关闭当前界面
    }
    public void finishSelf(View view) {
        finish();
    }

    public void navigateToArtistDetail(String artistName) {
        ArtistDetailActivity.start(this, artistName);
    }
}
