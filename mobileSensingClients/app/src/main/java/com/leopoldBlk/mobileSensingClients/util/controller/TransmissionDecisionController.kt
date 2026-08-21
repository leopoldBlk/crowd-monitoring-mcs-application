package com.leopoldBlk.mobileSensingClients.util.controller

import com.leopoldBlk.mobileSensingClients.util.DataStore
import crowdDensityLibrary.dataClasses.PositionalBeacon
import java.nio.ByteBuffer

object TransmissionDecisionController {
    public fun updateTransmissionDecision() {
        val newValueForNeighborWithLargerTransmissionBenefitExists: Boolean =
            neighborWithLargerTransmissionBenefitExists()
        val oldValueForNeighborWithLargerTransmissionBenefitExists: Boolean =
            DataStore.clientHasLargestTransmissionBenefitOfNearbyClients.value!!.not()

        DataStore.clientHasLargestTransmissionBenefitOfNearbyClients.postValue(!newValueForNeighborWithLargerTransmissionBenefitExists)
        val timestampAfterUpdateOfTheEdgeClientRole: Long =
            if (DataStore.trustedTimeClient != null) {
                DataStore.trustedTimeClient!!.computeCurrentUnixEpochMillis()!!
            } else {
                System.currentTimeMillis()
            }
    }

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

        // TODO: Specify further
        // If client has no value for transmission benefit return true, so it won't send to and request data form the server
        return true
    }
}