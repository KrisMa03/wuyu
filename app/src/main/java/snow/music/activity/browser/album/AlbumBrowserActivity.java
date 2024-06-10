package snow.music.activity.browser.album;

import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import java.util.List;

import snow.music.R;
import snow.music.activity.ListActivity;
import snow.music.activity.browser.artist.ArtistBrowserActivity;
import snow.music.activity.detail.album.AlbumDetailActivity;
import snow.music.activity.localmusic.LocalMusicActivity;
import snow.music.service.AppPlayerService;
import snow.music.util.PlayerUtil;
import snow.player.lifecycle.PlayerViewModel;

public class AlbumBrowserActivity extends ListActivity {
    private RecyclerView rvAlbumBrowser;
    private AlbumBrowserViewModel mViewModel;
    private PlayerViewModel mPlayerViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_album_browser);

        ViewModelProvider provider = new ViewModelProvider(this);
        mViewModel = provider.get(AlbumBrowserViewModel.class);
        mPlayerViewModel = provider.get(PlayerViewModel.class);

        PlayerUtil.initPlayerViewModel(this, mPlayerViewModel, AppPlayerService.class);

        rvAlbumBrowser = findViewById(R.id.rvAlbumBrowser);
        Button btnSongs = findViewById(R.id.btnAlbums);

        btnSongs.setSelected(true);
        initRecyclerView();
    }

    private void initRecyclerView() {
        rvAlbumBrowser.setLayoutManager(new LinearLayoutManager(this));

        List<String> allAlbum = mViewModel.getAllAlbum().getValue();
        assert allAlbum != null;

        AlbumBrowserAdapter adapter = new AlbumBrowserAdapter(allAlbum);
        rvAlbumBrowser.setAdapter(adapter);

        mViewModel.getAllAlbum()
                .observe(this, adapter::setAllAlbum);

        mPlayerViewModel.getPlayingMusicItem()
                .observe(this, musicItem -> {
                    if (musicItem == null) {
                        adapter.clearMark();
                        return;
                    }

                    List<String> albumList = mViewModel.getAllAlbum().getValue();
                    adapter.setMarkPosition(albumList.indexOf(musicItem.getAlbum()));
                });

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