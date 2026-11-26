package com.xiaoman.app

/**
 * UI 设计令牌。
 *
 * Android 端不使用 XML 主题，尺寸集中在这里定义；UI 代码引用语义化常量，
 * 避免在布局中散落 `16f`、`20f` 之类的魔法数字。新增尺寸时先在这里补令牌。
 */


/** 调色板：颜色集中定义，UI 代码不出现十六进制字面量。 */
object Colors {
    const val TRANSPARENT = 0x00000000
    const val WHITE = 0xFFFFFFFF.toInt()
    const val BLACK = 0xFF111111.toInt()

    const val NEUTRAL_980 = 0xFF101010.toInt()
    const val NEUTRAL_950 = 0xFF1A1A1A.toInt()
    const val NEUTRAL_900 = 0xFF1C1C1C.toInt()
    const val NEUTRAL_850 = 0xFF242424.toInt()
    const val NEUTRAL_830 = 0xFF262626.toInt()
    const val NEUTRAL_800 = 0xFF2A2A2A.toInt()
    const val NEUTRAL_780 = 0xFF2C2C2C.toInt()
    const val NEUTRAL_700 = 0xFF333333.toInt()
    const val NEUTRAL_650 = 0xFF3F3F3F.toInt()
    const val NEUTRAL_600 = 0xFF5C5C5C.toInt()
    const val NEUTRAL_580 = 0xFF5F6352.toInt()
    const val NEUTRAL_500 = 0xFF7A7A7A.toInt()
    const val NEUTRAL_460 = 0xFF8A8A8A.toInt()
    const val NEUTRAL_450 = 0xFF8D8D8D.toInt()
    const val NEUTRAL_400 = 0xFF9A9A9A.toInt()
    const val NEUTRAL_380 = 0xFFA3A3A3.toInt()
    const val NEUTRAL_360 = 0xFFB8B8B8.toInt()
    const val NEUTRAL_340 = 0xFFBBBBBB.toInt()
    const val NEUTRAL_300 = 0xFFCCCCCC.toInt()
    const val NEUTRAL_250 = 0xFFE0E0E0.toInt()
    const val NEUTRAL_220 = 0xFFE9E9E9.toInt()
    const val NEUTRAL_200 = 0xFFEDEDED.toInt()
    const val NEUTRAL_180 = 0xFFEFEFEF.toInt()
    const val NEUTRAL_160 = 0xFFF0F0F0.toInt()
    const val NEUTRAL_150 = 0xFFF1F1F1.toInt()
    const val NEUTRAL_120 = 0xFFF2F2F2.toInt()
    const val NEUTRAL_100 = 0xFFF4F4F4.toInt()
    const val NEUTRAL_80 = 0xFFF6F6F6.toInt()

    const val LIME = 0xFFC9F44F.toInt()
    const val LIME_DEEP = 0xFFB6EE25.toInt()
    const val TEAL = 0xFF3ED6B0.toInt()
    const val ORANGE = 0xFFFF7A45.toInt()
    const val RED = 0xFFFF5A4E.toInt()
    const val AMBER = 0xFFFFD166.toInt()
    const val BLUE = 0xFF7AA2FF.toInt()

    const val LIME_SURFACE_DARK = 0xFF22300C.toInt()
    const val LIME_SURFACE_DARK_ALT = 0xFF2A3A10.toInt()
    const val LIME_SURFACE_LIGHT = 0xFFEDF8C9.toInt()
    const val LIME_SOFT = 0xFFF1F8DC.toInt()
    const val LIME_INK = 0xFF5D7A14.toInt()
    const val LIME_INK_SOFT = 0xFFB9D97A.toInt()

    const val HERO_START = 0xFFFDFEF8.toInt()
    const val HERO_MID = 0xFFF7FBE4.toInt()
    const val HERO_END = 0xFFEEF8CD.toInt()

    const val DANGER_SURFACE = 0xFFFFF0EE.toInt()
    const val DANGER_SURFACE_DARK = 0xFF3A1F1D.toInt()
    const val SOLD_SURFACE_LIGHT = 0xFFFFF0E8.toInt()
    const val SOLD_SURFACE_DARK = 0xFF3A2419.toInt()
    const val SOLD_INK_LIGHT = 0xFFC75B28.toInt()
    const val SOLD_INK_DARK = 0xFFE08A5E.toInt()

    const val ONBOARD_DOT = 0xFFC9CDA9.toInt()
}

/** 圆角（dp） */
object Radius {
    const val INDICATOR = 2f
    const val SEGMENT = 8f
    const val SEGMENT_SM = 9f
    const val FIELD = 10f
    const val PLATE = 11f
    const val CELL = 12f
    const val CHIP = 14f
    const val CARD = 16f
    const val PILL = 17f
    const val CARD_MD = 18f
    const val CARD_LG = 20f
    const val DIALOG = 20f
    const val BUTTON = 23f
    const val ACTION_LG = 24f
    const val ACTION = 26f
    const val NAV = 32f
}

/** 字号（sp） */
object FontSize {
    const val TINY = 9f
    const val CAPTION = 10f
    const val SMALL = 11f
    const val META = 12f
    const val BODY_SM = 13f
    const val BODY = 14f
    const val BODY_LG = 15f
    const val TITLE = 16f
    const val TITLE_LG = 17f
    const val HEADING = 19f
    const val HEADLINE = 20f
    const val NUMBER = 22f
    const val DISPLAY = 24f
    const val LARGE = 28f
    const val HUGE = 30f
    const val ARROW = 34f
    const val HERO = 40f
}

/** 固定尺寸（dp） */
object Size {
    const val NAV_ITEM_W = 84
    const val NAV_BAR_H = 64
    const val NAV_BOTTOM_MARGIN = 22
    const val FAB = 64
    const val ICON_SMALL = 16
    const val ICON_MEDIUM = 18
    const val ICON_LARGE = 22
    const val ICON_XL = 44
    const val BAR_HEIGHT = 5
}

/** 常用间距（dp），用于 padding / margin */
object Spacing {
    const val XS = 4f
    const val S = 6f
    const val M = 8f
    const val L = 10f
    const val XL = 12f
    const val XXL = 16f
    const val XXXL = 18f
    const val HUGE = 20f
    const val MASSIVE = 24f
}
