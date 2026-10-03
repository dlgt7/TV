package com.fongmi.android.tv.ui.dialog;

import android.content.DialogInterface;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;

/** Material side sheet with the same inset card bounds as the TV command drawer. */
public abstract class BasePlaybackSideSheetDialog extends BaseSideSheetDialog {
    public interface Listener {
        void onPlaybackPanelClosed();
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        if (getActivity() instanceof Listener listener) listener.onPlaybackPanelClosed();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (!Util.isLeanback() || getDialog() == null) return;
        FrameLayout sheet = getDialog().findViewById(com.google.android.material.R.id.m3_side_sheet);
        if (sheet == null) return;
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) sheet.getLayoutParams();
        params.width = Math.min(ResUtil.dp2px(320), ResUtil.getScreenWidth() - ResUtil.dp2px(48));
        params.height = ViewGroup.LayoutParams.MATCH_PARENT;
        params.setMargins(0, ResUtil.dp2px(48), ResUtil.dp2px(24), ResUtil.dp2px(48));
        sheet.setLayoutParams(params);
    }
}
