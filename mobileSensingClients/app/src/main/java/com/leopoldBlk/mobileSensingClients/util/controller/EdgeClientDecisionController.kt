package com.leopoldBlk.mobileSensingClients.util.controller

import android.util.Log
import com.leopoldBlk.mobileSensingClients.util.DataStore
import crowdDensityLibrary.dataClasses.PositionalBeacon
import java.nio.ByteBuffer

// Checked (final)
object EdgeClientDecisionController {
    private val className: String = "EdgeClientDecisionController"

    // Checked (final)
    public fun performEdgeClientDecision() {
        val isEdgeClient: Boolean = !neighborWithLargerTransmissionBenefitExists()
        DataStore.isEdgeClient.postValue(isEdgeClient)

        LogController.log(
            Log.INFO,
            className,
            "Performed decision if to take the role as edge client, result: $isEdgeClient"
        )
    }

    // Checked (final)
    private fun neighborWithLargerTransmissionBenefitExists(): Boolean {
        val clientTransmissionBenefit: Float? =
            DataStore.clientTransmissionBenefit.value

        val positionalBeaconFromNtWithLargestTransmissionBenefit: PositionalBeacon? =
            DataStore.measurementManager.getPositionalBeaconFromNtWithLargestTransmissionBenefit()

        // Check if client has largest transmission benefit value of nearby sensing clients
        // --> only makes sense, if the client itself has a value for transmission benefit
        if (!(clientTransmissionBenefit == null || clientTransmissionBenefit == 0.0f)) {
            val neighborWithLargerTransmissionBenefitExists: Boolean =
                if (positionalBeaconFromNtWithLargestTransmissionBenefit == null) {
                    false
                } else if (clientTransmissionBenefit == positionalBeaconFromNtWithLargestTransmissionBenefit.clientTransmissionBenefit) {
                    ByteBuffer.wrap(DataStore.clientId).getInt() < ByteBuffer.wrap(
                        positionalBeaconFromNtWithLargestTransmissionBenefit.clientIdHexString.hexToByteArray()
                    ).getInt()
                } else if (clientTransmissionBenefit < positionalBeaconFromNtWithLargestTransmissionBenefit.clientTransmissionBenefit) {
                    true
                } else {
                    false
                }

            return neighborWithLargerTransmissionBenefitExists
        }

        // If client has no value for transmission benefit or transmission benefit is 0 return true,
        // so it won't act as edge client
        return true
    }
}