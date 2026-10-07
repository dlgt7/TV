package com.fongmi.android.tv.ui.dialog;

import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.source.health.SourceHealthManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

final class SourceHealthDialog {
    private SourceHealthDialog() {}

    static void show(FragmentActivity activity) {
        List<SourceHealthManager.SiteHealth> entries = SourceHealthManager.snapshot(VodConfig.get().getSites());
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity).setTitle(R.string.maintenance_health);
        if (entries.isEmpty()) builder.setMessage(R.string.maintenance_health_empty);
        else {
            String[] labels = new String[entries.size()];
            for (int i = 0; i < labels.length; i++) labels[i] = entries.get(i).getSite().getName() + "\n" + entries.get(i).getLabel();
            builder.setItems(labels, (dialog, which) -> MaintenanceDialog.message(activity, R.string.maintenance_health,
                    labels[which] + "\n\n" + activity.getString(R.string.maintenance_health_hint)));
        }
        builder.setNeutralButton(SourceHealthManager.isSortEnabled() ? R.string.maintenance_health_sort_on : R.string.maintenance_health_sort_off,
                (dialog, which) -> {
                    SourceHealthManager.setSortEnabled(!SourceHealthManager.isSortEnabled());
                    show(activity);
                });
        builder.setPositiveButton(R.string.maintenance_health_clear, (dialog, which) -> {
            SourceHealthManager.clear();
            show(activity);
        });
        builder.setNegativeButton(R.string.dialog_negative, null);
        MaintenanceDialog.showDialog(builder.show());
    }
}
