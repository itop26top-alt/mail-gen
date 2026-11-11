package com.vastobb.mailgen

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class MailAttachment(
    val id: String,
    val filename: String,
    val contentType: String,
    val size: Int,
    val downloadUrl: String
)

data class Account(
    val id: String,
    val address: String,
    val password: String,
    val token: String,
)

data class MessageSummary(
    val id: String,
    val subject: String,
    val from: String,
    val intro: String,
    val seen: Boolean,
    val createdAt: String,
)

data class MessageDetail(
    val id: String,
    val subject: String,
    val from: String,
    val text: String,
    val html: String,
    val createdAt: String,
    val cc: List<String> = emptyList(),
    val attachments: List<MailAttachment> = emptyList()
)

object MailApi {
    private const val BASE = "https://api.mail.tm"
    private const val UA = "mail-gen/1.0"

    private val json = "application/json".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    class ApiException(message: String) : Exception(message)

    private fun call(
        method: String,
        path: String,
        token: String? = null,
        body: JSONObject? = null,
    ): Pair<Int, String> {
        val rb = body?.toString()?.toRequestBody(json)
        val req = Request.Builder()
            .url(BASE + path)
            .header("User-Agent", UA)
            .header("Accept", "application/json")
            .apply { token?.let { header("Authorization", "Bearer $it") } }
            .method(method, rb)
            .build()
        client.newCall(req).execute().use { r ->
            return r.code to (r.body?.string() ?: "")
        }
    }

    private fun items(raw: String): JSONArray {
        val t = raw.trim()
        if (t.startsWith("[")) return JSONArray(t)
        val o = JSONObject(t)
        return o.optJSONArray("hydra:member") ?: o.optJSONArray("member") ?: JSONArray()
    }

    suspend fun activeDomain(): String = withContext(Dispatchers.IO) {
        val (code, raw) = call("GET", "/domains?page=1")
        if (code != 200) throw ApiException("domains failed: $code")
        val arr = items(raw)
        for (i in 0 until arr.length()) {
            val d = arr.getJSONObject(i)
            if (d.optBoolean("isActive") && !d.optBoolean("isPrivate")) {
                return@withContext d.getString("domain")
            }
        }
        throw ApiException("no active public domain")
    }

    private fun randomLocal(n: Int = 10): String {
        val cs = "abcdefghijklmnopqrstuvwxyz0123456789"
        return (1..n).map { cs.random() }.joinToString("")
    }

    private fun randomPassword(): String {
        val cs = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..16).map { cs.random() }.joinToString("") + "!aA1"
    }

    suspend fun createAccount(domain: String? = null): Account = withContext(Dispatchers.IO) {
        val dom = domain ?: activeDomain()
        val address = "${randomLocal()}@$dom"
        val password = randomPassword()

        val (code, raw) = call(
            "POST", "/accounts",
            body = JSONObject().put("address", address).put("password", password),
        )
        if (code != 200 && code != 201) throw ApiException("create failed: $code $raw")
        val created = JSONObject(raw)

        val (tcode, traw) = call(
            "POST", "/token",
            body = JSONObject().put("address", address).put("password", password),
        )
        if (tcode != 200) throw ApiException("token failed: $tcode")
        val token = JSONObject(traw).getString("token")

        Account(
            id = created.optString("id"),
            address = address,
            password = password,
            token = token,
        )
    }

    suspend fun listMessages(token: String): List<MessageSummary> = withContext(Dispatchers.IO) {
        val (code, raw) = call("GET", "/messages?page=1", token)
        if (code != 200) throw ApiException("messages failed: $code")
        val arr = items(raw)
        (0 until arr.length()).map { i ->
            val m = arr.getJSONObject(i)
            MessageSummary(
                id = m.optString("id"),
                subject = m.optString("subject").ifBlank { "(no subject)" },
                from = m.optJSONObject("from")?.optString("address").orEmpty(),
                intro = m.optString("intro").orEmpty(),
                seen = m.optBoolean("seen"),
                createdAt = m.optString("createdAt").orEmpty(),
            )
        }
    }

    suspend fun readMessage(token: String, id: String): MessageDetail = withContext(Dispatchers.IO) {
        val (code, raw) = call("GET", "/messages/$id", token)
        if (code != 200) throw ApiException("read failed: $code")
        val m = JSONObject(raw)

        val ccList = mutableListOf<String>()
        val ccArr = m.optJSONArray("cc")
        if (ccArr != null) {
            for (i in 0 until ccArr.length()) {
                val item = ccArr.optJSONObject(i)
                item?.optString("address")?.let { ccList.add(it) }
            }
        }

        val attachmentsList = mutableListOf<MailAttachment>()
        val attArr = m.optJSONArray("attachments")
        if (attArr != null) {
            for (i in 0 until attArr.length()) {
                val att = attArr.getJSONObject(i)
                val attId = att.optString("id")
                val downloadPath = att.optString("downloadUrl").ifBlank { "/messages/$id/attachments/$attId" }
                attachmentsList.add(
                    MailAttachment(
                        id = attId,
                        filename = att.optString("filename").ifBlank { "attachment_${i + 1}" },
                        contentType = att.optString("contentType").ifBlank { "application/octet-stream" },
                        size = att.optInt("size", 0),
                        downloadUrl = if (downloadPath.startsWith("http")) downloadPath else "$BASE$downloadPath"
                    )
                )
            }
        }

        val htmlText = m.optJSONArray("html")?.optString(0) ?: m.optString("html").orEmpty()
        val plainText = m.optJSONArray("text")?.optString(0) ?: m.optString("text").orEmpty()

        MessageDetail(
            id = m.optString("id"),
            subject = m.optString("subject").ifBlank { "(no subject)" },
            from = m.optJSONObject("from")?.optString("address").orEmpty(),
            text = plainText,
            html = htmlText,
            createdAt = m.optString("createdAt").orEmpty(),
            cc = ccList,
            attachments = attachmentsList
        )
    }

    suspend fun deleteAccount(acc: Account): Boolean = withContext(Dispatchers.IO) {
        val (code, _) = call("DELETE", "/accounts/${acc.id}", acc.token)
        code == 200 || code == 204 || code == 401
    }

    suspend fun alive(acc: Account): Boolean = withContext(Dispatchers.IO) {
        val (code, _) = call("GET", "/me", acc.token)
        code == 200
    }
}
