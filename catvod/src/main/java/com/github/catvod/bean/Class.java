package com.github.catvod.bean;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.List;

public class Class {
    private static final Gson GSON = new Gson();
    private static final Type LIST_TYPE = TypeToken.getParameterized(List.class, (Type[])new Type[]{Class.class}).getType();
    @SerializedName(value="type_id")
    private String typeId;
    @SerializedName(value="type_name")
    private String typeName;
    @SerializedName(value="type_flag")
    private String typeFlag;

    public static List<Class> arrayFrom(String str) {
        return (List)GSON.fromJson(str, LIST_TYPE);
    }

    public Class(String typeId) {
        this(typeId, typeId);
    }

    public Class(String typeId, String typeName) {
        this(typeId, typeName, null);
    }

    public Class(String typeId, String typeName, String typeFlag) {
        this.typeId = typeId;
        this.typeName = typeName;
        this.typeFlag = typeFlag;
    }

    public String getTypeId() {
        return this.typeId;
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Class)) {
            return false;
        }
        Class it = (Class)obj;
        return this.getTypeId().equals(it.getTypeId());
    }
}
