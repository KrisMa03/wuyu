package butterfly.music.activity.detail.album;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.List;

import pinyin.util.PinyinComparator;
import butterfly.music.fragment.musiclist.BaseMusicListViewModel;
import butterfly.music.store.Music;
import butterfly.music.store.MusicList;
import butterfly.music.store.MusicStore;

public class AlbumDetailViewModel extends BaseMusicListViewModel {
    @NonNull
    @Override
    protected List<Music> loadMusicListItems() {
        List<Music> musicList = MusicStore.getInstance().getAlbumAllMusic(getAlbumName());
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
    private String getAlbumName() {
        String album = getMusicListName();
        return album.substring(AlbumDetailActivity.ALBUM_PREFIX.length());
    }
    @Override
    protected void removeMusic(@NonNull Music music) {
    }

    @Override
    protected void onSortMusicList(@NonNull MusicList.SortOrder sortOrder) {
    }
}
