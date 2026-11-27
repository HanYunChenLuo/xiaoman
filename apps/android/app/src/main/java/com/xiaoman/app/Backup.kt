package com.xiaoman.app

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.util.Locale

/**
 * 官方 .ias 备份的 Android 适配层。
 *
 * zip/AES 容器在 `core/src/backup.rs`；字段映射与合并策略在 `core/src/state.rs`，
 * 与 Web 端共用。这里只负责：
 * - 解析后的图片字节落盘 / 导出时读取本地图片；
 * - 规范状态的图片名称 ↔ 本地文件路径；
 * - 调用 Rust 完成映射与合并。
 */
object Backup {
    class Parsed(@JvmField val payload: JSONObject, @JvmField val images: MutableMap<String, ByteArray>)

    /** 解密并拆出 `{data, images}`；图片保持 base64，由调用方决定落盘。 */
    @JvmStatic
    fun readIas(zip: ByteArray): Parsed {
        val payload = JSONObject(NativeCore.decodeIas(zip))
        val images = HashMap<String, ByteArray>()
        val obj = payload.optJSONObject("images")
        if (obj != null) {
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                images[k] = android.util.Base64.decode(obj.optString(k), android.util.Base64.DEFAULT)
            }
        }
        return Parsed(payload, images)
    }

    /** 备份本身的数据修改时间（毫秒）：优先 settings.updatedAt，旧备份回退 exported_at。 */
    @JvmStatic
    fun backupTimeMs(payload: JSONObject): Long = NativeCore.backupTimeMs(payload.toString())

    /**
     * 导入备份；`replace` 用备份替换本地，否则合并。
     * 重复条目取较新一方（`preferIncoming`）的语义由 Rust 统一处理。
     */
    @JvmStatic
    fun importInto(
        store: Store,
        payload: JSONObject,
        replace: Boolean,
        preferIncoming: Boolean,
        imagePaths: Map<String, String>?
    ): Int {
        val now = System.currentTimeMillis()
        val result = JSONObject(NativeCore.backupImport(payload.toString(), now, tzOffsetMs()))
        val incoming = result.optJSONObject("state") ?: JSONObject()
        resolveImages(incoming, imagePaths)
        val local = store.mergeSnapshot()
        val merged = JSONObject(
            NativeCore.backupMerge(local.toString(), incoming.toString(), replace, preferIncoming)
        )
        store.applyMergedState(merged)
        return result.optJSONObject("meta")?.optInt("count", 0) ?: 0
    }

    /** 规范状态里的图片名称 -> Android 本地文件路径。 */
    private fun resolveImages(state: JSONObject, imagePaths: Map<String, String>?) {
        val assets = state.optJSONArray("assets") ?: return
        for (i in 0 until assets.length()) {
            val a = assets.optJSONObject(i) ?: continue
            val cover = a.optString("coverImage")
            a.remove("coverImage")
            if (cover.isNotEmpty() && imagePaths != null && imagePaths.containsKey(cover)) {
                a.put("coverPath", imagePaths[cover])
            }
            val notes = a.optJSONArray("noteImages")
            a.remove("noteImages")
            if (notes != null && notes.length() > 0 && imagePaths != null) {
                val paths = JSONArray()
                for (k in 0 until notes.length()) {
                    val name = notes.optString(k)
                    if (imagePaths.containsKey(name)) paths.put(imagePaths[name])
                }
                if (paths.length() > 0) a.put("noteImages", paths)
            }
            if (a.optString("icon").isEmpty()) a.put("icon", "emoji/icon_default_object.png")
        }
    }

    /** 生成备份：本地状态 -> 规范状态（图片写入 base64）-> Rust -> .ias 字节。 */
    @JvmStatic
    fun writeIas(store: Store): ByteArray {
        val images = JSONObject()
        val state = store.mergeSnapshot()
        val assets = state.optJSONArray("assets") ?: JSONArray()
        for (i in 0 until assets.length()) {
            val a = assets.optJSONObject(i) ?: continue
            val id = a.optString("id")

            val coverPath = a.optString("coverPath")
            a.remove("coverPath")
            if (coverPath.isNotEmpty()) {
                val f = File(coverPath)
                if (f.exists()) {
                    val name = "images/${id}_cover${ext(coverPath)}"
                    images.put(name, base64(readFile(f)))
                    a.put("coverImage", name)
                }
            }

            val noteImgs = a.optJSONArray("noteImages")
            a.remove("noteImages")
            if (noteImgs != null) {
                val names = JSONArray()
                for (k in 0 until noteImgs.length()) {
                    val p = noteImgs.optString(k)
                    val f = File(p)
                    if (!f.exists()) continue
                    val name = "images/${id}_note${k}${ext(p)}"
                    images.put(name, base64(readFile(f)))
                    names.put(name)
                }
                if (names.length() > 0) a.put("noteImages", names)
            }
        }
        val payload = NativeCore.backupExport(
            state.toString(), images.toString(), "AOS", System.currentTimeMillis(), tzOffsetMs()
        )
        return NativeCore.encodeIas(payload)
    }

    private fun base64(bytes: ByteArray): String =
        android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)

    private fun ext(path: String): String {
        val i = path.lastIndexOf('.')
        val e = if (i >= 0) path.substring(i).lowercase(Locale.US) else ".jpg"
        return if (e.length > 5) ".jpg" else e
    }

    private fun readFile(f: File): ByteArray {
        FileInputStream(f).use { input ->
            ByteArrayOutputStream().use { out ->
                val buf = ByteArray(8192)
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    out.write(buf, 0, n)
                }
                return out.toByteArray()
            }
        }
    }

    private fun tzOffsetMs(): Long =
        java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()).toLong()
}
