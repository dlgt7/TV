package com.fongmi.android.tv.ui.dialog;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.DanmakuApi;
import com.fongmi.android.tv.bean.Danmaku;
import com.fongmi.android.tv.databinding.DialogDanmakuSearchBinding;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.ui.adapter.DanmakuAdapter;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.utils.KeyUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;

import java.util.List;

public final class DanmakuSearchDialog {

    private PlayerManager player;

    public static DanmakuSearchDialog create() {
        return new DanmakuSearchDialog();
    }

    public DanmakuSearchDialog player(PlayerManager player) {
        this.player = player;
        return this;
    }

    public void show(FragmentActivity activity) {
        FragmentManager manager = activity.getSupportFragmentManager();
        for (Fragment f : manager.getFragments()) if (f instanceof BottomSheet || f instanceof SideSheet) return;
        if (Util.isFullscreenLand(activity) || Util.isLeanback()) new SideSheet(player).show(manager, null);
        else new BottomSheet(player).show(manager, null);
    }

    private static DialogDanmakuSearchBinding inflate(LayoutInflater inflater, ViewGroup container) {
        return DialogDanmakuSearchBinding.inflate(inflater, container, false);
    }

    public static final class BottomSheet extends BaseBottomSheetDialog {

        private final PlayerManager player;
        private DialogDanmakuSearchBinding binding;
        private Panel panel;

        BottomSheet(PlayerManager player) {
            this.player = player;
        }

        @Override
        protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
            return binding = DanmakuSearchDialog.inflate(inflater, container);
        }

        @Override
        protected void initView() {
            panel = new Panel(binding, player, this::dismiss);
            panel.initView();
        }

        @Override
        protected void initEvent() {
            panel.initEvent();
        }

        @Override
        public void onStart() {
            super.onStart();
            if (panel != null) panel.showKeyboard();
        }

        @Override
        public void onDestroyView() {
            if (panel != null) panel.onDestroyView();
            super.onDestroyView();
        }
    }

    public static final class SideSheet extends BasePlaybackSideSheetDialog {

        private final PlayerManager player;
        private DialogDanmakuSearchBinding binding;
        private Panel panel;

        SideSheet(PlayerManager player) {
            this.player = player;
        }

        @Override
        protected int getWidth() {
            return Math.min(ResUtil.dp2px(420), ResUtil.getScreenWidth() / 2);
        }

        @Override
        protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
            return binding = DanmakuSearchDialog.inflate(inflater, container);
        }

        @Override
        protected void initView() {
            panel = new Panel(binding, player, this::dismiss);
            panel.initView();
        }

        @Override
        protected void initEvent() {
            panel.initEvent();
        }

        @Override
        public void onStart() {
            super.onStart();
            if (panel != null) panel.showKeyboard();
        }

        @Override
        public void onDestroyView() {
            if (panel != null) panel.onDestroyView();
            super.onDestroyView();
        }
    }

    private static final class Panel implements DanmakuAdapter.OnClickListener {

        private final DialogDanmakuSearchBinding binding;
        private final PlayerManager player;
        private final DanmakuAdapter adapter;
        private final Runnable dismiss;

        private Panel(DialogDanmakuSearchBinding binding, PlayerManager player, Runnable dismiss) {
            this.binding = binding;
            this.player = player;
            this.dismiss = dismiss;
            this.adapter = new DanmakuAdapter(this);
        }

        private void initView() {
            binding.recycler.setAdapter(adapter);
            binding.recycler.setItemAnimator(null);
            binding.recycler.setHasFixedSize(false);
            binding.recycler.addItemDecoration(new SpaceItemDecoration(1, 16));
            setKeyword(player == null || player.getMetadata() == null ? "" : player.getMetadata().title);
            updateKeywordDownFocus(false);
            showKeyboard();
        }

        private void initEvent() {
            binding.keyword.setOnClickListener(Util::showKeyboard);
            binding.keyword.setOnEditorActionListener((textView, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_SEARCH && !getKeyword().isEmpty()) search();
                return true;
            });
            binding.keyword.setOnKeyListener((view, keyCode, event) -> {
                if (KeyUtil.isActionDown(event) && KeyUtil.isDownKey(event)) return focusFirstResult();
                return false;
            });
        }

        @Override
        public void onItemClick(Danmaku item) {
            if (player != null) player.setDanmaku(item.isSelected() ? Danmaku.empty() : item);
            dismiss.run();
        }

        private void setKeyword(CharSequence text) {
            CharSequence keyword = text == null ? "" : text;
            binding.keyword.setText(keyword);
            binding.keyword.setSelection(keyword.length());
        }

        private void showKeyboard() {
            Util.showKeyboard(binding.keyword);
        }

        private String getKeyword() {
            return binding.keyword.getText() == null ? "" : binding.keyword.getText().toString().trim();
        }

        private void showProgress() {
            boolean restoreFocus = binding.recycler.hasFocus();
            binding.recycler.setVisibility(GONE);
            binding.progress.setVisibility(VISIBLE);
            updateKeywordDownFocus(false);
            if (restoreFocus) requestKeywordFocus();
        }

        private void hideProgress(boolean empty) {
            boolean restoreFocus = empty && binding.recycler.hasFocus();
            binding.progress.setVisibility(GONE);
            binding.recycler.setVisibility(empty ? GONE : VISIBLE);
            updateKeywordDownFocus(!empty);
            if (restoreFocus) requestKeywordFocus();
        }

        private void search() {
            showProgress();
            adapter.clear();
            Util.hideKeyboard(binding.keyword);
            DanmakuApi.searchManual(getKeyword(), getEpisode(), new DanmakuApi.SearchCallback() {
                @Override
                public void onSuccess(List<Danmaku> items) {
                    if (items.isEmpty()) onError(new Exception(ResUtil.getString(R.string.error_empty)));
                    else Panel.this.onSuccess(items);
                }

                @Override
                public void onError(Exception e) {
                    Panel.this.onError(e);
                }
            });
        }

        private String getEpisode() {
            return player == null || player.getMetadata() == null || player.getMetadata().artist == null ? "" : player.getMetadata().artist.toString().trim();
        }

        private void onSuccess(List<Danmaku> items) {
            adapter.addAll(items);
            hideProgress(items.isEmpty());
            DialogFocus.requestRecyclerFocus(binding.recycler, 0, adapter.getItemCount());
        }

        private boolean focusFirstResult() {
            if (binding.recycler.getVisibility() != VISIBLE || adapter.getItemCount() == 0) return false;
            DialogFocus.requestRecyclerFocus(binding.recycler, 0, adapter.getItemCount());
            return true;
        }

        private void updateKeywordDownFocus(boolean hasResults) {
            binding.keyword.setNextFocusDownId(hasResults ? binding.recycler.getId() : binding.keyword.getId());
        }

        private void requestKeywordFocus() {
            binding.keyword.post(() -> {
                if (canRequestFocus(binding.keyword)) binding.keyword.requestFocus();
            });
        }

        private boolean canRequestFocus(View view) {
            return view != null && view.isShown() && view.isEnabled();
        }

        private void onError(Exception e) {
            hideProgress(true);
            Notify.show(e.getMessage());
        }

        private void onDestroyView() {
            Util.hideKeyboard(binding.keyword);
            DanmakuApi.cancel();
        }
    }
}
