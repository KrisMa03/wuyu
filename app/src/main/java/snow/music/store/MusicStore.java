package snow.music.store;

import android.content.Context;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.common.base.Preconditions;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import android.os.Handler;
import android.util.Log;

import io.objectbox.Box;
import io.objectbox.BoxStore;
import io.objectbox.query.QueryBuilder;
import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;
import snow.music.util.MusicUtil;

//用于吧歌曲存在数据库
public class MusicStore {
    private static final String TAG = "MusicStore";
    public static final String MUSIC_LIST_LOCAL_MUSIC = "__local_music";
    public static final String MUSIC_LIST_FAVORITE = "__favorite";

    public static final int NAME_MAX_LENGTH = 40;

    private static MusicStore mInstance;

    private final BoxStore mBoxStore;
    private final Box<Music> mMusicBox;
    private final Box<MusicListEntity> mMusicListEntityBox;
    private final Box<HistoryEntity> mHistoryEntityBox;

    private final Handler mMainHandler;

    private final List<OnFavoriteChangeListener> mAllFavoriteChangeListener;
    private final List<OnCustomMusicListUpdateListener> mAllCustomMusicListUpdateListener;
    private OnScanCompleteListener mOnScanCompleteListener;

    private final Set<String> mAllCustomMusicListName;

    private MusicStore(BoxStore boxStore) {
        mBoxStore = boxStore;
        mMusicBox = boxStore.boxFor(Music.class);
        mMusicListEntityBox = boxStore.boxFor(MusicListEntity.class);
        mHistoryEntityBox = boxStore.boxFor(HistoryEntity.class);
        mMainHandler = new Handler(Looper.getMainLooper());
        mAllFavoriteChangeListener = new ArrayList<>();
        mAllCustomMusicListUpdateListener = new ArrayList<>();
        mAllCustomMusicListName = new HashSet<>();

        loadAllMusicListName();
    }
    public void bindLyricsToMusicById(long musicId, String lrcFilePath) {
        Music music = getMusic(musicId);
        if (music != null) {
            String lyrics = MusicUtil.readLrcFromFile(lrcFilePath);
            music.setLyrics(lyrics);
            putMusic(music); // 保存更新后的音乐对象
        }
    }

    private void loadAllMusicListName() {
        Single.create(emitter -> {
            String[] allName = mMusicListEntityBox.query()
                    .notEqual(MusicListEntity_.name, MUSIC_LIST_LOCAL_MUSIC, QueryBuilder.StringOrder.CASE_SENSITIVE)
                    .notEqual(MusicListEntity_.name, MUSIC_LIST_FAVORITE, QueryBuilder.StringOrder.CASE_SENSITIVE)
                    .build()
                    .property(MusicListEntity_.name)
                    .findStrings();

            if (allName == null) {
                return;
            }

            mAllCustomMusicListName.addAll(Arrays.asList(allName));

        }).subscribeOn(Schedulers.io())
                .subscribe();
    }


    public synchronized static void init(@NonNull Context context) {
        Preconditions.checkNotNull(context);

        if (mInstance != null) {
            return;
        }

        BoxStore boxStore = MyObjectBox.builder()
                .directory(new File(context.getFilesDir(), "music_store"))
                .build();

        init(boxStore);
    }

    //初始化
    public synchronized static void init(@NonNull BoxStore boxStore) {
        Preconditions.checkNotNull(boxStore);

        mInstance = new MusicStore(boxStore);
    }

    public static MusicStore getInstance() throws IllegalStateException {
        if (mInstance == null) {
            throw new IllegalStateException("music store not init yet.");
        }

        return mInstance;
    }

    public synchronized void sort(@NonNull MusicList musicList, @NonNull MusicList.SortOrder sortOrder, @Nullable SortCallback callback) {
        Preconditions.checkNotNull(musicList);
        Preconditions.checkNotNull(sortOrder);

        BoxStore boxStore = getInstance().getBoxStore();
        boxStore.runInTxAsync(() -> {
            ArrayList<Music> items = new ArrayList<>(musicList.getMusicElements());
            Collections.sort(items, sortOrder.comparator());

            musicList.musicListEntity.sortOrder = sortOrder;
            musicList.getMusicElements().clear();
            musicList.getMusicElements().addAll(items);
            updateMusicList(musicList);
        }, (result, error) -> mMainHandler.post(() -> {
            if (callback != null) {
                callback.onSortFinished();
            }
        }));
    }

    //获得数据库BoxStore 对象
    public synchronized BoxStore getBoxStore() throws IllegalStateException {
        if (mBoxStore == null) {
            throw new IllegalStateException("MusicStore not init yet.");
        }

        return mBoxStore;
    }

    private void checkThread() {
        if (Thread.currentThread() == Looper.getMainLooper().getThread()) {
            Log.e(TAG, "Please do not access the database on the main thread.");
        }
    }

    //查看歌单是否存在
    public boolean isNameExists(@NonNull String name) {
        Preconditions.checkNotNull(name);

        name = trimName(name);
        return isBuiltInName(name) || mAllCustomMusicListName.contains(name);
    }

    private String trimName(String name) {
        return name.trim().substring(0, Math.min(name.length(), NAME_MAX_LENGTH));
    }

    //获取内置歌单名称
    @NonNull
    public Set<String> getAllCustomMusicListName() {
        return mAllCustomMusicListName;
    }

    //获取对应歌曲歌单
    @NonNull
    public synchronized List<String> getAllCustomMusicListName(@NonNull Music music) {
        Preconditions.checkNotNull(music);

        QueryBuilder<MusicListEntity> builder = mMusicListEntityBox.query()
                .notEqual(MusicListEntity_.name, MUSIC_LIST_LOCAL_MUSIC, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .notEqual(MusicListEntity_.name, MUSIC_LIST_FAVORITE, QueryBuilder.StringOrder.CASE_SENSITIVE);

        builder.link(MusicListEntity_.musicElements)
                .equal(Music_.id, music.getId());

        String[] names = builder.build()
                .property(MusicListEntity_.name)
                .findStrings();

        if (names == null) {
            return Collections.emptyList();
        }

        return new ArrayList<>(Arrays.asList(names));
    }

    //查看歌单是否存在
    public synchronized boolean isMusicListExists(@NonNull String name) {
        Preconditions.checkNotNull(name);
        checkThread();

        long count = mMusicListEntityBox.query()
                .equal(MusicListEntity_.name, name, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .build()
                .count();

        return count > 0;
    }

    private boolean isMusicListExists(long id) {
        long count = mMusicListEntityBox.query()
                .equal(MusicListEntity_.id, id)
                .build()
                .count();

        return count > 0;
    }

    //创建新歌单
    @NonNull
    public synchronized MusicList createCustomMusicList(@NonNull String name) throws IllegalArgumentException {
        Preconditions.checkNotNull(name);
        Preconditions.checkArgument(!name.isEmpty(), "name must not empty");
        checkThread();

        if (isBuiltInName(name)) {
            throw new IllegalArgumentException("Illegal music list name, conflicts with built-in name.");
        }

        name = trimName(name);

        if (isMusicListExists(name)) {
            MusicList musicList = getCustomMusicList(name);
            assert musicList != null;
            return musicList;
        }

        mAllCustomMusicListName.add(name);
        MusicListEntity entity = new MusicListEntity(0, name, 0, MusicList.SortOrder.BY_ADD_TIME, new byte[0]);
        mMusicListEntityBox.put(entity);
        return new MusicList(entity);
    }

    //获取对应歌单
    @Nullable
    public synchronized MusicList getCustomMusicList(@NonNull String name) {
        Preconditions.checkNotNull(name);
        checkThread();

        if (isBuiltInName(name)) {
            return null;
        }

        MusicListEntity entity = mMusicListEntityBox.query()
                .equal(MusicListEntity_.name, name, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .build()
                .findUnique();

        if (entity == null) {
            return null;
        }

        return new MusicList(entity);
    }

    //更新歌曲
    public synchronized void updateMusicList(@NonNull MusicList musicList) {
        Preconditions.checkNotNull(musicList);
        checkThread();

        if (!isMusicListExists(musicList.getId())) {
            return;
        }

        String name = musicList.getName();
        if (!isBuiltInName(name)) {
            mAllCustomMusicListName.add(name);
            notifyCustomMusicListUpdated(name);
        }

        musicList.applyChanges();
        mMusicListEntityBox.put(musicList.musicListEntity);
    }

    //删除歌单
    public synchronized void deleteMusicList(@NonNull MusicList musicList) {
        Preconditions.checkNotNull(musicList);
        checkThread();

        if (isBuiltInMusicList(musicList)) {
            return;
        }

        mAllCustomMusicListName.remove(musicList.getName());
        mMusicListEntityBox.query()
                .equal(MusicListEntity_.id, musicList.getId())
                .build()
                .remove();
    }

    //按名字删除歌单
    public synchronized void deleteMusicList(@NonNull String name) {
        Preconditions.checkNotNull(name);
        checkThread();

        if (isBuiltInName(name)) {
            return;
        }

        mMusicListEntityBox.query()
                .equal(MusicListEntity_.name, name, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .build()
                .remove();
    }

    //重命名歌单
    public synchronized void renameMusicList(@NonNull MusicList musicList, @NonNull String newName) {
        Preconditions.checkNotNull(musicList);
        Preconditions.checkNotNull(newName);

        if (newName.isEmpty() || !isMusicListExists(musicList.getName())) {
            return;
        }

        mAllCustomMusicListName.remove(musicList.getName());

        newName = trimName(newName);

        mAllCustomMusicListName.add(newName);
        musicList.musicListEntity.name = newName;
        updateMusicList(musicList);
    }

    //获得所有歌单
    @NonNull
    public synchronized List<MusicList> getAllCustomMusicList() {
        checkThread();

        List<MusicListEntity> allEntity = mMusicListEntityBox.query()
                .notEqual(MusicListEntity_.name, MUSIC_LIST_LOCAL_MUSIC, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .and()
                .notEqual(MusicListEntity_.name, MUSIC_LIST_FAVORITE, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .build()
                .find();

        if (allEntity.isEmpty()) {
            return Collections.emptyList();
        }

        List<MusicList> allMusicList = new ArrayList<>(allEntity.size());

        for (MusicListEntity entity : allEntity) {
            allMusicList.add(new MusicList(entity));
        }

        return allMusicList;
    }

    /**
     * 将 {@link Music} 对象添加到 musicListNames 包含的所有歌单中。
     *
     * @param music            {@link Music} 对象，不能为 null
     * @param allMusicListName {@link Music} 对象要添加到的所有歌单的名称，不能为 null。
     */
    public synchronized void addToAllMusicList(@NonNull Music music, @NonNull List<String> allMusicListName) {
        Preconditions.checkNotNull(music);
        Preconditions.checkNotNull(allMusicListName);

        List<MusicListEntity> entityList = new ArrayList<>();
        for (String name : allMusicListName) {
            MusicList musicList = getCustomMusicList(name);
            if (musicList == null) {
                continue;
            }
            musicList.getMusicElements().add(music);
            musicList.applyChanges();
            entityList.add(musicList.musicListEntity);
        }

        mMusicListEntityBox.put(entityList);

        notifyCustomMusicListUpdated(allMusicListName);
    }

    //多手歌曲添加到多个歌单
    public synchronized void addToAllMusicList(@NonNull List<Music> allMusic, @NonNull List<String> allMusicListName) {
        Preconditions.checkNotNull(allMusic);
        Preconditions.checkNotNull(allMusicListName);

        List<MusicListEntity> entityList = new ArrayList<>();
        for (String name : allMusicListName) {
            MusicList musicList = getCustomMusicList(name);
            if (musicList == null) {
                continue;
            }
            musicList.getMusicElements().addAll(allMusic);
            musicList.applyChanges();
            entityList.add(musicList.musicListEntity);
        }

        mMusicListEntityBox.put(entityList);

        notifyCustomMusicListUpdated(allMusicListName);
    }

    //查看歌曲是否在收藏
    public synchronized boolean isFavorite(@NonNull Music music) {
        Preconditions.checkNotNull(music);
        checkThread();

        return isFavorite(music.getId());
    }

    //按歌曲id查找是否在收藏
    public synchronized boolean isFavorite(long musicId) {
        checkThread();
        if (musicId <= 0) {
            return false;
        }

        QueryBuilder<Music> builder = mMusicBox.query().equal(Music_.id, musicId);
        builder.backlink(MusicListEntity_.musicElements)
                .equal(MusicListEntity_.name, MUSIC_LIST_FAVORITE, QueryBuilder.StringOrder.CASE_SENSITIVE);

        return builder.build().count() > 0;
    }

    //获得本地音乐歌单
    public synchronized MusicList getLocalMusicList() {
        checkThread();
        return getBuiltInMusicList(MUSIC_LIST_LOCAL_MUSIC);
    }


     //获取 “我的收藏” 歌单。
    @NonNull
    public synchronized MusicList getFavoriteMusicList() {
        checkThread();
        return getBuiltInMusicList(MUSIC_LIST_FAVORITE);
    }


     //将歌曲添加到 “我的收藏” 歌单。
    public synchronized void addToFavorite(@NonNull Music music) {
        Preconditions.checkNotNull(music);
        checkThread();

        if (isFavorite(music)) {
            return;
        }

        MusicList favorite = getFavoriteMusicList();
        favorite.getMusicElements().add(music);
        updateMusicList(favorite);
        notifyFavoriteChanged();
    }


    //将歌曲从 “我的收藏” 歌单中移除。
    public synchronized void removeFromFavorite(@NonNull Music music) {
        Preconditions.checkNotNull(music);
        checkThread();

        if (isFavorite(music)) {
            MusicList favorite = getFavoriteMusicList();
            favorite.getMusicElements().remove(music);
            updateMusicList(favorite);
            notifyFavoriteChanged();
        }
    }


     //切换歌曲的 “我的收藏” 状态。
    public synchronized void toggleFavorite(@NonNull Music music) {
        Objects.requireNonNull(music);
        checkThread();

        if (isFavorite(music)) {
            removeFromFavorite(music);
        } else {
            addToFavorite(music);
        }
    }

    private void notifyFavoriteChanged() {
        mMainHandler.post(() -> {
            for (OnFavoriteChangeListener listener : mAllFavoriteChangeListener) {
                listener.onFavoriteChanged();
            }
        });
    }

    private void notifyCustomMusicListUpdated(String name) {
        mMainHandler.post(() -> {
            for (OnCustomMusicListUpdateListener listener : mAllCustomMusicListUpdateListener) {
                listener.onCustomMusicListUpdate(name);
            }
        });
    }

    private void notifyCustomMusicListUpdated(List<String> nameList) {
        mMainHandler.post(() -> {
            for (String name : nameList) {
                for (OnCustomMusicListUpdateListener listener : mAllCustomMusicListUpdateListener) {
                    listener.onCustomMusicListUpdate(name);
                }
            }
        });
    }

    /**
     * 添加一个 {@link OnFavoriteChangeListener} 监听器，如果已添加，则忽略本次调用。
     *
     * @param listener {@link OnFavoriteChangeListener} 监听器对象，不能为 null
     */
    public synchronized void addOnFavoriteChangeListener(@NonNull OnFavoriteChangeListener listener) {
        Preconditions.checkNotNull(listener);

        if (mAllFavoriteChangeListener.contains(listener)) {
            return;
        }

        mAllFavoriteChangeListener.add(listener);
    }

    /**
     * 移除一个已添加的 {@link OnFavoriteChangeListener} 监听器，如果未添加或者已经移除，则忽略本次调用。
     *
     * @param listener {@link OnFavoriteChangeListener} 监听器对象，为 null 时将忽略本次调用。
     */
    public synchronized void removeOnFavoriteChangeListener(OnFavoriteChangeListener listener) {
        if (listener == null) {
            return;
        }

        mAllFavoriteChangeListener.remove(listener);
    }

    /**
     * 添加一个 {@link OnCustomMusicListUpdateListener} 监听器。
     *
     * @param listener {@link OnCustomMusicListUpdateListener} 监听器对象，不能为 null
     */
    public synchronized void addOnCustomMusicListUpdateListener(@NonNull OnCustomMusicListUpdateListener listener) {
        Preconditions.checkNotNull(listener);

        if (mAllCustomMusicListUpdateListener.contains(listener)) {
            return;
        }

        mAllCustomMusicListUpdateListener.add(listener);
    }

    /**
     * 移除一个已添加的 {@link OnCustomMusicListUpdateListener} 监听器，如果未添加或者已经移除，则忽略本次调用。
     *
     * @param listener {@link OnCustomMusicListUpdateListener} 监听器对象，为 null 时将忽略本次调用。
     */
    public synchronized void removeOnCustomMusicListUpdateListener(OnCustomMusicListUpdateListener listener) {
        if (listener == null) {
            return;
        }

        mAllCustomMusicListUpdateListener.remove(listener);
    }

    public boolean isBuiltInMusicList(@NonNull MusicList musicList) {
        String name = mMusicListEntityBox.query()
                .equal(MusicListEntity_.id, musicList.getId())
                .build()
                .property(MusicListEntity_.name)
                .unique()
                .findString();

        return isBuiltInName(name);
    }

    public static boolean isBuiltInName(String name) {
        return name.equalsIgnoreCase(MUSIC_LIST_LOCAL_MUSIC) ||
                name.equalsIgnoreCase(MUSIC_LIST_FAVORITE);
    }

    //添加一条历史记录。
    public synchronized void addHistory(@NonNull Music music) {
        Preconditions.checkNotNull(music);
        checkThread();

        HistoryEntity historyEntity = mHistoryEntityBox.query()
                .equal(HistoryEntity_.musicId, music.id)
                .build()
                .findFirst();

        if (historyEntity == null) {
            historyEntity = new HistoryEntity();
            historyEntity.music.setTarget(music);
        }
        historyEntity.timestamp = System.currentTimeMillis();

        mHistoryEntityBox.put(historyEntity);
    }

    //移除一条历史记录。
    public synchronized void removeHistory(@NonNull HistoryEntity historyEntity) {
        Preconditions.checkNotNull(historyEntity);
        checkThread();

        mHistoryEntityBox.query()
                .equal(HistoryEntity_.id, historyEntity.id)
                .build()
                .remove();
    }

    //清空历史记录。
    public synchronized void clearHistory() {
        checkThread();

        mHistoryEntityBox.query()
                .build()
                .remove();
    }

    //获取所有的历史记录。
    @NonNull
    public synchronized List<HistoryEntity> getAllHistory() {
        checkThread();

        return mHistoryEntityBox.query()
                .orderDesc(HistoryEntity_.timestamp)
                .build()
                .find();
    }

    /**
     * 存储/更新一个 {@link Music} 对象到数据库中。
     * <p>
     * <b>注意！必须先将 {@link Music} 对象存储到数据库中，然后才能添加到歌单中，否则无法保证歌单中元素的顺序</b>
     */
    public synchronized void putMusic(@NonNull Music music) {
        checkThread();
        Preconditions.checkNotNull(music);
        mMusicBox.put(music);
    }

    //获取指定 ID 的歌曲，如果歌曲不存在，则返回 null。
    @Nullable
    public synchronized Music getMusic(long id) {
        checkThread();
        return mMusicBox.get(id);
    }


    //获取在给定的 offset 偏移量和 limit 限制之间的所有音乐。
    @NonNull
    public synchronized List<Music> getAllMusic(long offset, long limit) {
        checkThread();
        return mMusicBox.query()
                .build()
                .find(offset, limit);
    }


    //存储更新多个 {@link Music} 对象到数据库中。
    public synchronized void putAllMusic(@NonNull Collection<Music> musics) {
        Preconditions.checkNotNull(musics);
        checkThread();
        mMusicBox.put(musics);
    }

    /**
     * 将列表中的全部音乐添加到具有指定名称的歌单中（包括内置歌单与自建歌单）。
     *
     * @param musicListName 歌单的名称（包括内置歌单与自建歌单），不能为 null。
     * @param allMusic      要添加到歌单中的音乐，不能为 null。
     */
    public synchronized void addAllMusic(@NonNull String musicListName, @NonNull List<Music> allMusic) {
        Preconditions.checkNotNull(musicListName);
        Preconditions.checkNotNull(allMusic);

        MusicList musicList;
        if (isBuiltInName(musicListName)) {
            musicList = getBuiltInMusicList(musicListName);
        } else {
            musicList = getCustomMusicList(musicListName);
        }

        if (musicList == null) {
            return;
        }

        musicList.getMusicElements().addAll(allMusic);
        updateMusicList(musicList);
    }

    /**
     * 从歌单中移除指定列表中的所有歌曲。
     *
     * @param musicListName 歌单名（包括内置歌单与自建歌单），不能为 null
     * @param allMusic      要移除的歌曲，不能为 null。
     */
    public synchronized void removeAllMusic(@NonNull String musicListName, @NonNull List<Music> allMusic) {
        Preconditions.checkNotNull(musicListName);
        Preconditions.checkNotNull(allMusic);

        MusicList musicList;
        if (isBuiltInName(musicListName)) {
            musicList = getBuiltInMusicList(musicListName);
        } else {
            musicList = getCustomMusicList(musicListName);
        }

        if (musicList == null) {
            return;
        }

        musicList.getMusicElements().removeAll(allMusic);
        updateMusicList(musicList);
    }

    /**
     * 获取具有指定 uri 的 {@link Music} 的 id 值。
     *
     * @param uri uri 字符串，不能为 null
     * @return 如果 {@link Music} 已存在，则返回其 id 值，否则返回 0
     */
    public synchronized long getId(@NonNull String uri) {
        Preconditions.checkNotNull(uri);

        Long id = mMusicBox.query()
                .equal(Music_.uri, uri, QueryBuilder.StringOrder.CASE_INSENSITIVE)
                .build()
                .property(Music_.id)
                .findLong();

        if (id == null) {
            return 0;
        }

        return id;
    }

    /**
     * 查询具有指定 uri 的 {@link Music} 是否已添加到 “本地音乐” 歌单中。
     *
     * @param uri uri 字符串，不能为 null
     * @return 如果歌曲已添加到本地歌单，则返回 true
     */
    public synchronized boolean isLocalMusic(@NonNull String uri) {
        Preconditions.checkNotNull(uri);

        QueryBuilder<Music> builder = mMusicBox.query()
                .equal(Music_.uri, uri, QueryBuilder.StringOrder.CASE_INSENSITIVE);

        builder.backlink(MusicListEntity_.musicElements)
                .equal(MusicListEntity_.name, MUSIC_LIST_LOCAL_MUSIC, QueryBuilder.StringOrder.CASE_SENSITIVE);

        return builder.build().count() > 0;
    }

    /**
     * 获取所有的歌手名。
     */
    @NonNull
    public synchronized List<String> getAllArtist() {
        checkThread();
        return new ArrayList<>(Arrays.asList(mMusicBox.query()
                .build()
                .property(Music_.artist)
                .distinct()
                .findStrings()));
    }

    /**
     * 获取所有的专辑名。
     */
    @NonNull
    public synchronized List<String> getAllAlbum() {
        checkThread();
        return new ArrayList<>(Arrays.asList(mMusicBox.query()
                .build()
                .property(Music_.album)
                .distinct()
                .findStrings()));
    }

    /**
     * 获取指定歌手的全部音乐。
     *
     * @param artist 歌手名，不能为 null
     * @return 歌手的全部音乐，不为 null
     */
    @NonNull
    public synchronized List<Music> getArtistAllMusic(@NonNull String artist) {
        Preconditions.checkNotNull(artist);
        checkThread();

        return mMusicBox.query()
                .equal(Music_.artist, artist, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .build()
                .find();
    }



    /**
     * 获取指定专辑的全部音乐。
     *
     * @param album 专辑名，不能为 null
     * @return 专辑中的全部音乐，不为 null
     */
    @NonNull
    public synchronized List<Music> getAlbumAllMusic(@NonNull String album) {
        Preconditions.checkNotNull(album);
        checkThread();

        return mMusicBox.query()
                .equal(Music_.album, album, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .build()
                .find();
    }

    @NonNull
    private synchronized MusicList getBuiltInMusicList(String name) {
        if (!isBuiltInName(name)) {
            throw new IllegalArgumentException("not built-in name:" + name);
        }

        MusicListEntity entity = mMusicListEntityBox.query()
                .equal(MusicListEntity_.name, name, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .build()
                .findUnique();

        if (entity != null) {
            return new MusicList(entity);
        }

        entity = createBuiltInMusicList(name);

        return new MusicList(entity);
    }

    private MusicListEntity createBuiltInMusicList(String name) {
        MusicListEntity entity = new MusicListEntity(0, name, 0, MusicList.SortOrder.BY_ADD_TIME, new byte[0]);
        mMusicListEntityBox.put(entity);
        return entity;
    }

    public void setOnScanCompleteListener(@Nullable OnScanCompleteListener listener) {
        mOnScanCompleteListener = listener;
    }

    /**
     * 通知本地音乐已扫描完成。
     */
    public void notifyScanComplete() {
        if (mOnScanCompleteListener != null) {
            mOnScanCompleteListener.onScanComplete();
        }
    }

    /**
     * 在名为 {@code musicListName} 的歌单中查找与指定 {@code key} 匹配的 {@link Music} 对象。
     * <p>
     * 只要歌曲名包含给定的 {@code key}，则就算匹配成功。
     *
     * @param musicListName 歌单名，可以是内置歌单名（如 {@link #MUSIC_LIST_LOCAL_MUSIC}、
     *                      {@link #MUSIC_LIST_FAVORITE}），不能为 null
     * @param key           搜索关键字，不能为 null
     * @return 查找结果，不为 null
     */
    @NonNull
    public List<Music> findMusicListMusic(@NonNull String musicListName, @NonNull String key) {
        Preconditions.checkNotNull(musicListName);
        Preconditions.checkNotNull(key);

        if (musicListName.isEmpty() || key.isEmpty()) {
            return Collections.emptyList();
        }

        QueryBuilder<Music> builder = mMusicBox.query();

        builder.backlink(MusicListEntity_.musicElements)
                .equal(MusicListEntity_.name, musicListName, QueryBuilder.StringOrder.CASE_SENSITIVE);

        return builder.contains(Music_.title, key, QueryBuilder.StringOrder.CASE_INSENSITIVE)
                .build()
                .find();
    }

    /**
     * 在歌手名为 {@code artistName} 的所有歌曲中查找与指定 {@code key} 匹配的 {@link Music} 对象。
     * <p>
     * 只要歌曲名包含给定的 {@code key}，则就算匹配成功。
     *
     * @param artistName 歌手名，不能为 null
     * @param key        搜索关键字，不能为 null
     * @return 查找结果，不为 null
     */
    @NonNull
    public List<Music> findArtistMusic(@NonNull String artistName, @NonNull String key) {
        Preconditions.checkNotNull(artistName);
        Preconditions.checkNotNull(key);

        if (artistName.isEmpty() || key.isEmpty()) {
            return Collections.emptyList();
        }

        return mMusicBox.query()
                .equal(Music_.artist, artistName, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .and()
                .contains(Music_.title, key, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .build()
                .find();
    }

    /**
     * 在专辑名为 {@code albumName} 的所有歌曲中查找与指定 {@code key} 匹配的 {@link Music} 对象。
     * <p>
     * 只要歌曲名包含给定的 {@code key}，则就算匹配成功。
     *
     * @param albumName 专辑名，不能为 null
     * @param key       搜索关键字，不能为 null
     * @return 查找结果，不为 null
     */
    @NonNull
    public List<Music> findAlbumMusic(@NonNull String albumName, @NonNull String key) {
        Preconditions.checkNotNull(albumName);
        Preconditions.checkNotNull(key);

        if (albumName.isEmpty() || key.isEmpty()) {
            return Collections.emptyList();
        }

        return mMusicBox.query()
                .equal(Music_.album, albumName, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .and()
                .contains(Music_.title, key, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .build()
                .find();
    }

    /**
     * 用于监听 “我的收藏” 歌单的修改事件。
     * <p>
     * 当往 “我的收藏” 歌单中添加或移除一首歌曲时，该监听器会被调用。
     */
    public interface OnFavoriteChangeListener {
        /**
         * 当 “我的收藏” 歌单被修改时，会调用该方法。
         * <p>
         * 该回调方法会在应用程序主线程调用。
         */
        void onFavoriteChanged();
    }

    /**
     * 监听 “本地音乐扫描完成” 事件。
     */
    public interface OnScanCompleteListener {
        /**
         * 本地音乐扫描完成时会调用该方法。
         * <p>
         * 该方法会在主线程中调用，请不要直接在该方法中访问数据库。
         *
         * @see #notifyFavoriteChanged()
         */
        void onScanComplete();
    }

    /**
     * 监听 “排序歌单” 完成事件。
     */
    public interface SortCallback {
        /**
         * 歌单排序完成后会调用该方法，且会在主线程中调用该方法。
         */
        void onSortFinished();
    }

    /**
     * 监听自建歌单更新事件。
     */
    public interface OnCustomMusicListUpdateListener {
        /**
         * 当某个自建歌单被更新时将调用该方法。
         * <p>
         * 该方法会在主线程上调用，请不要在该方法中执行耗时操作。
         *
         * @param name 被更新的自建歌单的名称。
         */
        void onCustomMusicListUpdate(String name);
    }
}
