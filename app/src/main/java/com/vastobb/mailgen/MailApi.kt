package com.vastobb/mailgen

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

data class MailAttachment(
    val fileName: String,
    val mimeType: String,
    val url: String
)

data class MailMessage(
    val id: String,
    val sender: String,
    val recipient: String,
    val subject: String,
    val snippet: String,
    val body: String,
    val timestamp: Long,
    val attachments: List<MailAttachment> = emptyList()
)

interface MailApi {
    @GET("api/mails")
    suspend fun getMails(@Query("inbox") inbox: String): List<MailMessage>

    @GET("api/mails/{id}")
    suspend fun getMailDetail(@Path("id") mailId: String): MailMessage
}
