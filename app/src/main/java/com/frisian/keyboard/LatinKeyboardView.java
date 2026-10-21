/*
 * Copyright (C) 2008-2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.frisian.keyboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.Keyboard.Key;
import android.inputmethodservice.KeyboardView;
import android.util.AttributeSet;
import android.view.inputmethod.InputMethodSubtype;

public class LatinKeyboardView extends KeyboardView {

    static final int KEYCODE_OPTIONS = -100;
    // TODO: Move this into android.inputmethodservice.Keyboard
    static final int KEYCODE_LANGUAGE_SWITCH = -101;
    static final int KEYCODE_LANGUAGE_SWITCH_ALT = -777;
    public Canvas mCanvas;

    private String mTransientMessage;
    private long mTransientMessageUntil;
    private final Paint mMessagePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect mMessageBounds = new Rect();
    private final Runnable mClearMessageRunnable = new Runnable() {
        @Override
        public void run() {
            mTransientMessage = null;
            invalidate();
        }
    };

    public LatinKeyboardView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public LatinKeyboardView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected boolean onLongPress(Key key) {
        if (key.codes[0] == Keyboard.KEYCODE_CANCEL) {
            getOnKeyboardActionListener().onKey(KEYCODE_OPTIONS, null);
            return true;
        /*} else if (key.codes[0] == 113) {

            return true; */
        } else {
            //Log.d("LatinKeyboardView", "KEY: " + key.codes[0]);
            return super.onLongPress(key);
        }
    }

    void setSubtypeOnSpaceKey(final InputMethodSubtype subtype) {
        final LatinKeyboard keyboard = (LatinKeyboard)getKeyboard();
        //keyboard.setSpaceIcon(getResources().getDrawable(subtype.getIconResId()));
        invalidateAllKeys();
    }

    /**
     * Shows a short lived message on top of the keyboard. This is the IME friendly
     * replacement for a {@link android.widget.Toast}, which is unreliable (and
     * rate limited) when it is posted from an input method service.
     *
     * <p>The message is drawn in the same way a toast is drawn, so it does not stand
     * out from the system toasts of the app.
     */
    public void showMessage(CharSequence message, long durationMillis) {
        if (message == null) {
            return;
        }
        mTransientMessage = message.toString();
        mTransientMessageUntil = System.currentTimeMillis() + durationMillis;
        mMessagePaint.setTextSize(
                getResources().getDimensionPixelSize(R.dimen.message_font_height));
        invalidate();
        removeCallbacks(mClearMessageRunnable);
        postDelayed(mClearMessageRunnable, durationMillis);
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        mCanvas = canvas;
        drawTransientMessage(canvas);
    }

    private void drawTransientMessage(Canvas canvas) {
        if (mTransientMessage == null
                || System.currentTimeMillis() >= mTransientMessageUntil) {
            return;
        }
        final float textWidth = mMessagePaint.measureText(mTransientMessage);
        final float padding = getResources().getDimensionPixelSize(R.dimen.message_padding);
        final float radius = getResources().getDimensionPixelSize(R.dimen.message_corner_radius);

        mMessagePaint.setStyle(Paint.Style.FILL);
        mMessagePaint.getTextBounds(
                mTransientMessage, 0, mTransientMessage.length(), mMessageBounds);

        final float left = (getWidth() - textWidth) / 2f;
        final float top = mMessageBounds.height() * 0.2f;
        final RectF background = new RectF(
                left - padding,
                top,
                left + textWidth + padding,
                top + mMessageBounds.height() + padding / 2f);

        mMessagePaint.setColor(0xE0202020);
        canvas.drawRoundRect(background, radius, radius, mMessagePaint);

        mMessagePaint.setColor(0xFFFFFFFF);
        canvas.drawText(mTransientMessage, left, top + mMessageBounds.height(), mMessagePaint);
    }
}
