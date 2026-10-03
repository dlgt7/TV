package com.fongmi.android.tv.ui.dialog;

/** Platform presentation for playback choices; selection logic stays shared. */
public abstract class BasePlaybackSelectionDialog extends BasePlaybackSideSheetDialog {
    @Override
    protected int getWidth() {
        return com.fongmi.android.tv.utils.ResUtil.dp2px(320);
    }
}
