package com.tencent.tinker.build.apkparser;

/**
 * Created by panchangpeng on 2019/6/19.
 */
public class Component {
    String name;
    int type;
    boolean exported;

    public Component(String _name, int _type, boolean _exported) {
        this.name = _name;
        this.type = _type;
        this.exported = _exported;
    }

    public String getName() {
        return name;
    }

    public int getType() {
        return type;
    }

    public boolean isExported() {
        return exported;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof Component) {
            Component old = (Component) o;
            return name.equals(old.name);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}
