package fr.familia.app.feature.trips.step

import fr.familia.app.core.model.StepType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StepAddKindTest {
    @Test fun aDestinationAsksNoType() = assertTrue(stepTypeOptions(StepAddKind.Destination, null).isEmpty())

    @Test fun aTransportChoosesItsWay() = assertEquals(TRANSPORT_TYPES, stepTypeOptions(StepAddKind.Transport, null))

    @Test fun anExistingStayAsksNoType() = assertTrue(stepTypeOptions(null, StepType.Lodging).isEmpty())

    @Test fun anExistingTrainStaysATransport() = assertEquals(TRANSPORT_TYPES, stepTypeOptions(null, StepType.Train))

    @Test fun anOldActivityCanBecomeADestination() = assertEquals(StepType.entries, stepTypeOptions(null, StepType.Activity))

    @Test fun withoutKindAllTypesAsBefore() = assertEquals(StepType.entries, stepTypeOptions(null, null))

    @Test fun presetTypes() {
        assertEquals(StepType.Lodging, presetStepType(StepAddKind.Destination))
        assertEquals(StepType.Car, presetStepType(StepAddKind.Transport))
        assertNull(presetStepType(null))
    }
}
