import pandas as pd
import glob

# File is checked (final)

columnDefEdgeClientSent = {
    'identifier of the client sent the message containing the LAMM with local density data': str,
    'identifier of sent message containing the LAMM with local density data': str,
    'timestamp when starting to generate the LAMM and the message containing the LAMM with local density data in milliseconds': str,
    'timestamp when finishing to generate the LAMM and the message containing the LAMM with local density data in milliseconds': str,
    'time to to generate the LAMM and the message containing the LAMM with local density data in milliseconds': int,
    'timestamp after sending the message containing the LAMM with local density data in milliseconds': str
}

columnDefEdgeClientProcessed = {
    'identifier of the client processing the message containing a FAMM with global density data': str,
    'identifier of processed message containing a FAMM with global density data': str,
    'timestamp when starting to process the message containing a FAMM with global density data in milliseconds': str,
    'timestamp when finishing to process the message containing a FAMM with global density data in milliseconds': str,
    'duration the message containing a FAMM with global density data took to process in milliseconds': int
}

columnDefServerProducer = {
    'identifier of the server sent the message containing the LAMM with global density data': str,
    "identifier of sent message containing the LAMM with global density data": str,
    "timestamp when starting to generate the LAMM and the message containing the LAMM with global density data in milliseconds": str,
    "timestamp when finishing to generate the LAMM and the message containing the LAMM with global density data in milliseconds": str,
    "time to to generate the LAMM and the message containing the LAMM with global density data in milliseconds": int,
    "timestamp after sending the message containing the LAMM with global density data in milliseconds": str
}

columnDefServerConsumer = {
    "identifier of the server processing the message containing a FAMM with local density data": str,
    "identifier of processed message containing a FAMM with local density data": str,
    "timestamp when starting to process the message containing a FAMM with local density data in milliseconds": str,
    "timestamp when finishing to process the message containing a FAMM with local density data in milliseconds": str,
    "duration the message containing a FAMM with local density data took to process in milliseconds": int
}


def readInClientLogsForScenario(scenarioPath):
    clientLogFileForScenarioSentMessages = glob.glob(scenarioPath + "/" + "*Sent*.txt")
    clientLogFileForScenarioProcessedMessages = glob.glob(scenarioPath + "/" + "*Processed*.txt")

    clientSentMessagesDf = pd.read_csv(filepath_or_buffer=clientLogFileForScenarioSentMessages[0], sep=";",
                                       dtype=columnDefEdgeClientSent)
    clientProcessedMessagesDf = pd.read_csv(filepath_or_buffer=clientLogFileForScenarioProcessedMessages[0], sep=";",
                                            dtype=columnDefEdgeClientProcessed)

    return (clientSentMessagesDf, clientProcessedMessagesDf)


def readInServerLogsForScenario(scenarioPath):
    serverLogFilesForScenario = glob.glob(scenarioPath + "/" + "*.log")

    logFileEntriesAcrossAllServersForScalabilityAnalysisProducer = []
    logFileEntriesAcrossAllServersForScalabilityAnalysisConsumer = []
    logFileEntriesAcrossAllServersForScalabilityAnalysisProducerFiltered = []
    logFileEntriesAcrossAllServersForScalabilityAnalysisConsumerFiltered = []

    for serverLogFile in serverLogFilesForScenario:
        logFile = open(serverLogFile)
        logFileEntries = logFile.read().splitlines()
        logFile.close()

        logFileEntriesAcrossAllServersForScalabilityAnalysisProducer = logFileEntriesAcrossAllServersForScalabilityAnalysisProducer + [
            entry for entry in logFileEntries if "Producer - Scalability Analysis" in entry]

        logFileEntriesAcrossAllServersForScalabilityAnalysisConsumer = logFileEntriesAcrossAllServersForScalabilityAnalysisConsumer + [
            entry for entry in logFileEntries if "Consumer - Scalability Analysis" in entry]

    for entry in logFileEntriesAcrossAllServersForScalabilityAnalysisProducer:
        logFileEntriesAcrossAllServersForScalabilityAnalysisProducerFiltered.append(
            (entry.split("Producer - Scalability Analysis : ")[1]).split("; "))

    logFileEntriesAcrossAllServersForScalabilityAnalysisProducerFilteredHeaderOut = []

    for entry in logFileEntriesAcrossAllServersForScalabilityAnalysisProducerFiltered:
        if (entry[0] != 'identifier of the server sent the message containing the LAMM with global density data'):
            logFileEntriesAcrossAllServersForScalabilityAnalysisProducerFilteredHeaderOut.append(entry)

    for entry in logFileEntriesAcrossAllServersForScalabilityAnalysisConsumer:
        logFileEntriesAcrossAllServersForScalabilityAnalysisConsumerFiltered.append(
            (entry.split("Consumer - Scalability Analysis : ")[1]).split("; "))

    logFileEntriesAcrossAllServersForScalabilityAnalysisConsumerFilteredHeaderOut = []

    for entry in logFileEntriesAcrossAllServersForScalabilityAnalysisConsumerFiltered:
        if (entry[0] != 'identifier of the server processing the message containing a FAMM with local density data'):
            logFileEntriesAcrossAllServersForScalabilityAnalysisConsumerFilteredHeaderOut.append(entry)

    serverProducerDf = pd.DataFrame(logFileEntriesAcrossAllServersForScalabilityAnalysisProducerFilteredHeaderOut,
                                                   columns=list(columnDefServerProducer.keys())).astype(
        columnDefServerProducer)
    serverConsumerDf = pd.DataFrame(logFileEntriesAcrossAllServersForScalabilityAnalysisConsumerFilteredHeaderOut,
                                                   columns=list(columnDefServerConsumer.keys())).astype(
        columnDefServerConsumer)

    return (serverProducerDf, serverConsumerDf)


def generateMetricCS(clientSentMMDf, serverConsumedMMDf):
    clientSentMMDfColumns = clientSentMMDf.columns
    clientSentMMDfCopy = clientSentMMDf[[clientSentMMDfColumns[1], clientSentMMDfColumns[3]]].copy()
    clientSentMMDfCopy.columns = ['message identifier',
                                  'timestamp client finished to generate the message containing local density data in milliseconds (before sending)']
    clientSentMMDfCopy['message identifier'] = clientSentMMDfCopy['message identifier'].str.strip()
    clientSentMMDfCopy[
        'timestamp client finished to generate the message containing local density data in milliseconds (before sending)'] = \
        clientSentMMDfCopy[
            'timestamp client finished to generate the message containing local density data in milliseconds (before sending)'].astype(
            int)

    serverConsumedMMDfColumns = serverConsumedMMDf.columns
    serverConsumedMMDfCopy = serverConsumedMMDf[[serverConsumedMMDfColumns[1], serverConsumedMMDfColumns[2]]].copy()
    serverConsumedMMDfCopy.columns = ['message identifier',
                                      'timestamp server started processing the message containing local density data in milliseconds']
    serverConsumedMMDfCopy[
        'timestamp server started processing the message containing local density data in milliseconds'] = \
        serverConsumedMMDfCopy[
            'timestamp server started processing the message containing local density data in milliseconds'].astype(
            int)

    dfMetricCS = pd.merge(clientSentMMDfCopy, serverConsumedMMDfCopy, on='message identifier', how='inner')
    calculatedCS = dfMetricCS[
                       'timestamp server started processing the message containing local density data in milliseconds'] - \
                   dfMetricCS['timestamp client finished to generate the message containing local density data in milliseconds (before sending)']

    dfMetricCS.insert(3, 'time in transit (CS)', calculatedCS)

    return dfMetricCS


def generateMetricSC(serverProduceMMDf, clientProcessMMDf):
    serverProduceMMDfColumns = serverProduceMMDf.columns
    serverProduceMMDfCopy = serverProduceMMDf[[serverProduceMMDfColumns[1], serverProduceMMDfColumns[3]]].copy()
    serverProduceMMDfCopy.columns = ['message identifier',
                                     'timestamp server finished to generate the message containing global density data in milliseconds (before sending)']
    serverProduceMMDfCopy['timestamp server finished to generate the message containing global density data in milliseconds (before sending)'] = \
        serverProduceMMDfCopy['timestamp server finished to generate the message containing global density data in milliseconds (before sending)'].astype(
            int)

    clientProcessMMDfColumns = clientProcessMMDf.columns
    clientProcessMMDfCopy = clientProcessMMDf[[clientProcessMMDfColumns[1], clientProcessMMDfColumns[2]]].copy()
    clientProcessMMDfCopy.columns = ['message identifier',
                                     'timestamp client started processing the message containing global density data in milliseconds']
    clientProcessMMDfCopy['message identifier'] = clientProcessMMDfCopy['message identifier'].str.strip()
    clientProcessMMDfCopy[
        'timestamp client started processing the message containing global density data in milliseconds'] = \
        clientProcessMMDfCopy[
            'timestamp client started processing the message containing global density data in milliseconds'].astype(
            int)

    dfMetricSC = pd.merge(serverProduceMMDfCopy, clientProcessMMDfCopy, on='message identifier', how='inner')
    #dfMetricSC = serverProduceMMDfCopy.merge(clientProcessMMDfCopy, how='inner', on='message identifier')
    calculatedSC = dfMetricSC[
                       'timestamp client started processing the message containing global density data in milliseconds'] - \
                   dfMetricSC['timestamp server finished to generate the message containing global density data in milliseconds (before sending)']
    dfMetricSC.insert(3, 'time in transit (SC)', calculatedSC)

    return dfMetricSC
