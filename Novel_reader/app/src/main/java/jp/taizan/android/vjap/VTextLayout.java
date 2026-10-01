package jp.taizan.android.vjap;

import java.io.IOException;
import java.net.URL;

import jp.taizan.android.vjap.VTextView.OnPageClacListener;

import com.shunlight_library.novel_reader.R;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager.OnPageChangeListener;

public class VTextLayout extends RelativeLayout {

    private final float PAGING_BAR_SIZE = 60;

    Context mContext;

    public VTextView vTextView;

    ReversedViewPager viewPager;
    PagerAdapter adapter;

    int currentPage = 1;

    ReversedSeekBar pagingBar;
    View pagingBarLayout;

    View imageLoadingLayout;

    TextView pageNumText;

    ProgressBar progressBar;

    OnPageEndListener onPageEndListener = null;
    OnReadyListener onReadyListener = null;
    OnPageChangedListener onPageChangedListener = null;
    OnNextAtEndListener onNextAtEndListener = null;

    private float density;

    public VTextLayout(Context context) {
        super(context);
        init(context);
    }

    public VTextLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public VTextLayout(Context context, AttributeSet attrs, int def) {
        super(context, attrs, def);
        init(context);
    }

    class DispView extends ImageView {

        int page;
        RotateAnimation rotate;
        boolean isLoading = false;

        public void onStartBitmapLoad() {
            rotate = new RotateAnimation(0, 360, this.getWidth() / 2, this.getHeight() / 2);
            rotate.setDuration(1000);
            rotate.setRepeatCount(Animation.INFINITE);
            this.startAnimation(rotate);
            this.invalidate();
        }

        public void setBitMap(Bitmap bmp) {
            this.setImageBitmap(bmp);
            this.clearAnimation();
            this.invalidate();
        }

        public DispView(Context context, int page) {
            super(context);
            this.page = page;
        }

        @Override
        public void onDraw(Canvas canvas) {
            String image = vTextView.checkImage(page);
            if (image == null) {
                vTextView.drawPage(canvas, page, this);
            } else {
                if (!isLoading || this.getDrawable() == null) {
                    new DrawImageTask(image, this).execute();
                    isLoading = true;
                }
                super.onDraw(canvas);
            }
        }

        class DrawImageTask {
            Bitmap bmp;
            DispView view;
            String urlStr;
            Handler handler = new Handler(Looper.getMainLooper());

            public DrawImageTask(String url, DispView v) {
                this.urlStr = url;
                this.view = v;
            }

            public void execute() {
                view.onStartBitmapLoad();
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            URL u = new URL(urlStr);
                            bmp = BitmapFactory.decodeStream(u.openConnection().getInputStream());
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                view.setBitMap(bmp);
                            }
                        });
                    }
                }).start();
            }
        }
    }

    public void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.vtext, this);
        this.mContext = context;

        vTextView = new VTextView(context);

        vTextView.setOnPageClacListener(new OnPageClacListener() {
            @Override
            public void onPageClac(int total) {
                viewPager.totalPage = total;
                progressBar.setVisibility(View.GONE);
                updatePageText();
                if (onReadyListener != null) {
                    onReadyListener.onReady(getLastPage());
                }
            }
        });

        adapter = new PagerAdapter() {
            @Override
            public Object instantiateItem(ViewGroup container, int position) {
                final Integer page = ReversedViewPager.MAX_PAGE - position - 1;

                if (page == 0) {
                    container.addView(vTextView);
                    return vTextView;
                }

                DispView view = new DispView(mContext, page);
                container.addView(view);
                return view;
            }

            @Override
            public int getCount() {
                return ReversedViewPager.MAX_PAGE;
            }

            @Override
            public void destroyItem(ViewGroup container, int position, Object object) {
                container.removeView((View) object);
            }

            @Override
            public boolean isViewFromObject(View view, Object object) {
                return view == (View) object;
            }
        };

        viewPager = (ReversedViewPager) findViewById(R.id.view_pager);
        viewPager.setAdapter(adapter);

        viewPager.setOnPageChangeListener(new OnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                currentPage = position;
                updatePageText();

                if (onPageChangedListener != null) {
                    onPageChangedListener.onPageChanged(position, getLastPage());
                }

                if (getLastPage() > 0 && position >= getLastPage() && onPageEndListener != null) {
                    onPageEndListener.onPageEnd();
                }

                Log.d("page", currentPage + "");
            }

            @Override
            public void onPageScrollStateChanged(int arg0) {
            }

            @Override
            public void onPageScrolled(int arg0, float arg1, int arg2) {
            }
        });

        imageLoadingLayout = findViewById(R.id.imageLoading);

        pagingBar = (ReversedSeekBar) findViewById(R.id.seekBar);
        pageNumText = (TextView) findViewById(R.id.pageNumText);
        pagingBarLayout = findViewById(R.id.seekBarLayout);
        progressBar = (ProgressBar) findViewById(R.id.vtextProgressBar);

        density = getResources().getDisplayMetrics().density;

        pagingBarLayout.setVisibility(View.GONE);
        pagingBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (pagingBarLayout.getVisibility() == View.VISIBLE) {
                    updatePageText();
                    viewPager.setCurrentItem(seekBar.getProgress(), false);
                }
            }

            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        progressBar.setVisibility(View.VISIBLE);
    }

    void updatePageText() {
        String text = getLastPage() < 0 ? currentPage + "" : currentPage + "/" + getLastPage();
        pageNumText.setText(text);
    }

    public void updatePageNum(final boolean showSeekBar) {
        pagingBar.setMax(getLastPage());
        pagingBar.setProgress(currentPage);
        updatePageText();
        progressBar.setVisibility(View.GONE);
        if (showSeekBar) pagingBarLayout.setVisibility(View.VISIBLE);
    }

    private float touchStartX;
    private float touchStartY;

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (ev.getY() > getHeight() - PAGING_BAR_SIZE * density) {
                    if (pagingBarLayout.getVisibility() != View.VISIBLE && vTextView.getTotalPage() > 0) {
                        updatePageNum(true);
                        return true;
                    }
                } else {
                    if (pagingBarLayout.getVisibility() == View.VISIBLE) {
                        pagingBarLayout.setVisibility(View.INVISIBLE);
                        return true;
                    }
                }
                touchStartX = ev.getX();
                touchStartY = ev.getY();
                break;
            case MotionEvent.ACTION_UP:
                if (viewPager.scrollDisabled) {
                    boolean isPrevTap;
                    if (vTextView.virtical) {
                        int direction = isClickDirectionLeft ? 1 : -1;
                        isPrevTap = direction * touchStartX > direction * vTextView.width / 2;
                    } else {
                        isPrevTap = touchStartY > vTextView.height / 2;
                    }
                    if (isPrevTap) {
                        prevPage();
                    } else if (!nextPage() && onNextAtEndListener != null) {
                        // 最終ページでの次ページ操作
                        onNextAtEndListener.onNextAtEnd();
                    }
                }
                updatePageText();
        }

        return super.onInterceptTouchEvent(ev);
    }

    public void setScrollDisabled(boolean isDisabled) {
        viewPager.setScrollDisabled(isDisabled);
    }

    boolean isClickDirectionLeft = true;

    public void setClickDirectionLeft(boolean isClickDirectionLeft) {
        this.isClickDirectionLeft = isClickDirectionLeft;
    }

    public void initContent(String title, String text) {
        this.vTextView.setText(text);
        this.vTextView.setTitle(title);
    }

    @Override
    public void onLayout(boolean changed, int l, int t, int r, int b) {
        if (changed) {
            reset();
        }
        super.onLayout(changed, l, t, r, b);
    }

    public void reset() {
        viewPager.totalPage = -1;
        viewPager.setCurrentItem(0);
        vTextView.invalidate();
    }

    /** Navigate to a specific page (1-indexed). */
    public void setCurrentPage(int page) {
        if (page > 0 && viewPager != null) {
            viewPager.setCurrentItem(page, false);
        }
    }

    /** Returns current page (1-indexed). */
    public int getCurrentPage() {
        return currentPage;
    }

    /**
     * 最終ページ番号（1始まり）。未計算なら -1。
     * VTextView.totalPage は最後の描画ページ添字（0始まり）のため +1 して返す。
     */
    public int getLastPage() {
        int total = vTextView.getTotalPage();
        return total < 0 ? -1 : total + 1;
    }

    /** Returns the last page number (1-indexed), or -1 if not yet calculated. */
    public int getTotalPage() {
        return getLastPage();
    }

    /** 次のページへ進む。最終ページで進めなかった場合は false。 */
    public boolean nextPage() {
        int last = getLastPage();
        if (last < 0 || currentPage < last) {
            viewPager.setCurrentItem(currentPage + 1, false);
            updatePageText();
            return true;
        }
        return false;
    }

    /** 前のページへ戻る。先頭で戻れなかった場合は false。 */
    public boolean prevPage() {
        if (currentPage > 1) {
            viewPager.setCurrentItem(currentPage - 1, false);
            updatePageText();
            return true;
        }
        return false;
    }

    /** 最終ページで次ページ操作（タップ）が行われたときに呼ばれる。 */
    public interface OnNextAtEndListener {
        void onNextAtEnd();
    }

    public void setOnNextAtEndListener(OnNextAtEndListener listener) {
        this.onNextAtEndListener = listener;
    }

    public interface OnPageEndListener {
        void onPageEnd();
    }

    public void setOnPageEndListener(OnPageEndListener onPageEndListener) {
        this.onPageEndListener = onPageEndListener;
    }

    /** Called when page calculation is complete. */
    public interface OnReadyListener {
        void onReady(int totalPage);
    }

    public void setOnReadyListener(OnReadyListener listener) {
        this.onReadyListener = listener;
    }

    /**
     * Called whenever the visible page changes (1-indexed).
     * totalPage may be -1 while page calculation is in progress.
     */
    public interface OnPageChangedListener {
        void onPageChanged(int page, int totalPage);
    }

    public void setOnPageChangedListener(OnPageChangedListener listener) {
        this.onPageChangedListener = listener;
    }

    public void setVirtical(boolean isVirtical) {
        vTextView.virtical = isVirtical;
    }

    public void setFontSize(int size) {
        vTextView.setFontSize(size);
        // 表示中ページを含めて再計算させる（ページ0へ戻し onDraw でページ計算を起動）
        reset();
    }

    public void setFont(String path) {
        vTextView.setFont(path);
        reset();
    }

    public void setColor(String fontColor, String backgroundColor) {
        vTextView.setColor(fontColor, backgroundColor);
        this.setBackgroundColor(Color.parseColor(backgroundColor));
        pageNumText.setTextColor(Color.parseColor(fontColor));
        reset();
    }
}
