package uk.nhs.adaptors.oneoneone.cda.report.service;

import static org.hl7.fhir.dstu3.model.Bundle.BundleType.TRANSACTION;

import java.util.List;
import java.util.Optional;

import org.hl7.fhir.dstu3.model.Appointment;
import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.Encounter;
import org.hl7.fhir.dstu3.model.Organization;
import org.hl7.fhir.dstu3.model.Reference;
import org.springframework.stereotype.Component;

import lombok.AllArgsConstructor;
import uk.nhs.adaptors.oneoneone.cda.report.mapper.EncounterMapper;
import uk.nhs.adaptors.oneoneone.cda.report.mapper.ServiceProviderMapper;
import uk.nhs.connect.iucds.cda.ucr.POCDMT000002UK01ClinicalDocument1;

@Component
@AllArgsConstructor
public class EncounterReportBundleService {

    private EncounterMapper encounterMapper;

    private ServiceProviderMapper serviceProviderMapper;

    private AppointmentService appointmentService;

    public Bundle createEncounterBundle(POCDMT000002UK01ClinicalDocument1 clinicalDocument) {
        Bundle bundle = new Bundle();
        bundle.setType(TRANSACTION);

        Encounter encounter = encounterMapper.mapEncounter(clinicalDocument
            .getComponentOf()
            .getEncompassingEncounter());

        addEncounter(bundle, encounter);
        addServiceProvider(bundle, encounter);
        addParticipants(bundle, encounter);
        addAppointment(bundle, encounter, clinicalDocument);

        return bundle;
    }

    private void addEncounter(Bundle bundle, Encounter encounter) {
        bundle.addEntry()
            .setFullUrl(encounter.getIdElement().getValue())
            .setResource(encounter);
    }

    private void addServiceProvider(Bundle bundle, Encounter encounter) {
        Organization organization = serviceProviderMapper.mapServiceProvider();

        bundle.addEntry()
            .setFullUrl(organization.getIdElement().getValue())
            .setResource(organization);
        encounter.setServiceProvider(new Reference(organization));

    }

    private void addParticipants(Bundle bundle, Encounter encounter) {
        List<Encounter.EncounterParticipantComponent> participantComponents = encounter.getParticipant();
        for (Encounter.EncounterParticipantComponent participantComponent : participantComponents) {
            bundle.addEntry()
                .setFullUrl(participantComponent.getIndividualTarget().getIdElement().getValue())
                .setResource(participantComponent.getIndividualTarget());
        }
    }

    private void addAppointment(Bundle bundle, Encounter encounter, POCDMT000002UK01ClinicalDocument1 clinicalDocument) {
        //TODO 2020-06-05 referallRequest should not be null, but be taken from the fhitEncounter
        //TODO to be corrected after referallRequest is mapped: NIAD-279 IncommingReferal ?
        Reference referralRequest = null;
        Reference patient = encounter.getSubject();

        Optional<Appointment> appointment = appointmentService.retrieveAppointment(referralRequest, patient, clinicalDocument);
        if (appointment.isPresent()) {
            bundle.addEntry()
                .setFullUrl(appointment.get().getIdElement().getValue())
                .setResource(appointment.get());
            encounter.setAppointment(new Reference(appointment.get()));
            encounter.setAppointmentTarget(appointment.get());
        }
    }
}