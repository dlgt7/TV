package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.WindowManager;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.GridLayoutManager;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogMenuBinding;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.adapter.MenuAdapter;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MenuKeyDialog implements MenuAdapter.OnClickListener {

    private final DialogMenuBinding binding;
    private final Listener callback;
    private final MenuAdapter adapter;
    private final AlertDialog dialog;

    public interface Listener {

        void onMenuKeyItemClick(int position);
    }

    public static MenuKeyDialog create(Activity activity) {
        return new MenuKeyDialog(activity);
    }

    public MenuKeyDialog(Activity activity) {
        String[] items = ResUtil.getStringArray(R.array.select_home_menu_key);
        List<String> mItems = new ArrayList<>(Arrays.asList(items));
        this.callback = (Listener) activity;
        this.adapter = new MenuAdapter(this, mItems);
        this.binding = DialogMenuBinding.inflate(LayoutInflater.from(activity));
        this.dialog = new MaterialAlertDialogBuilder(activity).setView(binding.getRoot()).create();
    }

    public void show() {
        setRecyclerView();
        setDialog();
    }

    private void setRecyclerView() {
        binding.recycler.setAdapter(adapter);
        binding.recycler.setHasFixedSize(true);
        binding.recycler.setItemAnimator(null);
        binding.recycler.addItemDecoration(new SpaceItemDecoration(3, 16));
        binding.recycler.setLayoutManager(new GridLayoutManager(dialog.getContext(), 3));
        binding.recycler.post(() -> binding.recycler.scrollToPosition(Setting.getHomeMenuKey()));
    }

    private void setDialog() {
        WindowManager.LayoutParams params = dialog.getWindow().getAttributes();
        params.width = (int) (ResUtil.getScreenWidth() * 0.8f);
        dialog.getWindow().setAttributes(params);
        dialog.getWindow().setDimAmount(0);
        dialog.show();
    }

    @Override
    public void onItemClick(int position) {
        if (dialog != null) dialog.dismiss();
        callback.onMenuKeyItemClick(position);
    }
}