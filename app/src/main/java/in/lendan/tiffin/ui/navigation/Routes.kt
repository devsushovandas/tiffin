package `in`.lendan.tiffin.ui.navigation

import android.net.Uri

object Routes {
    const val ARG_KITCHEN_ID = "kitchenId"
    const val ARG_SOURCE = "source"

    const val LIST = "list"
    const val DETAIL = "detail/{$ARG_KITCHEN_ID}"
    const val PAYWALL = "paywall?$ARG_SOURCE={$ARG_SOURCE}&$ARG_KITCHEN_ID={$ARG_KITCHEN_ID}"

    /** Paywall `source` values. They are also sent as the analytics `source` property. */
    const val SOURCE_SUBSCRIBE_TAP = "subscribe_tap"
    const val SOURCE_THIRD_LAUNCH = "third_launch"

    fun detail(kitchenId: String): String = "detail/${Uri.encode(kitchenId)}"

    fun paywall(source: String, kitchenId: String?): String = buildString {
        append("paywall?$ARG_SOURCE=${Uri.encode(source)}")
        if (kitchenId != null) append("&$ARG_KITCHEN_ID=${Uri.encode(kitchenId)}")
    }
}
