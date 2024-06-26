package butterfly.music.activity.browser.album;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.common.base.Preconditions;

import java.util.ArrayList;
import java.util.List;

import recyclerview.helper.ItemClickHelper;
import recyclerview.helper.SelectableHelper;
import butterfly.music.R;

public class AlbumBrowserAdapter extends RecyclerView.Adapter<AlbumBrowserAdapter.ViewHolder> {
    private static final int TYPE_EMPTY = 1;
    private static final int TYPE_ITEM = 2;

    private List<String> mAllAlbum;
    private final ItemClickHelper mItemClickHelper;
    private final SelectableHelper mSelectableHelper;

    public AlbumBrowserAdapter(@NonNull List<String> allAlbum) {
        Preconditions.checkNotNull(allAlbum);
        mAllAlbum = new ArrayList<>(allAlbum);
        mItemClickHelper = new ItemClickHelper();
        mSelectableHelper = new SelectableHelper(this);
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setAllAlbum(List<String> allAlbum) {
        mAllAlbum = new ArrayList<>(allAlbum);
        notifyDataSetChanged();
    }

    public void setOnItemClickListener(ItemClickHelper.OnItemClickListener listener) {
        mItemClickHelper.setOnItemClickListener(listener);
    }

    public void setMarkPosition(int position) {
        if (position < 0) {
            mSelectableHelper.clearSelected();
            return;
        }

        mSelectableHelper.setSelect(position, true);
    }

    public void clearMark() {
        mSelectableHelper.clearSelected();
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        // 当RecyclerView与适配器关联时，也将ItemClickHelper和SelectableHelper与RecyclerView关联
        mItemClickHelper.attachToRecyclerView(recyclerView);
        mSelectableHelper.attachToRecyclerView(recyclerView);
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
        // 当RecyclerView与适配器解除关联时，也将ItemClickHelper和SelectableHelper与RecyclerView解除关联
        mItemClickHelper.detach();
        mSelectableHelper.detach();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // 根据viewType选择不同的布局
        int layoutId = viewType == TYPE_EMPTY ? R.layout.empty_album_browser : R.layout.item_album_browser;
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(layoutId, parent, false);
        return new ViewHolder(itemView, viewType == TYPE_EMPTY);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        if (holder.empty) {
            return;
        }
        // 设置专辑名称
        holder.tvAlbum.setText(mAllAlbum.get(position));
        // 绑定点击事件和选择状态
        mItemClickHelper.bindClickListener(holder.itemView);
        mSelectableHelper.updateSelectState(holder, position);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder
            implements SelectableHelper.Selectable {
        public final boolean empty;
        public TextView tvAlbum;
        public View mark;

        public ViewHolder(@NonNull View itemView, boolean empty) {
            super(itemView);

            this.empty = empty;
            if (empty) {
                return;
            }
            tvAlbum = itemView.findViewById(R.id.tvAlbum);
            mark = itemView.findViewById(R.id.mark);
        }

        @Override
        public void onSelected() {
            mark.setVisibility(View.VISIBLE);
        }

        @Override
        public void onUnselected() {
            mark.setVisibility(View.GONE);
        }
    }
    @Override
    public int getItemCount() {
        if (mAllAlbum.isEmpty()) {
            return 1;
        }

        return mAllAlbum.size();
    }
    @Override
    public int getItemViewType(int position) {
        if (mAllAlbum.isEmpty()) {
            return TYPE_EMPTY;
        }
        return TYPE_ITEM;
    }
}
