package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.databinding.AdapterSearchRecordBinding;
import com.fongmi.android.tv.setting.Setting;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.List;

public class RecordAdapter extends RecyclerView.Adapter<RecordAdapter.ViewHolder> {

    private final OnClickListener listener;
    private final List<String> mItems;

    public RecordAdapter(OnClickListener listener) {
        this.listener = listener;
        this.mItems = getItems();
        this.listener.onDataChanged(mItems.size(), RecyclerView.NO_POSITION);
    }

    public interface OnClickListener {

        void onItemClick(String text);

        void onDataChanged(int size, int deletedPosition);
    }

    private List<String> getItems() {
        if (Setting.getKeyword().isEmpty()) return new ArrayList<>();
        return App.gson().fromJson(Setting.getKeyword(), TypeToken.getParameterized(List.class, String.class).getType());
    }

    private void checkToAdd(String item) {
        mItems.remove(item);
        mItems.add(0, item);
        if (mItems.size() > 9) mItems.remove(9);
    }

    public void add(String item) {
        checkToAdd(item);
        Setting.putKeyword(App.gson().toJson(mItems));
        notifyDataSetChanged();
        listener.onDataChanged(getItemCount(), RecyclerView.NO_POSITION);
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterSearchRecordBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.binding.text.setText(mItems.get(position));
        holder.binding.text.setOnClickListener(v -> {
            int adapterPosition = holder.getBindingAdapterPosition();
            if (isValidPosition(adapterPosition)) listener.onItemClick(mItems.get(adapterPosition));
        });
    }

    private boolean isValidPosition(int position) {
        return position >= 0 && position < mItems.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnLongClickListener {

        private final AdapterSearchRecordBinding binding;

        public ViewHolder(@NonNull AdapterSearchRecordBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            itemView.setOnLongClickListener(this);
        }

        @Override
        public boolean onLongClick(View v) {
            int position = getBindingAdapterPosition();
            if (!isValidPosition(position)) return true;
            mItems.remove(position);
            notifyItemRemoved(position);
            listener.onDataChanged(getItemCount(), position);
            Setting.putKeyword(App.gson().toJson(mItems));
            return true;
        }
    }
}
