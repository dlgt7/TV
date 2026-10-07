package com.fongmi.android.tv.ui.presenter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.leanback.widget.Presenter;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.DiscoverFilterOption;
import com.fongmi.android.tv.bean.DiscoverFilterPanel;
import com.fongmi.android.tv.databinding.AdapterDiscoverFilterPanelBinding;
import com.fongmi.android.tv.ui.custom.JetStreamChipRow;

import java.util.ArrayList;
import java.util.List;

public final class DiscoverFilterPanelPresenter extends Presenter {

    public interface Listener {
        void onFilterGroupClick(int row);
        void onFilterClick(int row, DiscoverFilterOption option);
    }

    private final Listener listener;

    public DiscoverFilterPanelPresenter(Listener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent) {
        return new Holder(AdapterDiscoverFilterPanelBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder viewHolder, Object object) {
        ((Holder) viewHolder).bind((DiscoverFilterPanel) object, listener);
    }

    @Override
    public void onUnbindViewHolder(@NonNull ViewHolder viewHolder) {
        ((Holder) viewHolder).unbind();
    }

    private static final class Holder extends ViewHolder {

        private final AdapterDiscoverFilterPanelBinding binding;

        private Holder(AdapterDiscoverFilterPanelBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        private void bind(DiscoverFilterPanel panel, Listener listener) {
            int rowCount = panel.getRowCount();
            List<String> summaries = new ArrayList<>();
            for (int row = 0; row < rowCount; row++) summaries.add(summaryLabel(binding.getRoot().getContext(), row, selected(panel.getRow(row))));
            int expanded = panel.getExpandedRow();
            int focused = binding.summary.getFocusedPosition();
            binding.summary.setItems(summaries, expanded);
            if (focused >= 0) binding.summary.setFocusedPosition(Math.min(focused, rowCount - 1));
            binding.summary.setOnChipClickListener(listener::onFilterGroupClick);
            binding.summary.setNextFocusLeftId(binding.summary.getId());
            binding.summary.setNextFocusRightId(binding.summary.getId());
            binding.options.setNextFocusLeftId(binding.options.getId());
            binding.options.setNextFocusRightId(binding.options.getId());

            boolean expandedVisible = expanded >= 0 && expanded < rowCount;
            binding.optionsContainer.setVisibility(expandedVisible ? View.VISIBLE : View.GONE);
            if (!expandedVisible) {
                binding.options.clearListeners();
                return;
            }
            List<DiscoverFilterOption> options = panel.getRow(expanded);
            List<String> labels = new ArrayList<>();
            int selected = -1;
            for (int i = 0; i < options.size(); i++) {
                labels.add(options.get(i).getLabel());
                if (options.get(i).isSelected()) selected = i;
            }
            binding.optionTitle.setText(groupName(binding.getRoot().getContext(), expanded));
            binding.options.setItems(labels, selected);
            int row = expanded;
            binding.options.setOnChipClickListener(position -> {
                if (position >= 0 && position < options.size()) listener.onFilterClick(row, options.get(position));
            });
        }

        private String summaryLabel(Context context, int row, DiscoverFilterOption selected) {
            String group = groupName(context, row);
            if (selected == null || row <= 1) return selected == null ? group : selected.getLabel();
            if ((row == 2 || row == 3) && selected.getValue().isEmpty()) return group;
            if (row == 6 && selected.getValue().isEmpty()) return group;
            if (row == 4 && selected.getStartDate().isEmpty() && selected.getEndDate().isEmpty()) return group;
            return group + " · " + selected.getLabel();
        }

        private DiscoverFilterOption selected(List<DiscoverFilterOption> options) {
            for (DiscoverFilterOption option : options) if (option.isSelected()) return option;
            return null;
        }

        private String groupName(Context context, int row) {
            int resource = switch (row) {
                case 0 -> R.string.discover_filter_library;
                case 1 -> R.string.discover_filter_media;
                case 2 -> R.string.discover_filter_genre;
                case 3 -> R.string.discover_filter_region;
                case 4 -> R.string.discover_filter_year;
                case 6 -> R.string.discover_filter_platform;
                default -> R.string.discover_filter_sort;
            };
            return context.getString(resource);
        }

        private void unbind() {
            binding.summary.clearListeners();
            binding.options.clearListeners();
        }
    }
}
