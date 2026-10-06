package app.notify.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Plain JSON backup of the bank and rhythms. Offline-only apps need a way out of the phone.
 * Import is additive: folders are matched by name, words already present are skipped.
 */
object Backup {
    data class Imported(val folders: Int, val words: Int, val rhythmIds: List<Long>)

    suspend fun export(dao: BankDao): String {
        val folders = dao.foldersOnce()
        val wordsByFolder = dao.wordsOnce().groupBy { it.folderId }
        val rhythms = dao.rhythmsOnce()
        val links = dao.linksOnce().groupBy({ it.rhythmId }, { it.folderId })
        val nameOf = folders.associate { it.id to it.name }

        val root = JSONObject().put("app", "notify").put("version", 1)
        val folderArray = JSONArray()
        for (f in folders) {
            val wordArray = JSONArray()
            for (w in wordsByFolder[f.id].orEmpty()) {
                wordArray.put(JSONObject().put("text", w.text).put("label", w.label).put("shown", w.shown))
            }
            folderArray.put(JSONObject().put("name", f.name).put("hue", f.hue).put("words", wordArray))
        }
        val rhythmArray = JSONArray()
        for (r in rhythms) {
            rhythmArray.put(
                JSONObject()
                    .put("name", r.name).put("enabled", r.enabled).put("shuffle", r.shuffle)
                    .put("fixedTimes", r.fixedTimes).put("windowStart", r.windowStart).put("windowEnd", r.windowEnd)
                    .put("perDay", r.perDay).put("times", r.times).put("days", r.days)
                    .put("folders", JSONArray(links[r.id].orEmpty().mapNotNull { nameOf[it] })),
            )
        }
        root.put("folders", folderArray).put("rhythms", rhythmArray)
        return root.toString(2)
    }

    /** Throws if the text is not a Notify export. */
    suspend fun import(dao: BankDao, text: String): Imported {
        val root = JSONObject(text)
        require(root.optString("app") == "notify") { "Not a Notify export" }

        val byName = dao.foldersOnce().associateByTo(mutableMapOf()) { it.name.lowercase() }
        val have = dao.wordsOnce().mapTo(HashSet()) { it.folderId to it.text }
        var newFolders = 0
        var newWords = 0

        val folderArray = root.optJSONArray("folders") ?: JSONArray()
        for (i in 0 until folderArray.length()) {
            val fo = folderArray.getJSONObject(i)
            val name = fo.optString("name").trim()
            if (name.isEmpty()) continue
            val folder = byName[name.lowercase()] ?: run {
                val hue = fo.optInt("hue", 0)
                val created = Folder(id = dao.insertFolder(Folder(name = name, hue = hue)), name = name, hue = hue)
                newFolders++
                byName[name.lowercase()] = created
                created
            }
            val words = fo.optJSONArray("words") ?: JSONArray()
            val fresh = ArrayList<Message>()
            for (j in 0 until words.length()) {
                val wo = words.getJSONObject(j)
                val t = wo.optString("text").trim()
                if (t.isEmpty() || !have.add(folder.id to t)) continue
                fresh += Message(folderId = folder.id, text = t, label = wo.optString("label"), shown = wo.optInt("shown", 0))
            }
            if (fresh.isNotEmpty()) {
                dao.insertMessages(fresh)
                newWords += fresh.size
            }
        }

        val rhythmNames = dao.rhythmsOnce().mapTo(HashSet()) { it.name.lowercase() }
        val rhythmIds = mutableListOf<Long>()
        val rhythmArray = root.optJSONArray("rhythms") ?: JSONArray()
        for (i in 0 until rhythmArray.length()) {
            val ro = rhythmArray.getJSONObject(i)
            val name = ro.optString("name").trim()
            if (name.isEmpty() || !rhythmNames.add(name.lowercase())) continue
            val names = ro.optJSONArray("folders") ?: JSONArray()
            val ids = (0 until names.length()).mapNotNull { byName[names.getString(it).lowercase()]?.id }
            val r = Rhythm(
                name = name,
                enabled = ro.optBoolean("enabled", true),
                shuffle = ro.optBoolean("shuffle", true),
                fixedTimes = ro.optBoolean("fixedTimes", false),
                windowStart = ro.optInt("windowStart", 480),
                windowEnd = ro.optInt("windowEnd", 1260),
                perDay = ro.optInt("perDay", 3),
                times = ro.optString("times", "480,1200"),
                days = ro.optInt("days", 127),
            )
            rhythmIds += dao.saveRhythm(r, ids)
        }
        return Imported(newFolders, newWords, rhythmIds)
    }
}
