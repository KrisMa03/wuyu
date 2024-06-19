package butterfly.music.activity.history;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.common.base.Preconditions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import io.reactivex.Single;
import io.reactivex.SingleOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import butterfly.music.store.HistoryEntity;
import butterfly.music.store.Music;
import butterfly.music.store.MusicStore;

public class HistoryViewModel extends ViewModel {
    private final MutableLiveData<List<HistoryEntity>> mHistory;
    private Disposable mLoadHistoryDisposable;

    public HistoryViewModel() {
        mHistory = new MutableLiveData<>(Collections.emptyList());
        loadHistory();
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (mLoadHistoryDisposable != null && !mLoadHistoryDisposable.isDisposed()) {
            mLoadHistoryDisposable.dispose();
        }
    }

    public LiveData<List<HistoryEntity>> getHistory() {
        return mHistory;
    }
    @NonNull
    public List<Music> getAllHistoryMusic() {
        List<HistoryEntity> history = mHistory.getValue();
        assert history != null;

        List<Music> musicList = new ArrayList<>(history.size());

        for (HistoryEntity entity : history) {
            musicList.add(entity.getMusic());
        }

        return musicList;
    }

    private void loadHistory() {
        // 创建一个异步任务来加载所有的历史
        mLoadHistoryDisposable = Single.create((SingleOnSubscribe<List<HistoryEntity>>) emitter -> {
                    List<HistoryEntity> history = MusicStore.getInstance().getAllHistory();
                    if (emitter.isDisposed()) {
                        return;
                    }
                    emitter.onSuccess(history);
                }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(mHistory::setValue); // 更新历史列表
    }
    public void clearHistory() {
        mHistory.setValue(Collections.emptyList());
        // 创建一个异步任务来清空音乐存储中的所有历史
        Single.create((SingleOnSubscribe<Boolean>) emitter -> MusicStore.getInstance().clearHistory())
                .subscribeOn(Schedulers.io())
                .subscribe();
    }

    public void removeHistory(@NonNull HistoryEntity historyEntity) {
        Preconditions.checkNotNull(historyEntity);
        // 获取当前的历史列表
        List<HistoryEntity> history = Objects.requireNonNull(mHistory.getValue());
        history.remove(historyEntity);
        // 更新历史列表
        mHistory.setValue(history);
        // 创建一个异步任务来从音乐存储中移除指定的历史实体
        Single.create((SingleOnSubscribe<Boolean>) emitter -> MusicStore.getInstance().removeHistory(historyEntity))
                .subscribeOn(Schedulers.io())
                .subscribe();
    }

}
