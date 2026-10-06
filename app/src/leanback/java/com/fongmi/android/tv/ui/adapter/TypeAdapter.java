package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Cache;
import com.fongmi.android.tv.bean.Class;
import com.fongmi.android.tv.databinding.AdapterTypeBinding;
import com.fongmi.android.tv.ui.custom.JetStreamTypeTabIndicator;
import com.fongmi.android.tv.utils.ResUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public class TypeAdapter extends RecyclerView.Adapter<TypeAdapter.ViewHolder> {

    private final OnClickListener mListener;
    private final List<Class> mItems;
    private final Map<RecyclerView, JetStreamTypeTabIndicator> mIndicators = new WeakHashMap<>();

    public TypeAdapter(OnClickListener listener) {
        mListener = listener;
        mItems = new ArrayList<>();
    }

    public void addAll(List<Class> items) {
        mItems.clear();
        mItems.addAll(items);
        notifyDataSetChanged();
    }

    public Class get(int position) {
        if (position < 0 || position >= mItems.size()) return new Class();
        return mItems.get(position);
    }

    public int indexOf(Class item) {
        return mItems.indexOf(item);
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterTypeBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Class item = mItems.get(position);
        holder.binding.text.setText(item.getTypeName());
        holder.binding.text.setCompoundDrawablePadding(ResUtil.dp2px(4));
        holder.binding.text.setCompoundDrawablesWithIntrinsicBounds(0, 0, getIcon(item), 0);
        holder.binding.text.setListener(() -> {
            int adapterPosition = holder.getBindingAdapterPosition();
            if (isValidPosition(adapterPosition)) mListener.onRefresh(mItems.get(adapterPosition));
        });
        holder.binding.getRoot().setOnClickListener(v -> {
            int adapterPosition = holder.getBindingAdapterPosition();
            if (isValidPosition(adapterPosition)) mListener.onItemClick(mItems.get(adapterPosition));
        });
    }

    private int getIcon(Class item) {
        return Cache.get(item).isEmpty() ? 0 : item.getFilter() ? R.drawable.ic_vod_filter_off : R.drawable.ic_vod_filter_on;
    }

    private boolean isValidPosition(int position) {
        return position >= 0 && position < mItems.size();
    }

    public interface OnClickListener {

        void onItemClick(Class item);

        void onRefresh(Class item);
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        JetStreamTypeTabIndicator indicator = new JetStreamTypeTabIndicator();
        mIndicators.put(recyclerView, indicator);
        recyclerView.addItemDecoration(indicator);
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        JetStreamTypeTabIndicator indicator = mIndicators.remove(recyclerView);
        if (indicator != null) {
            indicator.dispose();
            recyclerView.removeItemDecoration(indicator);
        }
        super.onDetachedFromRecyclerView(recyclerView);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterTypeBinding binding;

        ViewHolder(@NonNull AdapterTypeBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
