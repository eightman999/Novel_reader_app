package jp.taizan.android.vjap;

class CharSetting {

    public final String charcter;
    public final float angle;
    public final float x;
    public final float y;

    public CharSetting(String charcter, float angle, float x, float y) {
        super();
        this.charcter = charcter;
        this.angle = angle;
        this.x = x;
        this.y = y;
    }

    public static final CharSetting[] settings = {
        new CharSetting("、", 0.0f, 0.7f, -0.6f), new CharSetting("。", 0.0f, 0.7f, -0.6f),
        new CharSetting("，", 0.0f, 0.7f, -0.6f), new CharSetting("．", 0.0f, 0.7f, -0.6f),

        new CharSetting("「", 90.0f, -1.0f, -0.3f), new CharSetting("」", 90.0f, -0.7f, 0.0f),
        new CharSetting("『", 90.0f, -1.0f, -0.3f), new CharSetting("』", 90.0f, -0.7f, 0.0f),
        new CharSetting("（", 90.0f,-0.8f, -0.13f), new CharSetting("）", 90.0f, -0.8f, -0.13f),
        new CharSetting("【", 90.0f,-0.8f, -0.13f), new CharSetting("】", 90.0f, -0.8f, -0.13f),
        new CharSetting("［", 90.0f,-0.8f, -0.13f), new CharSetting("］", 90.0f, -0.8f, -0.13f),
        new CharSetting("〔", 90.0f,-0.8f, -0.13f), new CharSetting("〕", 90.0f, -0.8f, -0.13f),
        new CharSetting("〈", 90.0f,-0.8f, -0.13f), new CharSetting("〉", 90.0f, -0.8f, -0.13f),
        new CharSetting("《", 90.0f,-0.8f, -0.13f), new CharSetting("》", 90.0f, -0.8f, -0.13f),
        new CharSetting("＜", 90.0f,-0.8f, -0.13f), new CharSetting("＞", 90.0f, -0.8f, -0.13f),
        new CharSetting("：", 90.0f, -0.8f, -0.1f), new CharSetting("；", 90.0f, 0.8f, -0.1f),
        new CharSetting("／", 90.0f, -0.9f, -0.1f), new CharSetting("｜", 90.0f, -0.8f, -0.1f),
        new CharSetting("＝", 90.0f, -0.8f, -0.1f), new CharSetting("÷", 90.0f, -0.8f, -0.1f),

        new CharSetting("“", 0.0f, -0.0f, 0.6f), new CharSetting("”", 0.0f, -0.0f, 0.1f),
        new CharSetting("゛", 0.0f, 0.9f, -1.0f), new CharSetting("゜", 0.0f, 0.9f, -1.0f),

        new CharSetting("～", 90.0f, -0.8f, -0.1f), new CharSetting("〜", 90.0f, -0.8f, -0.1f),
        new CharSetting("─", 90.0f, -0.8f, -0.1f), new CharSetting("—", 90.0f, -0.8f, -0.1f),
        new CharSetting("―", 90.0f, -0.8f, -0.1f), new CharSetting("−", 90.0f, -0.8f, -0.1f),

        new CharSetting(".", 0.0f, 0.7f, -0.6f), new CharSetting(",", 0.0f, 0.7f, -0.6f),
        new CharSetting("(", 90.0f, -0.3f, -0.15f), new CharSetting(")", 90.0f, -0.3f, -0.15f),
        new CharSetting("[", 90.0f, -0.3f, -0.13f), new CharSetting("]", 90.0f, -0.3f, -0.13f),
        new CharSetting("{", 90.0f, -0.3f, -0.13f), new CharSetting("}", 90.0f, -0.3f, -0.13f),
        new CharSetting(":", 90.0f, -0.4f, -0.1f), new CharSetting(";", 90.0f, -0.4f, -0.1f),
        new CharSetting("~", 90.0f, -0.4f, -0.1f), new CharSetting("|", 90.0f, -0.4f, -0.1f),
        new CharSetting("/", 90.0f, -0.4f, -0.1f), new CharSetting("…", 90.0f, -0.8f, -0.1f),
        new CharSetting("=", 90.0f, -0.4f, -0.1f), new CharSetting("-", 90.0f, -0.4f, -0.1f),

        new CharSetting("ぁ", 0.0f, 0.1f, -0.1f), new CharSetting("ぃ", 0.0f, 0.1f, -0.1f),
        new CharSetting("ぅ", 0.0f, 0.1f, -0.1f), new CharSetting("ぇ", 0.0f, 0.1f, -0.1f),
        new CharSetting("ぉ", 0.0f, 0.1f, -0.1f), new CharSetting("っ", 0.0f, 0.1f, -0.1f),
        new CharSetting("ゃ", 0.0f, 0.1f, -0.1f), new CharSetting("ゅ", 0.0f, 0.1f, -0.1f),
        new CharSetting("ょ", 0.0f, 0.1f, -0.1f), new CharSetting("ァ", 0.0f, 0.1f, -0.1f),
        new CharSetting("ィ", 0.0f, 0.1f, -0.1f), new CharSetting("ゥ", 0.0f, 0.1f, -0.1f),
        new CharSetting("ェ", 0.0f, 0.1f, -0.1f), new CharSetting("ォ", 0.0f, 0.1f, -0.1f),
        new CharSetting("ッ", 0.0f, 0.1f, -0.1f), new CharSetting("ャ", 0.0f, 0.1f, -0.1f),
        new CharSetting("ュ", 0.0f, 0.1f, -0.1f), new CharSetting("ョ", 0.0f, 0.1f, -0.1f),
        new CharSetting("ー", -90.0f, -0.05f, 0.9f),
        new CharSetting("a", 90.0f, -0.4f, -0.1f), new CharSetting("b", 90.0f, -0.4f, -0.1f),
        new CharSetting("c", 90.0f, -0.4f, -0.1f), new CharSetting("d", 90.0f, -0.4f, -0.1f),
        new CharSetting("e", 90.0f, -0.4f, -0.1f), new CharSetting("f", 90.0f, -0.4f, -0.1f),
        new CharSetting("g", 90.0f, -0.4f, -0.1f), new CharSetting("h", 90.0f, -0.4f, -0.1f),
        new CharSetting("i", 90.0f, -0.4f, -0.1f), new CharSetting("j", 90.0f, -0.4f, -0.1f),
        new CharSetting("k", 90.0f, -0.4f, -0.1f), new CharSetting("l", 90.0f, -0.4f, -0.1f),
        new CharSetting("m", 90.0f, -0.4f, -0.1f), new CharSetting("n", 90.0f, -0.4f, -0.1f),
        new CharSetting("o", 90.0f, -0.4f, -0.1f), new CharSetting("p", 90.0f, -0.4f, -0.1f),
        new CharSetting("q", 90.0f, -0.4f, -0.1f), new CharSetting("r", 90.0f, -0.4f, -0.1f),
        new CharSetting("s", 90.0f, -0.4f, -0.1f), new CharSetting("t", 90.0f, -0.4f, -0.1f),
        new CharSetting("u", 90.0f, -0.4f, -0.1f), new CharSetting("v", 90.0f, -0.4f, -0.1f),
        new CharSetting("w", 90.0f, -0.4f, -0.1f), new CharSetting("x", 90.0f, -0.4f, -0.1f),
        new CharSetting("y", 90.0f, -0.4f, -0.1f), new CharSetting("z", 90.0f, -0.4f, -0.1f),
        new CharSetting("A", 90.0f, -0.4f, -0.1f), new CharSetting("B", 90.0f, -0.4f, -0.1f),
        new CharSetting("C", 90.0f, -0.4f, -0.1f), new CharSetting("D", 90.0f, -0.4f, -0.1f),
        new CharSetting("E", 90.0f, -0.4f, -0.1f), new CharSetting("F", 90.0f, -0.4f, -0.1f),
        new CharSetting("G", 90.0f, -0.4f, -0.1f), new CharSetting("H", 90.0f, -0.4f, -0.1f),
        new CharSetting("I", 90.0f, -0.4f, -0.1f), new CharSetting("J", 90.0f, -0.4f, -0.1f),
        new CharSetting("K", 90.0f, -0.4f, -0.1f), new CharSetting("L", 90.0f, -0.4f, -0.1f),
        new CharSetting("M", 90.0f, -0.4f, -0.1f), new CharSetting("N", 90.0f, -0.4f, -0.1f),
        new CharSetting("O", 90.0f, -0.4f, -0.1f), new CharSetting("P", 90.0f, -0.4f, -0.1f),
        new CharSetting("Q", 90.0f, -0.4f, -0.1f), new CharSetting("R", 90.0f, -0.4f, -0.1f),
        new CharSetting("S", 90.0f, -0.4f, -0.1f), new CharSetting("T", 90.0f, -0.4f, -0.1f),
        new CharSetting("U", 90.0f, -0.4f, -0.1f), new CharSetting("V", 90.0f, -0.4f, -0.1f),
        new CharSetting("W", 90.0f, -0.4f, -0.1f), new CharSetting("X", 90.0f, -0.4f, -0.1f),
        new CharSetting("Y", 90.0f, -0.4f, -0.1f), new CharSetting("Z", 90.0f, -0.4f, -0.1f),
    };

    public static CharSetting getSetting(String character) {
        for (int i = 0; i < settings.length; i++) {
            if (settings[i].charcter.equals(character)) {
                return settings[i];
            }
        }
        return null;
    }

    private static final String[] PUNCTUATION_MARK = {"、", "。", "「", "」"};

    public static boolean isPunctuationMark(String s) {
        for (String functuationMark : PUNCTUATION_MARK) {
            if (functuationMark.equals(s)) {
                return true;
            }
        }
        return false;
    }
}
