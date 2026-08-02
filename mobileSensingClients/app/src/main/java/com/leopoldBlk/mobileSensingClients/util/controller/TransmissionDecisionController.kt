package com.leopoldBlk.mobileSensingClients.util.controller

import com.leopoldBlk.mobileSensingClients.util.DataStore
import crowdDensityLibrary.dataClasses.PositionalBeacon

object TransmissionDecisionController {

    public fun updateTransmissionDecision() {
        DataStore.clientHasLargestTransmissionBenefitOfNearbyClients.postValue(!neighborWithLargerTransmissionBenefitExists())
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
                positionalBeaconFromNtWithLargestTransmissionBenefit != null && clientTransmissionBenefit < positionalBeaconFromNtWithLargestTransmissionBenefit.clientTransmissionBenefit

            return neighborWithLargerTransmissionBenefitExists
        }

        // If client has no value for transmission benefit return true, so it won't send to and request data form the server
        return true
    }
}