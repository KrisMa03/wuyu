package butterfly.music.activity.browser.album;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import io.reactivex.Single;
import io.reactivex.SingleOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import pinyin.util.PinyinComparator;
import butterfly.music.store.MusicStore;

public class AlbumBrowserViewModel extends ViewModel {
    private final MutableLiveData<List<String>> mAllAlbum;
    private Disposable mLoadAllAlbumDisposable;

    public AlbumBrowserViewModel() {
        mAllAlbum = new MutableLiveData<>(Collections.emptyList());
        loadAllAlbum();
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (mLoadAllAlbumDisposable != null && !mLoadAllAlbumDisposable.isDisposed()) {
            mLoadAllAlbumDisposable.dispose();
        }
    }

    private void loadAllAlbum() {
        // 创建一个异步任务来加载所有的专辑
        mLoadAllAlbumDisposable = Single.create((SingleOnSubscribe<List<String>>) emitter -> {
                    // 从音乐存储中获取所有的专辑
                    List<String> allAlbum = MusicStore.getInstance().getAllAlbum();
                    Collections.sort(allAlbum, new PinyinComparator());
                    if (emitter.isDisposed()) {
                        return;
                    }
                    emitter.onSuccess(allAlbum);
                }).subscribeOn(Schedulers.io()) // 在IO线程上执行任务
                .observeOn(AndroidSchedulers.mainThread()) // 在主线程上处理结果
                .subscribe(mAllAlbum::setValue); // 更新LiveData中的专辑列表
    }
    public LiveData<List<String>> getAllAlbum() {
        return mAllAlbum;
    }

    public String getAlbum(int position) {
        return Objects.requireNonNull(mAllAlbum.getValue()).get(position);
    }
}
