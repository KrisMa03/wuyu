package butterfly.music.activity.navigation;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import com.google.common.base.Preconditions;

import java.util.List;

import butterfly.music.R;
import butterfly.music.activity.browser.album.AlbumBrowserActivity;
import butterfly.music.activity.browser.artist.ArtistBrowserActivity;
import butterfly.music.activity.browser.musiclist.MusicListBrowserActivity;
import butterfly.music.activity.favorite.FavoriteActivity;
import butterfly.music.activity.history.HistoryActivity;
import butterfly.music.activity.localmusic.LocalMusicActivity;
import butterfly.music.activity.player.PlayerActivity;
import butterfly.music.activity.search.SearchActivity;
import butterfly.music.activity.setting.SettingActivity;
import butterfly.music.store.Music;
import butterfly.music.store.MusicStore;
import butterfly.music.util.FavoriteObserver;
import butterfly.music.util.MusicUtil;
import butterfly.player.PlaybackState;
import butterfly.player.PlayerClient;
import butterfly.player.audio.MusicItem;
import butterfly.player.lifecycle.PlayerViewModel;

public class NavigationViewModel extends ViewModel {
    //最爱的音乐
    private final MutableLiveData<List<Music>> mFavoriteMusic;
    private final MutableLiveData<Integer> mFavoriteDrawable;
    private final MutableLiveData<CharSequence> mSecondaryText;

    private final Observer<MusicItem> mPlayingMusicItemObserver;
    private final Observer<String> mArtistObserver;
    private final Observer<Boolean> mErrorObserver;
    private final FavoriteObserver mFavoriteObserver;

    private PlayerViewModel mPlayerViewModel;
    private boolean mInitialized;

    public NavigationViewModel() {
        // 初始化
        mSecondaryText = new MutableLiveData<>("");
        mFavoriteMusic = new MutableLiveData<>();
        // 创建一个观察者来观察最喜欢的音乐的变化，并在变化时更新最喜欢的音乐
        mFavoriteObserver = new FavoriteObserver(favorite ->
                updateFavoriteMusic()
        );
        mFavoriteDrawable = new MutableLiveData<>(R.drawable.ic_favorite_false);
        mPlayingMusicItemObserver = mFavoriteObserver::setMusicItem;
        mArtistObserver = artist -> updateSecondaryText();
        mErrorObserver = error -> updateSecondaryText();
    }

    public void init(@NonNull PlayerViewModel playerViewModel) {
        Preconditions.checkNotNull(playerViewModel);
        if (mInitialized) {
            return;
        }
        mInitialized = true;
        mPlayerViewModel = playerViewModel;
        // 永久观察
        mPlayerViewModel.getPlayingMusicItem().observeForever(mPlayingMusicItemObserver);
        mPlayerViewModel.getArtist().observeForever(mArtistObserver);
        // 让最喜欢的观察者订阅
        mFavoriteObserver.subscribe();
        mPlayerViewModel.isError().observeForever(mErrorObserver);
        // 更新最喜欢的音乐
        updateFavoriteMusic();
    }

    //最喜爱
    private void updateFavoriteMusic() {
        List<Music> favoriteMusicList = MusicStore.getInstance().getFavoriteMusicList().getMusicElements();
        mFavoriteMusic.postValue(favoriteMusicList);
    }

    public LiveData<List<Music>> getFavoriteMusic() {
        return mFavoriteMusic;
    }

    private void updateSecondaryText() {
        PlayerClient playerClient = mPlayerViewModel.getPlayerClient();

        CharSequence text = mPlayerViewModel.getArtist().getValue();

        if (playerClient.isError()) {
            text = playerClient.getErrorMessage();
            SpannableString colorText = new SpannableString(text);
            colorText.setSpan(new ForegroundColorSpan(Color.parseColor("#F44336")), 0, text.length(), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
            text = colorText;
        }

        mSecondaryText.setValue(text);
    }

    public boolean isInitialized() {
        return mInitialized;
    }

    @Override
    protected void onCleared() {
        mFavoriteObserver.unsubscribe();
        if (isInitialized()) {
            mPlayerViewModel.getPlayingMusicItem().removeObserver(mPlayingMusicItemObserver);
            mPlayerViewModel.getArtist().removeObserver(mArtistObserver);
            mPlayerViewModel.isError().removeObserver(mErrorObserver);
        }
    }

    public LiveData<String> getMusicTitle() {
        if (!mInitialized) {
            throw new IllegalStateException("NavigationViewModel not init yet.");
        }

        return mPlayerViewModel.getTitle();
    }

    public LiveData<CharSequence> getSecondaryText() {
        if (!mInitialized) {
            throw new IllegalStateException("NavigationViewModel not init yet.");
        }

        return mSecondaryText;
    }

    @NonNull
    public LiveData<Integer> getFavoriteDrawable() {
        return mFavoriteDrawable;
    }

    @NonNull
    public LiveData<Integer> getPlayPauseDrawable() {
        if (!mInitialized) {
            throw new IllegalStateException("NavigationViewModel not init yet.");
        }

        return Transformations.map(mPlayerViewModel.getPlaybackState(), playbackState -> {
            if (playbackState == PlaybackState.PLAYING) {
                return R.mipmap.ic_pause;
            } else {
                return R.mipmap.ic_play;
            }
        });
    }

    public void togglePlayingMusicFavorite() {
        if (!isInitialized()) {
            return;
        }

        MusicItem playingMusicItem = mPlayerViewModel.getPlayingMusicItem().getValue();
        if (playingMusicItem == null) {
            return;
        }

        MusicStore.getInstance().toggleFavorite(MusicUtil.asMusic(playingMusicItem));
    }

    public void skipToPrevious() {
        if (!mInitialized) {
            throw new IllegalStateException("NavigationViewModel not init yet.");
        }

        mPlayerViewModel.skipToPrevious();
    }

    public void playPause() {
        if (!mInitialized) {
            throw new IllegalStateException("NavigationViewModel not init yet.");
        }

        mPlayerViewModel.playPause();
    }

    public void skipToNext() {
        if (!mInitialized) {
            throw new IllegalStateException("NavigationViewModel not init yet.");
        }

        mPlayerViewModel.skipToNext();
    }

    public LiveData<Integer> getDuration() {
        if (!mInitialized) {
            throw new IllegalStateException("NavigationViewModel not init yet.");
        }

        return mPlayerViewModel.getDuration();
    }

    public LiveData<Integer> getPlayProgress() {
        if (!mInitialized) {
            throw new IllegalStateException("NavigationViewModel not init yet.");
        }

        return mPlayerViewModel.getPlayProgress();
    }

    public void navigateToSearch(View view) {
        Context context = view.getContext();
        SearchActivity.start(context, SearchActivity.Type.MUSIC_LIST, MusicStore.MUSIC_LIST_LOCAL_MUSIC);
    }

    public void navigateToSetting(View view) {
        startActivity(view.getContext(), SettingActivity.class);
    }

    public void navigateToLocalMusic(View view) {
        startActivity(view.getContext(), LocalMusicActivity.class);
    }

    public void navigateToFavorite(View view) {
        startActivity(view.getContext(), FavoriteActivity.class);
    }

    public void navigateToMusicListBrowser(View view) {
        startActivity(view.getContext(), MusicListBrowserActivity.class);
    }

    public void navigateToArtistBrowser(View view) {
        startActivity(view.getContext(), ArtistBrowserActivity.class);
    }

    public void navigateToAlbum(View view) {
        startActivity(view.getContext(), AlbumBrowserActivity.class);
    }

    public void navigateToHistory(View view) {
        startActivity(view.getContext(), HistoryActivity.class);
    }

    public void navigateToPlayer(View view) {
        startActivity(view.getContext(), PlayerActivity.class);
    }

    private void startActivity(Context context, Class<? extends Activity> activity) {
        Intent intent = new Intent(context, activity);
        context.startActivity(intent);
    }
    
}
