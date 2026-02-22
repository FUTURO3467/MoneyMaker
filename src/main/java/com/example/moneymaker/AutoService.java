package com.example.moneymaker;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.annotation.SuppressLint;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;

@SuppressLint("AccessibilityPolicy")
public class AutoService extends AccessibilityService {
    public static AutoService instance;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        System.out.println("Accessibility connected");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {
        instance = null;
    }

    public void click(int x, int y) {
        if (instance == null) return;

        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(() -> {
            Path path = new Path();
            path.moveTo(x, y);
            GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, 50))
                .build();

            instance.dispatchGesture(gesture, null, null);
        });
    }
}