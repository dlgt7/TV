package com.fongmi.android.tv.bean;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class DiscoverFilterPanel {

    private final List<List<DiscoverFilterOption>> rows = new ArrayList<>();
    private int expandedRow = -1;
    private int rowCount = 6;

    public DiscoverFilterPanel() {
        for (int i = 0; i < 7; i++) rows.add(Collections.emptyList());
    }

    public int getRowCount() { return rowCount; }

    public void setRowCount(int count) {
        rowCount = Math.max(1, Math.min(count, rows.size()));
        if (expandedRow >= rowCount) expandedRow = -1;
    }

    public void setRow(int row, List<DiscoverFilterOption> options) {
        rows.set(row, options == null ? Collections.emptyList() : new ArrayList<>(options));
    }

    public List<DiscoverFilterOption> getRow(int row) {
        return Collections.unmodifiableList(rows.get(row));
    }

    public int getExpandedRow() {
        return expandedRow;
    }

    public void setExpandedRow(int expandedRow) {
        this.expandedRow = expandedRow >= 0 && expandedRow < rowCount ? expandedRow : -1;
    }
}
