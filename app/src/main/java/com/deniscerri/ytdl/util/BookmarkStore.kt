package com.deniscerri.ytdl.util

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Bookmarks live entirely in local cache. No Room table, no migration, nothing that can touch
 * the download pipeline. Everything is a JSON blob in its own SharedPreferences file.
 */
object BookmarkStore {

    private const val FILE = "zetta_bookmarks"
    private const val KEY_FOLDERS = "folders"
    private const val KEY_ITEMS = "items"

    data class Folder(
        val id: Long,
        val name: String,
        val colorIndex: Int,
        val createdAt: Long
    )

    data class Item(
        val id: Long,
        val folderId: Long,
        val url: String,
        val title: String,
        val author: String,
        val thumb: String,
        val platform: String,
        val addedAt: Long
    )

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    // ---------------------------------------------------------------- folders

    fun getFolders(context: Context): List<Folder> {
        return runCatching {
            val raw = prefs(context).getString(KEY_FOLDERS, "[]") ?: "[]"
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Folder(
                    id = o.optLong("id"),
                    name = o.optString("name"),
                    colorIndex = o.optInt("color", 0),
                    createdAt = o.optLong("createdAt")
                )
            }.sortedBy { it.createdAt }
        }.getOrDefault(emptyList())
    }

    fun createFolder(context: Context, name: String, colorIndex: Int = 0): Folder {
        val folder = Folder(
            id = System.currentTimeMillis(),
            name = name.trim().ifBlank { "Untitled" },
            colorIndex = colorIndex,
            createdAt = System.currentTimeMillis()
        )
        saveFolders(context, getFolders(context) + folder)
        return folder
    }

    fun renameFolder(context: Context, id: Long, name: String) {
        saveFolders(context, getFolders(context).map {
            if (it.id == id) it.copy(name = name.trim().ifBlank { it.name }) else it
        })
    }

    fun deleteFolder(context: Context, id: Long) {
        saveFolders(context, getFolders(context).filterNot { it.id == id })
        saveItems(context, getAllItems(context).filterNot { it.folderId == id })
    }

    private fun saveFolders(context: Context, folders: List<Folder>) {
        runCatching {
            val arr = JSONArray()
            folders.forEach {
                arr.put(
                    JSONObject()
                        .put("id", it.id)
                        .put("name", it.name)
                        .put("color", it.colorIndex)
                        .put("createdAt", it.createdAt)
                )
            }
            prefs(context).edit().putString(KEY_FOLDERS, arr.toString()).apply()
        }
    }

    // ------------------------------------------------------------------ items

    fun getAllItems(context: Context): List<Item> {
        return runCatching {
            val raw = prefs(context).getString(KEY_ITEMS, "[]") ?: "[]"
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Item(
                    id = o.optLong("id"),
                    folderId = o.optLong("folderId"),
                    url = o.optString("url"),
                    title = o.optString("title"),
                    author = o.optString("author"),
                    thumb = o.optString("thumb"),
                    platform = o.optString("platform"),
                    addedAt = o.optLong("addedAt")
                )
            }
        }.getOrDefault(emptyList())
    }

    fun getItems(context: Context, folderId: Long): List<Item> =
        getAllItems(context).filter { it.folderId == folderId }.sortedByDescending { it.addedAt }

    fun countIn(context: Context, folderId: Long): Int =
        getAllItems(context).count { it.folderId == folderId }

    fun addItem(
        context: Context,
        folderId: Long,
        url: String,
        title: String = "",
        author: String = "",
        thumb: String = "",
        platform: String = ""
    ): Item {
        val item = Item(
            id = System.currentTimeMillis(),
            folderId = folderId,
            url = url.trim(),
            title = title.ifBlank { url.trim() },
            author = author,
            thumb = thumb,
            platform = platform.ifBlank { hostOf(url) },
            addedAt = System.currentTimeMillis()
        )
        saveItems(context, getAllItems(context) + item)
        return item
    }

    fun deleteItem(context: Context, id: Long) {
        saveItems(context, getAllItems(context).filterNot { it.id == id })
    }

    fun updateItemMeta(context: Context, id: Long, title: String, author: String, thumb: String) {
        saveItems(context, getAllItems(context).map {
            if (it.id == id) it.copy(
                title = title.ifBlank { it.title },
                author = author.ifBlank { it.author },
                thumb = thumb.ifBlank { it.thumb }
            ) else it
        })
    }

    private fun saveItems(context: Context, items: List<Item>) {
        runCatching {
            val arr = JSONArray()
            items.forEach {
                arr.put(
                    JSONObject()
                        .put("id", it.id)
                        .put("folderId", it.folderId)
                        .put("url", it.url)
                        .put("title", it.title)
                        .put("author", it.author)
                        .put("thumb", it.thumb)
                        .put("platform", it.platform)
                        .put("addedAt", it.addedAt)
                )
            }
            prefs(context).edit().putString(KEY_ITEMS, arr.toString()).apply()
        }
    }

    fun hostOf(url: String): String {
        return runCatching {
            var w = url.trim().lowercase()
                .removePrefix("https://").removePrefix("http://").removePrefix("www.")
            w = w.substringBefore('/')
            val parts = w.split('.').filter { it.isNotBlank() }
            val core = if (parts.size >= 2) parts[parts.size - 2] else parts.firstOrNull() ?: w
            core.replaceFirstChar { c -> c.uppercase() }
        }.getOrDefault("Link")
    }
}
