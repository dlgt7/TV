package com.fongmi.android.tv.bean;

import android.text.TextUtils;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.utils.ResUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Button {

    public int id;
    public int resId;

    private static List<Button> buttons;

    public Button(int id, int resId) {
        this.id = id;
        this.resId = resId;
    }

    public static List<Button> all() {
        if (buttons != null) return buttons;
        buttons = new ArrayList<>();
        buttons.add(new Button(0, R.string.home_vod));
        buttons.add(new Button(1, R.string.home_live));
        buttons.add(new Button(2, R.string.home_search));
        buttons.add(new Button(3, R.string.home_keep));
        buttons.add(new Button(4, R.string.home_push));
        buttons.add(new Button(5, R.string.home_history_short));
        buttons.add(new Button(6, R.string.home_setting));
        return buttons;
    }

    public static List<Button> sortedAll() {
        String sorted = Setting.getHomeButtonsSorted("0,1,2,3,4,5,6");
        if (TextUtils.isEmpty(sorted)) return all();
        String[] arr = sorted.split(",");
        List<Button> list = new ArrayList<>();
        Map<Integer, Button> allButtons = getMap(all());
        for (String s : arr) {
            int id = Integer.parseInt(s);
            if (allButtons.containsKey(id)) list.add(allButtons.get(id));
        }
        return list;
    }

    public static Map<Integer, Button> getMap(List<Button> buttons) {
        Map<Integer, Button> map = new LinkedHashMap<>();
        for (Button b : buttons) map.put(b.getId(), b);
        return map;
    }

    public static String getDefaultButtons() {
        return "0,1,2,3,4,5,6";
    }

    public static String getButtons() {
        return Setting.getHomeButtons(getDefaultButtons());
    }

    public static List<Button> getButtonList() {
        String buttons = getButtons();
        if (TextUtils.isEmpty(buttons)) return new ArrayList<>();
        String[] arr = buttons.split(",");
        List<Button> list = new ArrayList<>();
        Map<Integer, Button> allButtons = getMap(all());
        for (String s : arr) {
            int id = Integer.parseInt(s);
            if (allButtons.containsKey(id)) list.add(allButtons.get(id));
        }
        return list;
    }

    public static Map<Integer, Button> getButtonsMap() {
        return getMap(getButtonList());
    }

    public static void save(Map<Integer, Button> map) {
        List<Integer> ids = new ArrayList<>(map.keySet());
        Setting.putHomeButtons(TextUtils.join(",", ids));
    }

    public static void saveSorted(Map<Integer, Button> map) {
        List<Integer> ids = new ArrayList<>(map.keySet());
        Setting.putHomeButtonsSorted(TextUtils.join(",", ids));
    }

    public String getName() {
        return ResUtil.getString(resId);
    }

    public int getResId() {
        return resId;
    }

    public int getId() {
        return id;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Button)) return false;
        return getId() == ((Button) obj).getId();
    }
}