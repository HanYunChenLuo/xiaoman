package com.xiaoman.app

/**
 * UI 设计令牌。
 *
 * Android 端不使用 XML 主题，尺寸集中在这里定义；UI 代码引用语义化常量，
 * 避免在布局中散落 `16f`、`20f` 之类的魔法数字。新增尺寸时先在这里补令牌。
 */

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
