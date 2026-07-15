package com.osama.bydyoutubebridge;

import android.content.Context;
import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/** Reflection-only adapter for BYD's framework API. */
public final class AmbientLightBridge {
    private static final String TAG = "BYD-AmbientBridge";
    private static final String CLASS_NAME = "android.hardware.bydauto.audio.BYDAutoAudioDevice";

    private final Context context;
    private Object device;
    private Method setFreqMethod;
    private String status = "Not initialized";

    public AmbientLightBridge(Context context) {
        this.context = context.getApplicationContext();
    }

    public synchronized boolean initialize() {
        if (device != null && setFreqMethod != null) return true;
        try {
            Class<?> cls = Class.forName(CLASS_NAME);
            device = obtainInstance(cls);
            setFreqMethod = findSetFrequencyMethod(cls);
            if (device == null) {
                status = "BYD class found, but instance unavailable";
                return false;
            }
            if (setFreqMethod == null) {
                status = "BYD device found, setAmbientLightFreq missing";
                return false;
            }
            setFreqMethod.setAccessible(true);
            status = "BYD ambient API ready: " + signature(setFreqMethod);
            return true;
        } catch (Throwable t) {
            status = t.getClass().getSimpleName() + ": " + safe(t.getMessage());
            Log.e(TAG, "Unable to initialize BYD ambient API", t);
            return false;
        }
    }

    public synchronized boolean send(int[] values) {
        if (values == null || values.length != 16) {
            status = "Expected exactly 16 frequency values";
            return false;
        }
        if (!initialize()) return false;
        try {
            Class<?> type = setFreqMethod.getParameterTypes()[0];
            Object arg;
            if (type == int[].class) {
                arg = values;
            } else if (type == Integer[].class) {
                Integer[] boxed = new Integer[values.length];
                for (int i = 0; i < values.length; i++) boxed[i] = values[i];
                arg = boxed;
            } else if (List.class.isAssignableFrom(type)) {
                List<Integer> list = new ArrayList<>();
                for (int value : values) list.add(value);
                arg = list;
            } else {
                status = "Unsupported parameter: " + type.getName();
                return false;
            }
            Object result = setFreqMethod.invoke(device, arg);
            status = "Sent 16 bands" + (result == null ? "" : " result=" + result);
            return true;
        } catch (Throwable t) {
            status = t.getClass().getSimpleName() + ": " + safe(t.getMessage());
            Log.e(TAG, "setAmbientLightFreq failed", t);
            return false;
        }
    }

    public synchronized String getStatus() {
        return status;
    }

    private Object obtainInstance(Class<?> cls) throws Exception {
        for (Method method : cls.getDeclaredMethods()) {
            if (!Modifier.isStatic(method.getModifiers())) continue;
            if (!(method.getName().equals("getInstance") || method.getName().equals("getDevice"))) continue;
            method.setAccessible(true);
            Class<?>[] p = method.getParameterTypes();
            if (p.length == 0) return method.invoke(null);
            if (p.length == 1 && Context.class.isAssignableFrom(p[0])) return method.invoke(null, context);
        }
        for (Constructor<?> constructor : cls.getDeclaredConstructors()) {
            constructor.setAccessible(true);
            Class<?>[] p = constructor.getParameterTypes();
            if (p.length == 0) return constructor.newInstance();
            if (p.length == 1 && Context.class.isAssignableFrom(p[0])) return constructor.newInstance(context);
        }
        return null;
    }

    private Method findSetFrequencyMethod(Class<?> cls) {
        for (Method method : cls.getMethods()) {
            if (method.getName().equals("setAmbientLightFreq") && method.getParameterTypes().length == 1) {
                return method;
            }
        }
        for (Method method : cls.getDeclaredMethods()) {
            if (method.getName().equals("setAmbientLightFreq") && method.getParameterTypes().length == 1) {
                return method;
            }
        }
        return null;
    }

    private static String signature(Method method) {
        Class<?>[] params = method.getParameterTypes();
        return method.getName() + "(" + (params.length == 0 ? "" : params[0].getSimpleName()) + ")";
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
