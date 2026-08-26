package com.leopoldBlk.mobileSensingClients.util.controller

import android.os.Environment
import com.leopoldBlk.mobileSensingClients.util.DataStore
import java.io.File
import java.io.FileWriter
import java.time.LocalDateTime

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
            "identifier of the client sent the message containing the LAMM with local density data; " +
                    "identifier of sent message containing the LAMM with local density data; " +
                    "timestamp when starting to generate the LAMM and the message containing the LAMM with local density data in milliseconds; " +
                    "timestamp when finishing to generate the LAMM and the message containing the LAMM with local density data in milliseconds; " +
                    "time to to generate the LAMM and the message containing the LAMM with local density data in milliseconds; " +
                    "timestamp after sending the message containing the LAMM with local density data in milliseconds"
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
            "identifier of the client processing the message containing a FAMM with global density data; " +
                    "identifier of processed message containing a FAMM with global density data; " +
                    "timestamp when starting to process the message containing a FAMM with global density data in milliseconds; " +
                    "timestamp when finishing to process the message containing a FAMM with global density data in milliseconds; " +
                    "duration the message containing a FAMM with global density data took to process in milliseconds\n"
        )
        scalabilityAnalysisLogProcessedMessagesFileWriter.flush()
    }

    // Checked (final)
    public fun addScalabilityAnalysisLogSentMessagesEntry(
        clientIdHexString: String,
        messageIdHexString: String,
        timestampLammAndMessageGenerationsStarts: ULong,
        timestampLammAndMessageGenerationsEnds: ULong,
        timeToGenerateLammAndMessage: ULong,
        timestampAfterLocalDensityDataMessageSent: ULong
    ) {
        scalabilityAnalysisLogSentMessagesFileWriter.write(
            "$clientIdHexString; " +
                    "$messageIdHexString; " +
                    "$timestampLammAndMessageGenerationsStarts; " +
                    "$timestampLammAndMessageGenerationsEnds; " +
                    "$timeToGenerateLammAndMessage; " +
                    "$timestampAfterLocalDensityDataMessageSent\n"
        )
        scalabilityAnalysisLogSentMessagesFileWriter.flush()

    }

    // Checked (final)
    public fun addScalabilityAnalysisLogProcessedMessagesEntry(
        clientIdHexString: String,
        messageIdHexString: String,
        timestampWhenStartingToProcessMessageInMilliseconds: ULong,
        timestampWhenEndingToProcessMessageInMilliseconds: ULong,
        durationTheMessageTookToProcessInMilliseconds: ULong
    ) {
        scalabilityAnalysisLogProcessedMessagesFileWriter.write(
            "$clientIdHexString; " +
                    "$messageIdHexString; " +
                    "$timestampWhenStartingToProcessMessageInMilliseconds; " +
                    "$timestampWhenEndingToProcessMessageInMilliseconds; " +
                    "$durationTheMessageTookToProcessInMilliseconds\n"
        )
        scalabilityAnalysisLogProcessedMessagesFileWriter.flush()
    }

    // Checked (final)
    public fun stopScalabilityAnalysisLogging() {
        scalabilityAnalysisLogSentMessagesFileWriter.close()
        scalabilityAnalysisLogProcessedMessagesFileWriter.close()
    }
}