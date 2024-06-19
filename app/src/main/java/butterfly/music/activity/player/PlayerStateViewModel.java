package butterfly.music.activity.player;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import com.google.common.base.Preconditions;

import butterfly.music.R;
import butterfly.music.service.AppPlayerService;
import butterfly.music.store.MusicStore;
import butterfly.music.util.FavoriteObserver;
import butterfly.music.util.MusicUtil;
import butterfly.player.PlayMode;
import butterfly.player.PlaybackState;
import butterfly.player.PlayerClient;
import butterfly.player.audio.MusicItem;
import butterfly.player.lifecycle.PlayerViewModel;
import butterfly.player.ui.equalizer.EqualizerActivity;

public class PlayerStateViewModel extends ViewModel {
    private final MutableLiveData<Integer> mFavoriteDrawable;
    private final MutableLiveData<Integer> mErrorMessageVisibility;
    private final MutableLiveData<String> mErrorMessage;
    private final MutableLiveData<Boolean> mKeepScreenOn;
    private final MutableLiveData<Integer> mKeepScreenOnDrawable;

    private final FavoriteObserver mFavoriteObserver;
    private final Observer<MusicItem> mPlayingMusicItemObserver;
    private final Observer<Boolean> mErrorObserver;

    private PlayerViewModel mPlayerViewModel;
    private boolean mInitialized;

    private boolean mStartByPendingIntent;

    private boolean mIgnoreKeepScreenOnToast;

    public PlayerStateViewModel() {
        mFavoriteDrawable = new MutableLiveData<>(R.drawable.ic_favorite_false);
        mErrorMessageVisibility = new MutableLiveData<>(View.GONE);
        mErrorMessage = new MutableLiveData<>("");
        mKeepScreenOn = new MutableLiveData<>(false);
        mKeepScreenOnDrawable = new MutableLiveData<>(R.drawable.ic_keep_screen_on_false);

        mFavoriteObserver = new FavoriteObserver(favorite ->
                mFavoriteDrawable.setValue(favorite ? R.drawable.ic_favorite_true : R.drawable.ic_favorite_false));
        mErrorObserver = this::updateErrorState;

        mPlayingMusicItemObserver = mFavoriteObserver::setMusicItem;
    }

    public void init(@NonNull PlayerViewModel playerViewModel, boolean startByPendingIntent) {
        Preconditions.checkNotNull(playerViewModel);

        if (mInitialized) {
            return;
        }

        mInitialized = true;
        mStartByPendingIntent = startByPendingIntent;
        mPlayerViewModel = playerViewModel;

        mFavoriteObserver.subscribe();
        mPlayerViewModel.getPlayingMusicItem().observeForever(mPlayingMusicItemObserver);
        mPlayerViewModel.isError().observeForever(mErrorObserver);
    }

    public boolean isInitialized() {
        return mInitialized;
    }

    @Override
    protected void onCleared() {
        mFavoriteObserver.unsubscribe();

        if (isInitialized()) {
            mPlayerViewModel.getPlayingMusicItem().removeObserver(mPlayingMusicItemObserver);
            mPlayerViewModel.isError().removeObserver(mErrorObserver);
        }
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

    public LiveData<Integer> getPlayModeDrawable() {
        if (!mInitialized) {
            throw new IllegalStateException("NavigationViewModel not init yet.");
        }

        return Transformations.map(mPlayerViewModel.getPlayMode(), playMode -> {
            switch (playMode) {
                case PLAYLIST_LOOP:
                    return R.drawable.ic_play_mode_playlist_loop;
                case LOOP:
                    return R.drawable.ic_play_mode_loop;
                case SHUFFLE:
                    return R.drawable.ic_play_mode_shuffle;
            }

            return R.drawable.ic_play_mode_playlist_loop;
        });
    }

    public LiveData<Boolean> getKeepScreenOn() {
        return mKeepScreenOn;
    }

    public LiveData<Integer> getKeepScreenOnDrawable() {
        return mKeepScreenOnDrawable;
    }

    public LiveData<String> getErrorMessage() {
        return mErrorMessage;
    }

    public MutableLiveData<Integer> getErrorMessageVisibility() {
        return mErrorMessageVisibility;
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

    public void switchPlayMode() {
        if (!isInitialized()) {
            return;
        }

        PlayerClient playerClient = mPlayerViewModel.getPlayerClient();
        switch (playerClient.getPlayMode()) {
            case PLAYLIST_LOOP:
                playerClient.setPlayMode(PlayMode.LOOP);
                break;
            case LOOP:
                playerClient.setPlayMode(PlayMode.SHUFFLE);
                break;
            case SHUFFLE:
                playerClient.setPlayMode(PlayMode.PLAYLIST_LOOP);
                break;
        }
    }

    public boolean isStartByPendingIntent() {
        return mStartByPendingIntent;
    }

    private void updateErrorState(boolean error) {
        if (error) {
            mErrorMessage.setValue(mPlayerViewModel.getPlayerClient().getErrorMessage());
            mErrorMessageVisibility.setValue(View.VISIBLE);
        } else {
            mErrorMessage.setValue("");
            mErrorMessageVisibility.setValue(View.GONE);
        }
    }

    public void toggleKeepScreenOn() {
        Boolean keepScreenOn = mKeepScreenOn.getValue();
        assert keepScreenOn != null;

        if (keepScreenOn) {
            mKeepScreenOn.setValue(false);
            mKeepScreenOnDrawable.setValue(R.drawable.ic_keep_screen_on_false);
            return;
        }

        mKeepScreenOn.setValue(true);
        mKeepScreenOnDrawable.setValue(R.drawable.ic_keep_screen_on_true);
    }

    public void startEqualizerActivity(View view) {
        EqualizerActivity.start(view.getContext(), AppPlayerService.class);
    }

    public void setIgnoreKeepScreenOnToast(boolean ignoreKeepScreenOnToast) {
        mIgnoreKeepScreenOnToast = ignoreKeepScreenOnToast;
    }

    public boolean consumeIgnoreKeepScreenOnToast() {
        boolean result = mIgnoreKeepScreenOnToast;
        mIgnoreKeepScreenOnToast = false;
        return result;
    }
}
