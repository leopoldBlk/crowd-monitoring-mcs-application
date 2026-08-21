package com.leopoldBlk.mobileSensingClients.util.controller

import android.os.Environment
import com.leopoldBlk.mobileSensingClients.util.DataStore
import java.io.File
import java.io.FileWriter
import java.time.LocalDateTime
import kotlin.time.Duration

// Checked (final)
object ScalabilityAnalysisLogController {
    private val scalabilityAnalysisLogDirectory: String =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            .toString() + "/Crowd_Monitoring_App/Scalability_Analysis/"

    // File and file writer for logging the event of sending messages containing local density data
    // to the MOM
    private lateinit var scalabilityAnalysisLogSentMessagesFileWriter: FileWriter
    private lateinit var scalabilityAnalysisLogSentMessagesFile: File

    // File and file writer for logging the event of processing messages containing global density
    // data received from the MOM
    private lateinit var scalabilityAnalysisLogProcessedMessagesFileWriter: FileWriter
    private lateinit var scalabilityAnalysisLogProcessedMessagesFile: File

    // Checked (final)
    public fun setupLogFilesAndWriters() {
        if (!File(scalabilityAnalysisLogDirectory).exists()) {
            File(scalabilityAnalysisLogDirectory).mkdirs()
        }

        val scalabilityAnalysisLogSentMessagesFileName: String =
            (DataStore.clientIdHexString + "_Sent_Messages_" + LocalDateTime.now()).replace(
                "T",
                "_"
            ).replace(":", "-") + ".txt"

        scalabilityAnalysisLogSentMessagesFile =
            File(scalabilityAnalysisLogDirectory + scalabilityAnalysisLogSentMessagesFileName)
        scalabilityAnalysisLogSentMessagesFileWriter =
            FileWriter(scalabilityAnalysisLogSentMessagesFile, true)

        scalabilityAnalysisLogSentMessagesFileWriter.write(
            "identifier of the client sent message containing local density data; " +
                    "identifier of sent message containing local density data; " +
                    "timestamp after sending the message containing local density data in milliseconds\n"
        )
        scalabilityAnalysisLogSentMessagesFileWriter.flush()

        val scalabilityAnalysisLogProcessedMessagesFileName: String =
            (DataStore.clientIdHexString + "_Processed_Messages_" + LocalDateTime.now()).replace(
                "T",
                "_"
            ).replace(":", "-") + ".txt"

        scalabilityAnalysisLogProcessedMessagesFile =
            File(scalabilityAnalysisLogDirectory + scalabilityAnalysisLogProcessedMessagesFileName)
        scalabilityAnalysisLogProcessedMessagesFileWriter =
            FileWriter(scalabilityAnalysisLogProcessedMessagesFile, true)

        scalabilityAnalysisLogProcessedMessagesFileWriter.write(
            "identifier of the client processing the message containing global density data; " +
                    "identifier of processed message containing global density data; " +
                    "timestamp when starting to process the message containing global density data in milliseconds; " +
                    "duration the message containing global density data took to process\n"
        )
        scalabilityAnalysisLogProcessedMessagesFileWriter.flush()
    }

    // Checked (final)
    public fun addScalabilityAnalysisLogSentMessagesEntry(
        clientIdHexString: String,
        messageIdHexString: String,
        timestampAfterMessageSentInMilliseconds: ULong
    ) {
        scalabilityAnalysisLogSentMessagesFileWriter.write(
            "$clientIdHexString; " +
                    "$messageIdHexString; " +
                    "$timestampAfterMessageSentInMilliseconds\n"
        )
        scalabilityAnalysisLogSentMessagesFileWriter.flush()

    }

    // Checked (final)
    public fun addScalabilityAnalysisLogProcessedMessagesEntry(
        clientIdHexString: String,
        messageIdHexString: String,
        timestampWhenStartingToProcessMessageInMilliseconds: ULong,
        durationTheMessageTookToProcess: Duration
    ) {
        scalabilityAnalysisLogProcessedMessagesFileWriter.write(
            "$clientIdHexString; " +
                    "$messageIdHexString; " +
                    "$timestampWhenStartingToProcessMessageInMilliseconds; " +
                    "$durationTheMessageTookToProcess\n"
        )
        scalabilityAnalysisLogProcessedMessagesFileWriter.flush()
    }

    // Checked (final)
    public fun stopScalabilityAnalysisLogging() {
        scalabilityAnalysisLogSentMessagesFileWriter.close()
        scalabilityAnalysisLogProcessedMessagesFileWriter.close()
    }
}