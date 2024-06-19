package butterfly.music.activity.detail.album;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;

import io.reactivex.Single;
import io.reactivex.SingleOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import butterfly.music.R;
import butterfly.music.dialog.BottomDialog;
import butterfly.music.dialog.BottomMenuDialog;
import butterfly.music.fragment.musiclist.BaseMusicListFragment;
import butterfly.music.fragment.musiclist.BaseMusicListViewModel;
import butterfly.music.store.Music;
import butterfly.music.store.MusicStore;

public class AlbumDetailFragment extends BaseMusicListFragment {
    private static final String KEY_ALBUM = "ALBUM";

    private Disposable mCheckFavoriteDisposable;

    public static AlbumDetailFragment newInstance(String album) {
        AlbumDetailFragment fragment = new AlbumDetailFragment();
        Bundle args = new Bundle();
        args.putString(KEY_ALBUM, album);

        fragment.setArguments(args);
        return fragment;
    }

    private String getAlbum() {
        Bundle args = getArguments();
        if (args == null) {
            return "";
        }
        return args.getString(KEY_ALBUM, "");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        disposeCheckFavorite();
    }

    @Override
    protected BaseMusicListViewModel onCreateMusicListViewModel(ViewModelProvider viewModelProvider) {
        AlbumDetailViewModel viewModel = viewModelProvider.get(AlbumDetailViewModel.class);
        viewModel.init(getAlbum());
        return viewModel;
    }

    @Override
    protected void showItemOptionMenu(@NonNull Music music) {
        disposeCheckFavorite();
        // 创建一个异步任务来检查音乐是否被收藏
        mCheckFavoriteDisposable = Single.create((SingleOnSubscribe<Boolean>) emitter -> {
                    boolean result = MusicStore.getInstance().isFavorite(music); // 检查音乐是否被收藏
                    if (emitter.isDisposed()) {
                        return;
                    }
                    emitter.onSuccess(result); // 发出成功的信号
                }).subscribeOn(Schedulers.io()) // 在IO线程上执行任务
                .observeOn(AndroidSchedulers.mainThread()) // 在主线程上处理结果
                .subscribe(favorite -> showItemOptionMenu(favorite, music)); // 显示选项菜单
    }

    private void disposeCheckFavorite() {
        // 如果检查收藏状态的任务还没有完成，就释放它
        if (mCheckFavoriteDisposable != null && !mCheckFavoriteDisposable.isDisposed()) {
            mCheckFavoriteDisposable.dispose();
        }
    }

    private void showItemOptionMenu(boolean favorite, @NonNull Music music) {
        // 根据音乐是否被收藏，选择不同的图标和标题
        int favoriteIconRes = favorite ? R.drawable.ic_menu_item_favorite_true : R.drawable.ic_menu_item_favorite_false;
        int favoriteTitleRes = favorite ? R.string.menu_item_remove_from_favorite : R.string.menu_item_add_to_favorite;

        // 创建一个底部对话框来显示选项菜单
        BottomDialog bottomDialog = new BottomMenuDialog.Builder(requireContext())
                .setTitle(music.getTitle()) // 设置标题为音乐的标题
                .addMenuItem(R.drawable.ic_menu_item_next_play, R.string.menu_item_next_play)
                .addMenuItem(R.drawable.ic_menu_item_add, R.string.menu_item_add_to_music_list)
                .addMenuItem(favoriteIconRes, favoriteTitleRes)
                .addMenuItem(R.drawable.ic_menu_item_rington, R.string.menu_item_set_as_ringtone)
                .setOnMenuItemClickListener((dialog, position) -> {
                    dialog.dismiss(); // 关闭对话框
                    switch (position) {
                        case 0:
                            setNextPlay(music);
                            break;
                        case 1:
                            if (favorite) {
                                removeFavorite(music);
                            } else {
                                addToFavorite(music);
                            }
                            break;
                        case 2:
                            addToMusicList(music); // 将音乐添加到音乐列表
                            break;
                        case 3:
                            setAsRingtone(music); // 将音乐设置为铃声
                            break;
                    }
                })
                .build(); // 创建对话框
        bottomDialog.show(getParentFragmentManager(), "showItemOptionMenu");
    }
}
