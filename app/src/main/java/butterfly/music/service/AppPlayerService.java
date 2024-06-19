package butterfly.music.service;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.widget.RemoteViews;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.res.ResourcesCompat;

import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;
import butterfly.music.R;
import butterfly.music.activity.player.PlayerActivity;
import butterfly.music.store.MusicStore;
import butterfly.music.util.FavoriteObserver;
import butterfly.music.util.MusicUtil;
import butterfly.player.HistoryRecorder;
import butterfly.player.PlayMode;
import butterfly.player.PlayerService;
import butterfly.player.annotation.PersistenceId;
import butterfly.player.audio.MusicItem;
import butterfly.player.effect.AudioEffectManager;
import butterfly.player.ui.equalizer.AndroidAudioEffectManager;

@PersistenceId("AppPlayerService")
public class AppPlayerService extends PlayerService {
    private MusicStore mMusicStore;

    @Override
    public void onCreate() {
        super.onCreate();
        setMaxIDLETime(5);
        mMusicStore = MusicStore.getInstance();
    }

    @Nullable
    @Override
    protected NotificationView onCreateNotificationView() {
        return new AppNotificationView();
    }

    @Nullable
    @Override
    protected HistoryRecorder onCreateHistoryRecorder() {
        return musicItem -> Single.create(emitter -> mMusicStore.addHistory(MusicUtil.asMusic(musicItem)))
                .subscribeOn(Schedulers.io())
                .subscribe();
    }

    @Nullable
    @Override
    protected AudioEffectManager onCreateAudioEffectManager() {
        return new AndroidAudioEffectManager();
    }

    private static class AppNotificationView extends MediaNotificationView {
        private static final String ACTION_TOGGLE_FAVORITE = "toggle_favorite";
        private static final String ACTION_SWITCH_PLAY_MODE = "switch_play_mode";

        private FavoriteObserver mFavoriteObserver;
        private PendingIntent mToggleFavorite;
        private PendingIntent mSwitchPlayMode;
        private PendingIntent mContentIntent;

        @Override
        protected void onInit(Context context) {
            super.onInit(context);

            mFavoriteObserver = new FavoriteObserver(favorite -> invalidate());
            mFavoriteObserver.subscribe();

            mToggleFavorite = buildCustomAction(ACTION_TOGGLE_FAVORITE, (player, extras) ->
                    MusicStore.getInstance().toggleFavorite(MusicUtil.asMusic(getPlayingMusicItem())));

            mSwitchPlayMode = buildCustomAction(ACTION_SWITCH_PLAY_MODE, (player, extras) -> {
                switch (getPlayMode()) {
                    case PLAYLIST_LOOP:
                        player.setPlayMode(PlayMode.LOOP);
                        break;
                    case LOOP:
                        player.setPlayMode(PlayMode.SHUFFLE);
                        break;
                    case SHUFFLE:
                        player.setPlayMode(PlayMode.PLAYLIST_LOOP);
                        break;
                }
            });

            setDefaultIcon(loadDefaultIcon());

            Intent intent = new Intent(getContext(), PlayerActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra(PlayerActivity.KEY_START_BY_PENDING_INTENT, true);

            int flags;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;
            } else {
                flags = PendingIntent.FLAG_UPDATE_CURRENT;
            }

            mContentIntent = PendingIntent.getActivity(context, 0, intent, flags);
        }

        @Override
        protected void onPlayModeChanged(@NonNull PlayMode playMode) {
            invalidate();
        }

        @Override
        protected void onRelease() {
            super.onRelease();
            mFavoriteObserver.unsubscribe();
        }

        @Override
        public int getSmallIconId() {
            return R.mipmap.ic_notif_small_icon;
        }

        @Override
        protected void onBuildMediaStyle(androidx.media.app.NotificationCompat.MediaStyle mediaStyle) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                mediaStyle.setShowActionsInCompactView(1, 2, 3);
                return;
            }
            mediaStyle.setShowActionsInCompactView(2, 3);
        }

        @Override
        protected void onBuildNotification(NotificationCompat.Builder builder) {
            addToggleFavorite(builder);
            addSkipToPrevious(builder);
            addPlayPause(builder);
            addSkipToNext(builder);
            addSwitchPlayMode(builder);

            builder.setContentIntent(mContentIntent);
        }

        private Bitmap loadDefaultIcon() {
            Context context = getContext();
            BitmapDrawable drawable = (BitmapDrawable) ResourcesCompat.getDrawable(
                    context.getResources(),
                    R.drawable.ic_notification,
                    context.getTheme());

            if (drawable == null) {
                throw new NullPointerException();
            }

            return drawable.getBitmap();
        }

        @Override
        protected void onPlayingMusicItemChanged(@NonNull MusicItem musicItem) {
            super.onPlayingMusicItemChanged(musicItem);
            mFavoriteObserver.setMusicItem(getPlayingMusicItem());
        }

        private void addToggleFavorite(NotificationCompat.Builder builder) {
            if (mFavoriteObserver.isFavorite()) {
                builder.addAction(0, "favorite", mToggleFavorite);
            } else {
                builder.addAction(0, "don't favorite", mToggleFavorite);
            }
        }

        private void addSkipToPrevious(NotificationCompat.Builder builder) {
            builder.addAction(0, "skip to previous", doSkipToPrevious());
        }

        private void addPlayPause(NotificationCompat.Builder builder) {
            builder.addAction(0, "play pause", doPlayPause());
        }

        private void addSkipToNext(NotificationCompat.Builder builder) {
            builder.addAction(0, "skip to next", doSkipToNext());
        }

        private void addSwitchPlayMode(NotificationCompat.Builder builder) {
            switch (getPlayMode()) {
                case PLAYLIST_LOOP:
                    builder.addAction(R.mipmap.ic_notif_play_mode_playlist_loop, "sequential", mSwitchPlayMode);
                    break;
                case LOOP:
                    builder.addAction(R.mipmap.ic_notif_play_mode_loop, "sequential", mSwitchPlayMode);
                    break;
                case SHUFFLE:
                    builder.addAction(R.mipmap.ic_notif_play_mode_shuffle, "sequential", mSwitchPlayMode);
                    break;
            }
            builder.addAction(0, "switch play mode", mSwitchPlayMode);
        }

        @NonNull
        @Override
        public android.app.Notification onCreateNotification() {
            RemoteViews customView = new RemoteViews(getContext().getPackageName(), android.R.layout.simple_list_item_1);

            // 设置自定义布局中的控件
            customView.setTextViewText(android.R.id.text1, "Song Title - Artist Name");

            // 美化布局
            customView.setTextColor(android.R.id.text1, Color.WHITE);
            customView.setInt(android.R.id.text1, "setBackgroundColor", Color.DKGRAY);
            customView.setTextViewTextSize(android.R.id.text1, TypedValue.COMPLEX_UNIT_SP, 16);

            androidx.media.app.NotificationCompat.MediaStyle mediaStyle =
                    new androidx.media.app.NotificationCompat.MediaStyle()
                            .setMediaSession(getMediaSession().getSessionToken());

            onBuildMediaStyle(mediaStyle);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(getContext(), CHANNEL_ID)
                    .setSmallIcon(getSmallIconId())
                    .setCustomContentView(customView)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setShowWhen(false)
                    .setAutoCancel(false)
                    .setStyle(mediaStyle)
                    .setContentIntent(mContentIntent);

            onBuildNotification(builder);

            return builder.build();
        }

        @NonNull
        @Override
        public android.app.Notification onCreatePlaceHolderNotification(String hint) {
            RemoteViews customView = new RemoteViews(getContext().getPackageName(), android.R.layout.simple_list_item_1);

            // 设置自定义布局中的控件
            customView.setTextViewText(android.R.id.text1, hint);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(getContext(), CHANNEL_ID)
                    .setSmallIcon(getSmallIconId())
                    .setCustomContentView(customView)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setShowWhen(false)
                    .setAutoCancel(false)
                    .setContentIntent(mContentIntent);

            return builder.build();
        }
    }
}
