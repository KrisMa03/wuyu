package butterfly.music.util;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentManager;

import com.google.common.base.Preconditions;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.reactivex.Single;
import butterfly.music.fragment.ringtone.RingtoneUtilFragment;
import butterfly.music.store.Music;
import butterfly.player.audio.MusicItem;
import butterfly.player.playlist.Playlist;

public final class MusicUtil {
    private static final String KEY_ADD_TIME = "add_time";

    private MusicUtil() {
        throw new AssertionError();
    }

    public static Music asMusic(@NonNull MusicItem musicItem) {
        Preconditions.checkNotNull(musicItem);

        return new Music(
                Long.parseLong(musicItem.getMusicId()),
                musicItem.getTitle(),
                musicItem.getArtist(),
                musicItem.getAlbum(),
                musicItem.getUri(),
                musicItem.getIconUri(),
                musicItem.getDuration(),
                getAddTime(musicItem),
                musicItem.getLyrics()
        );
    }
    public static String readLrcFromFile(String filePath) {
        File file = new File(filePath);
        StringBuilder text = new StringBuilder();

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                text.append(line);
                text.append('\n');
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return text.toString();
    }

    public static Map<Integer, String> parseLyricsToSeconds(String lrcContent) {
        Map<Integer, String> lyricsMap = new HashMap<>();
        String[] lines = lrcContent.split("\n");

        for (String line : lines) {
            if (line.trim().isEmpty()) {
                continue;
            }
            // 匹配行中的所有时间标签
            Matcher matcher = Pattern.compile("\\[(\\d{2}):(\\d{2})\\.\\d{2}]").matcher(line);
            List<Integer> times = new ArrayList<>();
            // 提取所有时间标签，并转换为秒
            while (matcher.find()) {
                int minutes = Integer.parseInt(matcher.group(1));
                int seconds = Integer.parseInt(matcher.group(2));
                int timeInSeconds = minutes * 60 + seconds;
                times.add(timeInSeconds);
            }
            // 移除时间标签，留下歌词部分
            String lyrics = line.replaceAll("\\[\\d{2}:\\d{2}\\.\\d{2}]", "").trim();
            // 将同一句歌词映射到所有对应的时间点
            for (int time : times) {
                lyricsMap.put(time, lyrics);
            }
        }
        return lyricsMap;
    }


    public static MusicItem asMusicItem(@NonNull Music music) {
        Preconditions.checkNotNull(music, "Music object cannot be null");
        MusicItem.Builder builder = new MusicItem.Builder()
                .setMusicId(String.valueOf(music.getId()))
                .setTitle(music.getTitle())
                .setArtist(music.getArtist())
                .setAlbum(music.getAlbum())
                .setUri(music.getUri())
                .setIconUri(music.getIconUri())
                .setDuration(music.getDuration());

        if (music.getLyrics() != null) {
            builder.setLyrics(music.getLyrics());
        } else {
            builder.setLyrics("");
        }
        MusicItem musicItem = builder.build();
        putAddTime(musicItem, music);
        return musicItem;
    }

    public static long getId(@NonNull MusicItem musicItem) {
        Preconditions.checkNotNull(musicItem);
        return Long.parseLong(musicItem.getMusicId());
    }

    public static Single<byte[]> getEmbeddedPicture(@NonNull Context context, @NonNull Music music) {
        Preconditions.checkNotNull(context);
        Preconditions.checkNotNull(music);
        return getEmbeddedPicture(context, music.getUri());
    }

    public static Single<byte[]> getEmbeddedPicture(@NonNull Context context, @NonNull String uri) {
        Preconditions.checkNotNull(context);
        Preconditions.checkNotNull(uri);

        return Single.create(emitter -> {
            if (uri.isEmpty()) {
                emitter.onSuccess(new byte[0]);
                return;
            }

            MediaMetadataRetriever retriever = new MediaMetadataRetriever();

            try {
                retriever.setDataSource(context, Uri.parse(uri));
                byte[] picture = retriever.getEmbeddedPicture();
                retriever.release();

                if (emitter.isDisposed()) {
                    return;
                }

                if (picture == null) {
                    emitter.onSuccess(new byte[0]);
                } else {
                    emitter.onSuccess(picture);
                }

            } catch (Exception e) {
                emitter.onSuccess(new byte[0]);
            } finally {
                retriever.release();
            }
        });
    }

    @NonNull
    public static Playlist asPlaylist(int position, @NonNull List<Music> musicList, @NonNull String name) {
        Preconditions.checkNotNull(musicList);
        Preconditions.checkNotNull(name);
        if (position < 0 || position >= musicList.size()) {
            throw new IndexOutOfBoundsException("position out of bound, position: " + position + ", size: " + musicList.size());
        }

        int start = 0;
        int end = musicList.size();
        List<MusicItem> musicItemList = new ArrayList<>(Math.min(musicList.size(), Playlist.MAX_SIZE));

        if (end > Playlist.MAX_SIZE) {
            int value = end - position;
            if (value >= Playlist.MAX_SIZE) {
                end = position + Playlist.MAX_SIZE;
                start = position;
            } else {
                start = position - (Playlist.MAX_SIZE - value);
            }
        }

        for (int i = start; i < end; i++) {
            musicItemList.add(MusicUtil.asMusicItem(musicList.get(i)));
        }

        return new Playlist.Builder()
                .setName(name)
                .appendAll(musicItemList)
                .build();
    }

    public static void setAsRingtone(@NonNull FragmentManager fm, @NonNull Music music) {
        Preconditions.checkNotNull(fm);
        Preconditions.checkNotNull(music);

        RingtoneUtilFragment.setAsRingtone(fm, music);
    }

    private static long getAddTime(MusicItem musicItem) {
        Bundle extra = musicItem.getExtra();
        if (extra == null) {
            return System.currentTimeMillis();
        }

        return extra.getLong(KEY_ADD_TIME, System.currentTimeMillis());
    }

    private static void putAddTime(MusicItem musicItem, Music music) {
        Bundle extra = new Bundle();
        extra.putLong(KEY_ADD_TIME, music.getAddTime());
        musicItem.setExtra(extra);
    }
}
