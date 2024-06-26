package butterfly.music.activity.player;

import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.ViewModelProvider;

import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.bumptech.glide.RequestManager;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;

import java.util.HashMap;
import java.util.Map;

import butterfly.music.GlideApp;
import butterfly.music.R;
import butterfly.music.activity.BaseActivity;
import butterfly.music.activity.detail.album.AlbumDetailActivity;
import butterfly.music.activity.detail.artist.ArtistDetailActivity;
import butterfly.music.activity.navigation.NavigationActivity;
import butterfly.music.databinding.ActivityPlayerBinding;
import butterfly.music.dialog.AddToMusicListDialog;
import butterfly.music.dialog.BottomMenuDialog;
import butterfly.music.dialog.PlaylistDialog;
import butterfly.music.service.AppPlayerService;
import butterfly.music.util.MusicUtil;
import butterfly.music.util.PlayerUtil;
import butterfly.player.audio.MusicItem;
import butterfly.player.lifecycle.PlayerViewModel;

public class PlayerActivity extends BaseActivity {
    public static final String KEY_START_BY_PENDING_INTENT = "START_BY_PENDING_INTENT";

    private PlayerViewModel mPlayerViewModel;
    private ActivityPlayerBinding mBinding;
    private AlbumIconAnimManager mAlbumIconAnimManager;
    private RequestManager mRequestManager;
    private Map<Integer, String> mLyrics;
    private PlayerStateViewModel mPlayerStateViewModel;
    private Handler mHandler = new Handler();
    private Runnable mUpdateLyricsRunnable;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = DataBindingUtil.setContentView(this, R.layout.activity_player);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        // 创建ViewModelProvider
        ViewModelProvider provider = new ViewModelProvider(this);
        mPlayerViewModel = provider.get(PlayerViewModel.class);
        PlayerUtil.initPlayerViewModel(this, mPlayerViewModel, AppPlayerService.class);
        // 设置播放器客户端
        setPlayerClient(mPlayerViewModel.getPlayerClient());
        mPlayerStateViewModel = provider.get(PlayerStateViewModel.class);
        // 初始化PlayerStateViewModel
        mPlayerStateViewModel.init(mPlayerViewModel, isStartByPendingIntent());
        mBinding.setPlayerViewModel(mPlayerViewModel);
        mBinding.setPlayerStateViewModel(mPlayerStateViewModel);
        mBinding.setLifecycleOwner(this);
        mAlbumIconAnimManager = new AlbumIconAnimManager(mBinding.ivAlbumIcon, this, mPlayerViewModel);
        mRequestManager = GlideApp.with(this);
        // 观察正在播放的音乐项
        observePlayingMusicItem(mPlayerViewModel);
        mPlayerStateViewModel.setIgnoreKeepScreenOnToast(true);
        // 观察是否保持屏幕常亮
        mPlayerStateViewModel.getKeepScreenOn()
                .observe(this, keepScreenOn -> {
                    // 如果消费了忽略保持屏幕常亮的Toast，则直接返回
                    if (mPlayerStateViewModel.consumeIgnoreKeepScreenOnToast()) {
                        return;
                    }
                    // 根据是否保持屏幕常亮来设置消息ID
                    int messageId;
                    if (keepScreenOn) {
                        messageId = R.string.toast_keep_screen_on;
                    } else {
                        messageId = R.string.toast_cancel_keep_screen_on;
                    }
                    // 显示Toast
                    Toast.makeText(PlayerActivity.this, messageId, Toast.LENGTH_SHORT).show();
                });
    }
    //更新歌词
    private void updateLyrics() {
        long currentPosition = mPlayerViewModel.getPlayProgress().getValue(); // 获取当前播放进度
        Log.d("lyrics",currentPosition+""+mLyrics.toString());
        if (mLyrics.isEmpty()) {
            mBinding.tvLyrics.setText(""); // 如果没有歌词，则不显示任何内容
            return;
        }
        // 查找当前时间之前的最近一条歌词
        String currentLyric = "";
        long closestTime = -1;
        for (Map.Entry<Integer, String> entry : mLyrics.entrySet()) {
            long time = entry.getKey();
            if (time <= currentPosition && time > closestTime) {
                closestTime = time;
                currentLyric = entry.getValue();
            }
        }
        mBinding.tvLyrics.setText(currentLyric);
    }

    private boolean isStartByPendingIntent() {
        Intent intent = getIntent();
        return intent.getBooleanExtra(KEY_START_BY_PENDING_INTENT, false);
    }

    private void observePlayingMusicItem(PlayerViewModel playerViewModel) {
        playerViewModel.getPlayingMusicItem()
                .observe(this, musicItem -> {
                    mAlbumIconAnimManager.reset();
                    if (musicItem == null) {
                        mBinding.ivAlbumIcon.setImageResource(R.mipmap.ic_player_album_default_icon_big);
                        return;
                    }
                    mRequestManager.load(musicItem.getUri())
                            .error(R.mipmap.ic_player_album_default_icon_big)
                            .placeholder(R.mipmap.ic_player_album_default_icon_big)
                            .transform(new CenterCrop(), new CircleCrop())
                            .transition(DrawableTransitionOptions.withCrossFade())
                            .into(mBinding.ivAlbumIcon);
                    // 初始化歌词
                    mLyrics = new HashMap<>(); // 初始化歌词映射
                    // 从当前播放的音乐中获取歌词并进行解析
                    MusicItem currentMusicItem = musicItem;
                    if (currentMusicItem != null) {
                        String lyricsContent = currentMusicItem.getLyrics();
                        mLyrics = MusicUtil.parseLyricsToSeconds(lyricsContent);
                    }
                    // 初始化歌词更新 Runnable
                    mUpdateLyricsRunnable = new Runnable() {
                        @Override
                        public void run() {
                            updateLyrics();
                            mHandler.postDelayed(this, 100); // 每500毫秒更新一次歌词
                        }
                    };
                    mHandler.post(mUpdateLyricsRunnable); // 开始更新歌词
                });
    }

    public void finishSelf(View view) {
        finish();
    }

    public void showPlaylist(View view) {
        PlaylistDialog.newInstance()
                .show(getSupportFragmentManager(), "Playlist");
    }

    @Override
    public void finish() {
        if (mPlayerStateViewModel.isStartByPendingIntent()) {
            startActivity(new Intent(this, NavigationActivity.class));
            overridePendingTransition(R.anim.activity_slide_in_left, R.anim.activity_slide_out_right);
        }

        super.finish();
    }
    public void showOptionMenu(View view) {
        MusicItem musicItem = mPlayerViewModel.getPlayerClient().getPlayingMusicItem();
        if (musicItem == null) {
            return;
        }
        BottomMenuDialog bottomMenuDialog = new BottomMenuDialog.Builder(this)
                .setTitle(musicItem.getTitle()) // 设置对话框标题为歌曲标题
                .addMenuItem(R.drawable.ic_artist, musicItem.getArtist())
                .addMenuItem(R.drawable.ic_album, musicItem.getAlbum())
                .addMenuItem(R.drawable.ic_menu_item_add, R.string.menu_item_add_to_music_list)
                .addMenuItem(R.drawable.ic_menu_item_rington, R.string.menu_item_set_as_ringtone)
                .setOnMenuItemClickListener((dialog, position) -> {
                    dialog.dismiss();
                    if (position == 0) { // 点击歌手名字
                        ArtistDetailActivity.start(this, musicItem.getArtist());
                    } else if (position == 1) { // 点击专辑名字
                        AlbumDetailActivity.start(this, musicItem.getAlbum());
                    } else if (position == 2) {
                        addToMusicListDialog(musicItem);
                    } else if (position == 3) {
                        setAsRingtone(musicItem);
                    }
                })
                .build();
        bottomMenuDialog.show(getSupportFragmentManager(), "musicItemOptionMenu");
    }

    private void addToMusicListDialog(MusicItem musicItem) {
        AddToMusicListDialog dialog = AddToMusicListDialog.newInstance(MusicUtil.asMusic(musicItem));
        dialog.show(getSupportFragmentManager(), "addToMusicList");
    }

    private void setAsRingtone(MusicItem musicItem) {
        MusicUtil.setAsRingtone(getSupportFragmentManager(), MusicUtil.asMusic(musicItem));
    }

    //销毁歌词
    @Override
    protected void onDestroy() {
        super.onDestroy();
        mHandler.removeCallbacks(mUpdateLyricsRunnable); // 停止更新歌词
    }
}