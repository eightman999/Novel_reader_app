package jp.taizan.android.vjap;

import java.lang.Character.UnicodeBlock;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

public class VTextView extends View {

    private static final int FONT_COLOR = Color.BLACK;
    private static final int TOP_SPACE = 18;     // dp
    private static final int BOTTOM_SPACE = 18;  // dp
    // ViewPager のページ数上限。超える長さの本文は最終ページで打ち切る（計算スレッドの範囲外書き込み防止）
    public static final int MAX_PAGE = 4096;

    // 行頭禁則文字（行頭に来てはならない文字。前の文字での改行を抑止し、ぶら下げで処理する）
    private static final String LINE_HEAD_PROHIBITED =
            "。、，．」』）)］]｝}〉》】〕＞>！？!?‼⁇⁈⁉…‥ーｰ～〜ゝゞヽヾ々" +
            "ぁぃぅぇぉっゃゅょゎァィゥェォッャュョヮヵヶ：；:;.,";

    // 行末禁則文字（行末に来てはならない文字。先に改行してから描画する）
    private static final String LINE_END_PROHIBITED = "「『（(［[｛{〈《【〔＜<“";

    // 密度スケール済み余白（init で設定）
    private float topSpace = TOP_SPACE;
    private float bottomSpace = BOTTOM_SPACE;

    int TITLE_SIZE = 48;
    int FONT_SIZE = 32;
    int RUBY_SIZE = 16;

    Context mContext;

    private Typeface mFace;

    // 行送り（文字サイズに対する倍率）。ルビを行間に描くため 1.5 未満にはしない
    private float lineSpaceRate = 2.0f;

    private TextStyle titleStyle;
    private TextStyle bodyStyle;
    private TextStyle rubyStyle;

    private String text = "eee";
    private String title = "タイトル";

    private int[] pageIndex = new int[MAX_PAGE];
    private int currentIndex = 0;
    /**
     * 最後の描画ページの添字（0始まり、-1は未計算）。
     * 表示上のページ番号は描画ページ添字 + 1 のため、最終ページ番号は totalPage + 1。
     */
    public int totalPage = -1;

    // ページ計算の世代管理（reset ごとに進め、古い計算結果を破棄する）
    private volatile int layoutGeneration = 0;
    private int calcGeneration = -1;
    int imageNum = 0;
    private boolean isNextImage = false;

    int width;
    int height;

    public boolean virtical = true;

    public VTextView(Context context) {
        this(context, null);
        init(context);
    }

    public VTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public VTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        mContext = context;
        float density = context.getResources().getDisplayMetrics().density;
        topSpace = TOP_SPACE * density;
        bottomSpace = BOTTOM_SPACE * density;
        mFace = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL);
        setFontSize(FONT_SIZE);
        rubyStyle.lineSpace = bodyStyle.lineSpace;
    }

    public void setTitle(String title) {
        this.title = title;
        this.reset(true);
    }

    public void setText(String text) {
        this.text = text;
        this.reset(true);
    }

    public void setPage(int page) {
        this.currentIndex = page;
        this.invalidate();
    }

    public int getCurrentPage() {
        return this.currentIndex;
    }

    public int getTotalPage() {
        return this.totalPage;
    }

    /** 描画対象の本文（青空文庫形式に変換済み） */
    public String getText() {
        return this.text;
    }

    /**
     * 本文中の文字位置を含むページ添字（0始まり）を返す。ページ計算前は -1。
     */
    public int findPageOf(int charIndex) {
        if (totalPage < 0) return -1;
        for (int page = 0; page <= totalPage; page++) {
            int next = pageStart(page + 1);
            if (page == totalPage || charIndex < next) return page;
        }
        return totalPage;
    }

    /** ページ先頭の文字位置（画像ページは負値で記録されているため絶対値に戻す） */
    private int pageStart(int page) {
        int index = pageIndex[page];
        return index >= 0 ? index : -index - 2;
    }

    /** 行送りの倍率を設定する（1.5〜3.0 に丸める） */
    public void setLineSpaceRate(float rate) {
        float clamped = Math.max(1.5f, Math.min(3.0f, rate));
        if (clamped == lineSpaceRate) return;
        lineSpaceRate = clamped;
        setFontSize(FONT_SIZE);
    }

    public void setColor(String fontColor, String backgroundColor) {
        titleStyle.paint.setColor(Color.parseColor(fontColor));
        bodyStyle.paint.setColor(Color.parseColor(fontColor));
        rubyStyle.paint.setColor(Color.parseColor(fontColor));
        this.setBackgroundColor(Color.parseColor(backgroundColor));
        reset(true);
    }

    public void setFont(String path) {
        if (path != null) {
            mFace = Typeface.createFromFile(path);
        } else {
            mFace = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL);
        }
        setFontSize(FONT_SIZE);
        reset(true);
    }

    public void setFontSize(int size) {
        TITLE_SIZE = (int) (size * 1.5);
        FONT_SIZE = size;
        RUBY_SIZE = size / 2;
        titleStyle = new TextStyle(TITLE_SIZE);
        bodyStyle = new TextStyle(FONT_SIZE);
        rubyStyle = new TextStyle(RUBY_SIZE);
        rubyStyle.lineSpace = bodyStyle.lineSpace;
        reset(true);
    }

    public void reset(boolean isReDraw) {
        this.layoutGeneration++;
        this.pageIndex[0] = 0;
        this.currentIndex = 0;
        this.totalPage = -1;
        this.imageNum = 0;
        this.isNextImage = false;
        if (isReDraw) this.invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        width = MeasureSpec.getSize(widthMeasureSpec);
        height = MeasureSpec.getSize(heightMeasureSpec);
        this.setMeasuredDimension(width, height);
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    public void toggleMode() {
        this.virtical = !this.virtical;
        this.invalidate();
    }

    boolean checkHalf(String s) {
        CharSetting setting = CharSetting.getSetting(s);
        if (setting == null) {
            return false;
        } else if (s.getBytes().length < 2 && setting.angle == 90.0f) {
            return true;
        }
        return false;
    }

    public void drawChar(Canvas canvas, String s, PointF pos, TextStyle style, boolean drawEnable) {
        CharSetting setting = CharSetting.getSetting(s);
        float fontSpacing = style.fontSpace;
        float halfOffset = 0;
        if (virtical && checkHalf(s)) {
            pos.y -= fontSpacing / 2;
        }
        if (virtical) {
            if (setting == null && s.getBytes().length < 2) {
                halfOffset = 0.2f;
            }
        }
        if (drawEnable) {
            if (setting == null || !virtical) {
                canvas.drawText(s, pos.x + fontSpacing * halfOffset, pos.y, style.paint);
            } else {
                canvas.save();
                canvas.rotate(setting.angle, pos.x, pos.y);
                canvas.drawText(s,
                        pos.x + fontSpacing * setting.x, pos.y + fontSpacing * setting.y,
                        style.paint);
                canvas.restore();
            }
        }
        if (!virtical && checkHalf(s)) {
            pos.x -= fontSpacing / 2;
        }
    }

    public boolean drawString(Canvas canvas, String s, PointF pos, TextStyle style, boolean drawEnable) {
        for (int i = 0; i < s.length(); i++) {
            drawChar(canvas, s.charAt(i) + "", pos, style, drawEnable);
            if (!goNext(s, pos, style, true)) {
                return false;
            }
        }
        return true;
    }

    boolean goNextLine(PointF pos, TextStyle type, float spaceRate) {
        if (virtical) {
            pos.x -= type.lineSpace * spaceRate;
            pos.y = topSpace + type.fontSpace;
            if (pos.x > 0) {
                return true;
            } else {
                return false;
            }
        } else {
            pos.y += type.lineSpace * spaceRate;
            pos.x = topSpace;
            if (pos.y < height - topSpace) {
                return true;
            } else {
                return false;
            }
        }
    }

    /** 現在位置が行の最終マスか（この位置に描いた次の文字は行内に収まらない） */
    boolean isLineFull(PointF pos, TextStyle type) {
        if (virtical) {
            return pos.y + type.fontSpace > height - bottomSpace;
        } else {
            return pos.x + type.fontSpace > width - bottomSpace - type.fontSpace;
        }
    }

    /** ページ内に次の行を確保できるか（goNextLine が成功するか） */
    boolean canGoNextLine(PointF pos, TextStyle type, float spaceRate) {
        if (virtical) {
            return pos.x - type.lineSpace * spaceRate > 0;
        } else {
            return pos.y + type.lineSpace * spaceRate < height - topSpace;
        }
    }

    boolean goNext(String s, PointF pos, TextStyle type, boolean lineChangable) {
        boolean newLine = isLineFull(pos, type);

        if (newLine && lineChangable) {
            return goNextLine(pos, type, 1);
        } else {
            float fontSpace = type.fontSpace;
            if (virtical) {
                pos.y += fontSpace;
            } else {
                pos.x += fontSpace;
            }
        }
        return true;
    }

    void initPos(PointF pos) {
        if (virtical) {
            pos.x = width - bodyStyle.lineSpace;
            pos.y = topSpace + bodyStyle.fontSpace;
        } else {
            pos.x = topSpace;
            pos.y = bodyStyle.lineSpace;
        }
    }

    PointF getHeadPos(PointF pos, TextStyle style) {
        PointF res = new PointF();
        if (virtical) {
            res.x = pos.x;
            res.y = pos.y;
        } else {
            res.x = pos.x;
            res.y = pos.y;
        }
        return res;
    }

    PointF getRubyPos(CurrentState state) {
        PointF res = new PointF();
        if (virtical) {
            res.x = state.rubyStart.x + bodyStyle.fontSpace;
            res.y = state.rubyStart.y - rubyStyle.fontSpace;
            if (state.pos.y - state.rubyStart.y > 0) {
                res.y -= 0.5 * (state.rubyText.length() * rubyStyle.fontSpace - (state.pos.y - state.rubyStart.y));
            }
            if (res.y < topSpace) res.y = topSpace;
        } else {
            res.x = state.rubyStart.x;
            res.y = state.rubyStart.y - bodyStyle.fontSpace;
            if (state.pos.x - state.rubyStart.x > 0) {
                res.x -= 0.5 * (state.rubyText.length() * rubyStyle.fontSpace - (state.pos.x - state.rubyStart.x));
            }
        }
        return res;
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        this.textDraw(canvas, currentIndex, true, this);
        if (com.shunlight_library.novel_reader.BuildConfig.ENABLE_LOGGING) Log.d("draw", "draw vtext");
        // 計算中に onDraw が繰り返されても同一レイアウトの計算は1本だけ走らせる
        if (this.totalPage < 0 && calcGeneration != layoutGeneration) this.calcPages();
    }

    final Handler mainHandler = new Handler(Looper.getMainLooper());

    public void calcPages() {
        final int generation = layoutGeneration;
        calcGeneration = generation;
        // 計算は専用の配列で行い、完了時に UI スレッドで差し替える
        // （描画中の pageIndex と共有すると、無効化後の古い計算結果が混入するため）
        final int[] calcIndex = new int[MAX_PAGE];
        new Thread(new Runnable() {
            @Override
            public void run() {
                int current = 0;
                while (!textDraw(null, current, false, null, calcIndex)) {
                    if (generation != layoutGeneration) return; // 設定変更で無効化された計算
                    if (current >= MAX_PAGE - 2) break; // 上限を超える本文は打ち切る
                    current++;
                }
                if (generation != layoutGeneration) return;
                // 本文がページ末尾ちょうどで終わると最後に空ページができるため除外する
                if (current > 0 && calcIndex[current] >= text.length()) {
                    current--;
                }
                if (com.shunlight_library.novel_reader.BuildConfig.ENABLE_LOGGING) Log.d("page", "last page: " + current);
                // 以前は current - 1 としており、最終描画ページへ遷移できなかった（#164）
                final int calculated = current;
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (generation != layoutGeneration) return;
                        System.arraycopy(calcIndex, 0, pageIndex, 0, MAX_PAGE);
                        totalPage = calculated;
                        if (onPageClacListener != null) onPageClacListener.onPageClac(totalPage);
                    }
                });
            }
        }).start();
    }

    public boolean drawPage(Canvas canvas, int page, View v) {
        return textDraw(canvas, page, true, v);
    }

    public String checkImage(int page) {
        return checkImage(page, pageIndex);
    }

    private String checkImage(int page, int[] pageIndex) {
        if (page + 1 >= MAX_PAGE) return null;
        int index = pageIndex[page];

        if (index < 0) {
            String urlStr = "";
            int i;
            for (i = -index; i < text.length(); i++) {
                if (text.charAt(i) == '$') break;
                urlStr += text.charAt(i);
            }
            if (com.shunlight_library.novel_reader.BuildConfig.ENABLE_LOGGING) Log.d("url", urlStr);
            pageIndex[page + 1] = i + 1;
            return urlStr;
        }

        return null;
    }

    public boolean textDraw(Canvas canvas, int page, boolean enable, View v) {
        if (page + 1 >= MAX_PAGE) return true;
        return textDraw(canvas, page, enable, v, pageIndex);
    }

    private boolean textDraw(Canvas canvas, int page, boolean enable, View v, int[] pageIndex) {
        CurrentState state = new CurrentState();
        initPos(state.pos);
        initPos(state.rpos);
        boolean endFlag = true;

        state.isDrawEnable = enable;
        if (page == 0) {
            state.isTitle = true;
            state.isRubyEnable = false;
            state.sAfter = "";
            for (int i = 0; i < title.length(); i++) {
                state.lineChangable = true;
                state.str = title.charAt(i) + "";
                charDrawProcess(canvas, state);
            }
            state.str = "\n";
            charDrawProcess(canvas, state);
            state.isTitle = false;
            state.isRubyEnable = true;
        }

        int index = pageIndex[page];

        if (checkImage(page, pageIndex) != null) {
            if (pageIndex[page + 1] < text.length()) {
                return false;
            } else {
                return true;
            }
        }

        for (; index < text.length(); index++) {
            state.lineChangable = true;
            state.strPrev = state.str;
            state.str = text.charAt(index) + "";
            state.sAfter = (index + 1 < text.length()) ?
                    text.charAt(index + 1) + "" : "";

            if (!charDrawProcess(canvas, state)) {
                endFlag = false;
                break;
            }
        }
        if (state.hasImage) {
            pageIndex[page + 1] = -(index + 2);
        } else {
            pageIndex[page + 1] = index + 1;
        }

        return endFlag;
    }

    boolean charDrawProcess(Canvas canvas, CurrentState state) {
        if (state.str.equals("%") && state.sAfter.equals("$")) {
            this.isNextImage = true;
            state.hasImage = true;
            return false;
        }

        if (state.isRubyEnable) {
            if (state.isRubyBody && (state.bodyText.length() > 20 || state.str.equals("\n"))) {
                drawString(canvas, state.buf + state.bodyText, state.pos, bodyStyle, state.isDrawEnable);
                state.bodyText = "";
                state.buf = "";
                state.isRubyBody = false;
            }

            if (state.str.equals("|") || state.str.equals("｜")) {
                if (state.bodyText.length() > 0) {
                    drawString(canvas, state.buf + state.bodyText, state.pos, bodyStyle, state.isDrawEnable);
                    state.bodyText = "";
                    state.buf = "";
                }
                state.bodyText = "";
                state.buf = state.str;
                state.isRubyBody = true;
                state.rubyStart = getHeadPos(state.pos, bodyStyle);
                return true;
            }
            if (state.str.equals("《") && (state.isRubyBody || state.isKanjiBlock)) {
                state.isRuby = true;
                state.isRubyBody = false;
                state.rubyText = "";
                return true;
            }
            if (state.str.equals("》") && state.isRuby) {
                drawString(canvas, state.bodyText, state.pos, bodyStyle, state.isDrawEnable);
                state.rpos = getRubyPos(state);
                drawString(canvas, state.rubyText, state.rpos, rubyStyle, state.isDrawEnable);
                state.isRuby = false;
                state.bodyText = "";
                state.buf = "";
                if (state.isPageEnd) {
                    return false;
                }
                return true;
            }

            boolean isKanji = (UnicodeBlock.of(state.str.charAt(0)) == UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS);
            if (isKanji && !state.isKanjiBlock) {
                if (!state.isRubyBody) {
                    state.rubyStart = getHeadPos(state.pos, bodyStyle);
                }
            }
            state.isKanjiBlock = isKanji;

            if (state.isRuby) {
                state.rubyText += state.str;
                return true;
            }
            if (state.isRubyBody) {
                state.bodyText += state.str;
                return true;
            }
        }

        TextStyle style = state.isTitle ? titleStyle : bodyStyle;

        if (state.str.equals("\n")) {
            return this.goNextLine(state.pos, style, 1);
        }

        // 行末禁則: 開き括弧等が行の最終マスに来る場合は先に改行してから描画する
        if (LINE_END_PROHIBITED.contains(state.str)
                && isLineFull(state.pos, style) && canGoNextLine(state.pos, style, 1)) {
            goNextLine(state.pos, style, 1);
        }

        this.drawChar(canvas, state.str, state.pos, style, state.isDrawEnable);

        if (!this.goNext(state.str, state.pos, style, checkLineChangable(state))) {
            state.isPageEnd = true;
            if (state.isRubyBody) {
                return true;
            } else {
                return false;
            }
        }
        return true;
    }

    boolean checkLineChangable(CurrentState state) {
        if (!state.lineChangable) {
            state.lineChangable = true;
        } else if (!state.sAfter.isEmpty() && LINE_HEAD_PROHIBITED.contains(state.sAfter)) {
            // 行頭禁則文字が次に来る場合は改行を抑止（ぶら下げ処理）
            state.lineChangable = false;
        }
        return state.lineChangable;
    }

    class CurrentState {
        String strPrev;
        String str;
        String sAfter;

        String rubyText = "";
        String bodyText = "";
        String buf = "";
        boolean isTitle = false;
        boolean isDrawEnable = true;
        boolean isRubyEnable = true;
        boolean isRuby = false;
        boolean isKanjiBlock = false;
        boolean isRubyBody = false;
        boolean lineChangable = true;
        boolean isPageEnd = false;
        boolean hasImage = false;

        PointF pos;
        PointF rpos;
        PointF rubyStart;
        PointF rubyEnd;

        CurrentState() {
            strPrev = "";
            sAfter = "";
            str = "";
            pos = new PointF();
            rpos = new PointF();
            rubyStart = new PointF();
            rubyEnd = new PointF();
        }
    }

    class TextStyle {
        public Paint paint;
        float fontSpace;
        float lineSpace;

        TextStyle(int size) {
            this.paint = new Paint();
            this.paint.setTextSize(size);
            this.paint.setColor(FONT_COLOR);
            this.paint.setTypeface(mFace);
            this.paint.setAntiAlias(true);
            this.paint.setSubpixelText(true);

            this.fontSpace = size;
            this.lineSpace = this.fontSpace * lineSpaceRate;
        }
    }

    OnPageClacListener onPageClacListener;

    public void setOnPageClacListener(OnPageClacListener onPageClacListener) {
        this.onPageClacListener = onPageClacListener;
    }

    public interface OnPageClacListener {
        void onPageClac(int total);
    }
}
