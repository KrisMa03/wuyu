package butterfly.music.activity.detail.artist;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.List;

import pinyin.util.PinyinComparator;
import butterfly.music.fragment.musiclist.BaseMusicListViewModel;
import butterfly.music.store.Music;
import butterfly.music.store.MusicList;
import butterfly.music.store.MusicStore;

public class ArtistDetailViewModel extends BaseMusicListViewModel {
    @NonNull
    @Override
    protected List<Music> loadMusicListItems() {
        List<Music> musicList = MusicStore.getInstance().getArtistAllMusic(getArtistName());

        PinyinComparator pinyinComparator = new PinyinComparator();
        Collections.sort(musicList, (o1, o2) -> pinyinComparator.compare(o1.getTitle(), o2.getTitle()));

        return musicList;
    }

    @NonNull
    @Override
    protected MusicList.SortOrder getSortOrder() {
        return MusicList.SortOrder.BY_TITLE;
    }

    // 去除前缀
    private String getArtistName() {
        String artist = getMusicListName();
        return artist.substring(ArtistDetailActivity.ARTIST_PREFIX.length());
    }
    @Override
    protected void removeMusic(@NonNull Music music) {
        // ignore
    }

    @Override
    protected void onSortMusicList(@NonNull MusicList.SortOrder sortOrder) {
        // ignore
    }
}
