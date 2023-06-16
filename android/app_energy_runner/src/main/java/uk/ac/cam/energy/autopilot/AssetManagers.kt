package uk.ac.cam.energy.autopilot

import android.content.Context
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import uk.ac.cam.energy.common.execution.ensureLogsDir
import uk.ac.cam.energy.common.mkdirIfNotExists
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

private val SERVER_IP = BuildConfig.WEB_SERVICE_IP
private val SERVER_PORT = BuildConfig.WEB_SERVICE_IP
private val SERVER_URI_SCENARIOS = "http://$SERVER_IP:$SERVER_PORT/energy/scenarios.zip"
private val SERVER_URI_ANDROID_LOG = "http://$SERVER_IP:$SERVER_PORT/energy/android_log"

// Set to empty string to disable authentication
private val SERVER_USER = BuildConfig.WEB_SERVICE_AUTH_USER
private val SERVER_PASSWORD = BuildConfig.WEB_SERVICE_AUTH_PASSWORD

class LogManager(val context: Context) {

    fun uploadAllResults() {
        val client = OkHttpClient()
        for (filename in listLogs()) {
            val file = getCsvFile(filename)
            uploadResult(file, filename, client)
        }
    }

    private fun uploadResult(file: File, name: String, client: OkHttpClient) {
        val requestBody = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart(
                name = "file",
                filename = name,
                body = RequestBody.create("text/csv".toMediaTypeOrNull(), file)
            )
            .build()

        val url = "$SERVER_URI_ANDROID_LOG/$name"
        val requestBuilder = Request.Builder()
            .url(url)
            .post(requestBody)

        if (!SERVER_USER.isNullOrEmpty()) {
            requestBuilder.addHeader(
                name = "Authorization",
                value = Credentials.basic(SERVER_USER, SERVER_PASSWORD)
            )
        }

        client.newCall(requestBuilder.build()).execute()
    }

    private fun getCsvFile(filename: String) = File(ensureLogsDir(context), filename)

    private fun listLogs(): List<String> {
        val dir = ensureLogsDir(context)
        return dir.list().asList()
    }
}

class ScenariosManager(private val context: Context) {

    fun removeScenarios() {
        val dir = ensureScenariosDir()
        dir.listFiles().forEach { it.delete() }
    }

    fun listScenarios(): List<String> {
        val dir = ensureScenariosDir()
        return dir.list().asList().sorted()
    }

    fun getFileForName(name: String): File {
        return File(ensureScenariosDir(), name)
    }

    fun downloadScenarios() {
        val client = OkHttpClient()
        val request: Request = Request.Builder()
            .addHeader("Authorization", Credentials.basic(SERVER_USER, SERVER_PASSWORD))
            .url(SERVER_URI_SCENARIOS)
            .get()
            .build()

        val response = client.newCall(request).execute()

        response.body!!.use { body ->
            body.byteStream().use { inputStream ->
                ZipInputStream(inputStream).use { zipInputStream ->
                    var entry: ZipEntry?
                    while (zipInputStream.getNextEntry().also { entry = it } != null) {
                        val outFile = getFileForName(entry!!.name)
                        outFile.outputStream().use { outputStream ->
                            zipInputStream.copyTo(outputStream)
                        }
                    }
                }
            }
        }
    }

    private fun ensureScenariosDir(): File {
        val dir = File(context.filesDir, "scenarios")
        dir.mkdirIfNotExists()
        return dir
    }
}
