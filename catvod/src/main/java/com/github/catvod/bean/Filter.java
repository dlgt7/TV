package com.github.catvod.bean;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class Filter {
    @SerializedName(value="key")
    private String key;
    @SerializedName(value="name")
    private String name;
    @SerializedName(value="init")
    private String init;
    @SerializedName(value="value")
    private List<Value> value;

    public Filter(String key, String name, List<Value> value) {
        this.key = key;
        this.name = name;
        this.value = value;
    }

    public static class Value {
        @SerializedName(value="n")
        private String n;
        @SerializedName(value="v")
        private String v;

        public Value(String value) {
            this(value, value);
        }

        public Value(String n, String v) {
            this.n = n;
            this.v = v;
        }
    }
}
