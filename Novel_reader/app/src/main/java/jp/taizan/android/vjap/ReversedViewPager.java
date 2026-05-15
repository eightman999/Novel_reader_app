package jp.taizan.android.vjap;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;

import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

public class ReversedViewPager extends ViewPager {

    static final int MAX_PAGE = VTextView.MAX_PAGE;

    boolean leftScrollDisabled = false;
    boolean scrollDisabled = false;

    int totalPage = -1;
    int currentPage;

    public ReversedViewPager(Context context) {
        super(context);
        super.addOnPageChangeListener(reversedOnPageChangeListener);
    }

    public ReversedViewPager(Context context, AttributeSet attrs) {
        super(context, attrs);
        super.addOnPageChangeListener(reversedOnPageChangeListener);
    }

    public void setScrollDisabled(boolean disable) {
        scrollDisabled = disable;
    }

    float prevX = 0;
    public float virtualX = 0;

    @SuppressLint("ClickableViewAccessibility")
    public boolean onTouchEvent(MotionEvent event) {
        if (scrollDisabled) {
            return true;
        }

        if (leftScrollDisabled) {
            float ex = event.getX();
            if (event.getX() - prevX > 0) {
                virtualX += 0.1 * (event.getX() - prevX);
            } else {
                virtualX += (event.getX() - prevX);
            }

            event.setLocation(virtualX, event.getY());
            prevX = ex;
            Log.d("ex", virtualX + "");
        }
        return super.onTouchEvent(event);
    }

    // Keep for compatibility: internally add as a page change listener
    OnPageChangeListener listener;

    @Override
    @Deprecated
    public void setOnPageChangeListener(OnPageChangeListener listener) {
        this.listener = listener;
    }

    public void setCurrentItem(int i) {
        super.setCurrentItem(MAX_PAGE - i);
    }

    public void setCurrentItem(int i, boolean smooth) {
        super.setCurrentItem(MAX_PAGE - i, smooth);
    }

    @Override
    public void setAdapter(PagerAdapter arg0) {
        super.setAdapter(arg0);
        this.setCurrentItem(0, false);
    }

    OnPageChangeListener reversedOnPageChangeListener = new OnPageChangeListener() {
        @Override
        public void onPageSelected(int position) {
            virtualX = 0;
            if (MAX_PAGE - position > totalPage && totalPage > 0) {
                leftScrollDisabled = true;
            } else {
                leftScrollDisabled = false;
            }

            if (MAX_PAGE - position > totalPage + 1 && totalPage > 0) {
                setCurrentItem(totalPage + 1);
            } else {
                currentPage = MAX_PAGE - position;
                if (listener != null) listener.onPageSelected(currentPage);
            }
        }

        @Override
        public void onPageScrollStateChanged(int arg0) {
            if (listener != null) listener.onPageScrollStateChanged(arg0);
        }

        @Override
        public void onPageScrolled(int arg0, float arg1, int arg2) {
            if (listener != null) listener.onPageScrolled(arg0, arg1, arg2);
        }
    };
}
