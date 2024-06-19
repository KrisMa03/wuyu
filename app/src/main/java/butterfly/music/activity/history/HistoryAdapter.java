package butterfly.music.activity.history;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.google.common.base.Preconditions;

import java.util.ArrayList;
import java.util.List;

import recyclerview.helper.ItemClickHelper;
import butterfly.music.R;
import butterfly.music.store.HistoryEntity;
import butterfly.music.store.Music;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {
    private static final int TYPE_EMPTY_VIEW = 1;
    private static final int TYPE_ITEM_VIEW = 2;

    private List<HistoryEntity> mHistory;
    private final ItemClickHelper mItemClickHelper;
    private OnItemClickListener mOnItemClickListener;

    public HistoryAdapter(@NonNull List<HistoryEntity> history) {
        mHistory = new ArrayList<>(history);
        mItemClickHelper = new ItemClickHelper();
        mItemClickHelper.setOnItemClickListener((position, viewId, view, holder) -> {
            if (mOnItemClickListener == null) {
                return;
            }
            if (viewId == R.id.historyItem) {
                mOnItemClickListener.onItemClicked(position, mHistory.get(position));
            } else if (viewId == R.id.btnRemove) {
                mOnItemClickListener.onRemoveClicked(position, mHistory.get(position));
            }
        });
    }
    // 设置历史记录列表，并通知数据变化
    public void setHistory(@NonNull List<HistoryEntity> history) {
        Preconditions.checkNotNull(history);
        if (mHistory.isEmpty() || history.isEmpty()) {
            mHistory = new ArrayList<>(history);
            notifyDataSetChanged();
            return;
        }
        DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return mHistory.size();
            }
            @Override
            public int getNewListSize() {
                return history.size();
            }
            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                Music oldMusic = mHistory.get(oldItemPosition).getMusic();
                Music newMusic = history.get(newItemPosition).getMusic();
                return oldMusic.equals(newMusic);
            }
            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                return areItemsTheSame(oldItemPosition, newItemPosition);
            }
        });
        result.dispatchUpdatesTo(this);
        mHistory = new ArrayList<>(history);
    }

    // 设置列表项点击监听器
    public void setOnItemClickListener(OnItemClickListener itemClickListener) {
        mOnItemClickListener = itemClickListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layoutId = R.layout.item_history;
        boolean emptyView = (viewType == TYPE_EMPTY_VIEW);
        if (emptyView) {
            layoutId = R.layout.empty_history;
        }

        return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(layoutId, parent, false), emptyView);
    }

    // 绑定ViewHolder，设置标题和艺术家-专辑文本，绑定点击事件
    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        if (holder.emptyView) {
            return;
        }
        HistoryEntity historyEntity = mHistory.get(position);
        Music music = historyEntity.getMusic();
        holder.tvTitle.setText(music.getTitle());
        holder.tvArtistAndAlbum.setText(music.getArtist() + " - " + music.getAlbum());
        mItemClickHelper.bindClickListener(holder.itemView, holder.btnRemove);
    }
    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        mItemClickHelper.attachToRecyclerView(recyclerView);
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
        mItemClickHelper.detach();
    }

    @Override
    public int getItemCount() {
        if (mHistory.isEmpty()) {
            return 1;
        }

        return mHistory.size();
    }
    // 获取列表项视图类型，如果历史记录为空，则返回空视图类型
    @Override
    public int getItemViewType(int position) {
        if (mHistory.isEmpty()) {
            return TYPE_EMPTY_VIEW;
        }
        return TYPE_ITEM_VIEW;
    }

    // 列表项点击监听器接口，包含列表项点击和移除点击两种事件
    public interface OnItemClickListener {
        void onItemClicked(int position, @NonNull HistoryEntity historyEntity);
        void onRemoveClicked(int position, @NonNull HistoryEntity historyEntity);
    }
    public static class ViewHolder extends RecyclerView.ViewHolder {
        final boolean emptyView;

        TextView tvTitle;
        TextView tvArtistAndAlbum;
        ImageButton btnRemove;

        public ViewHolder(@NonNull View itemView, boolean emptyView) {
            super(itemView);

            this.emptyView = emptyView;
            if (emptyView) {
                return;
            }

            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvArtistAndAlbum = itemView.findViewById(R.id.tvArtistAndAlbum);
            btnRemove = itemView.findViewById(R.id.btnRemove);
        }
    }

}
