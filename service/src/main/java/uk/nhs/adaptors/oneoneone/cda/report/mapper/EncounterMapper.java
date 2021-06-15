package uk.nhs.adaptors.oneoneone.cda.report.mapper;

import static java.util.Arrays.stream;

import static org.hl7.fhir.dstu3.model.Encounter.EncounterStatus.FINISHED;
import static org.hl7.fhir.dstu3.model.IdType.newRandomUuid;
import static org.hl7.fhir.dstu3.model.Narrative.NarrativeStatus.GENERATED;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.catalina.Session;
import org.hl7.fhir.dstu3.model.*;
import org.hl7.fhir.dstu3.model.Encounter.EncounterLocationComponent;
import org.hl7.fhir.dstu3.model.Encounter.EncounterParticipantComponent;
import org.springframework.stereotype.Component;

import lombok.AllArgsConstructor;
import uk.nhs.adaptors.oneoneone.cda.report.service.AppointmentService;
import uk.nhs.adaptors.oneoneone.cda.report.util.NodeUtil;
import uk.nhs.connect.iucds.cda.ucr.*;

@Component
@AllArgsConstructor
public class EncounterMapper {

    private static final String DIV_START = "<div>";

    private static final String DIV_END = "</div>";

    private final PeriodMapper periodMapper;

    private final ParticipantMapper participantMapper;

    private final InformantMapper informantMapper;

    private final DataEntererMapper dataEntererMapper;

    private final ServiceProviderMapper serviceProviderMapper;

    private final LocationMapper locationMapper;

    private final AppointmentService appointmentService;

    private final PatientMapper patientMapper;

    private final GroupMapper groupMapper;

    private final NodeUtil nodeUtil;

    public Encounter mapEncounter(POCDMT000002UK01ClinicalDocument1 clinicalDocument, List<PractitionerRole> practitionerRoles) {
        Encounter encounter = new Encounter();
        encounter.setIdElement(newRandomUuid());
        setIdentifier(encounter, clinicalDocument);
        encounter.setStatus(FINISHED);
        encounter.setLocation(getLocationComponents(clinicalDocument));
        encounter.setPeriod(getPeriod(clinicalDocument));
        setServiceProvider(encounter, clinicalDocument);
        setSubject(encounter, clinicalDocument);
        encounter.setParticipant(getEncounterParticipantComponents(clinicalDocument, practitionerRoles, encounter));
        setAppointment(encounter, clinicalDocument);
        setEncounterReasonAndType(encounter, clinicalDocument);
        return encounter;
    }

    private void setIdentifier(Encounter encounter, POCDMT000002UK01ClinicalDocument1 clinicalDocument) {
        if (clinicalDocument.isSetComponentOf()) {
            POCDMT000002UK01Component1 componentOf = clinicalDocument.getComponentOf();
            POCDMT000002UK01EncompassingEncounter ITKEncounter = componentOf.getEncompassingEncounter();
            II[] idArray = ITKEncounter.getIdArray();

            List<Identifier> ids = new ArrayList<>();

            for (II id : idArray) {
                if (id.isSetRoot()) {
                    Identifier destId = new Identifier();
                    destId.setId(id.getRoot());
                    destId.setValue(id.getExtension());

                    ids.add(destId);

                }
            }
            encounter.setIdentifier(ids);
        }
    }

    private List<EncounterLocationComponent> getLocationComponents(POCDMT000002UK01ClinicalDocument1 clinicalDocument1) {
        List<EncounterLocationComponent> locations = new ArrayList<>();
        if (clinicalDocument1.sizeOfRecordTargetArray() > 0) {
            locations = stream(clinicalDocument1.getRecordTargetArray())
                .filter(recordTarget -> recordTarget.getPatientRole().getProviderOrganization() != null)
                .map(recordTarget -> recordTarget.getPatientRole().getProviderOrganization())
                .map(locationMapper::mapOrganizationToLocationComponent)
                .collect(Collectors.toList());
        }
        return locations;
    }

    private Period getPeriod(POCDMT000002UK01ClinicalDocument1 clinicalDocument) {
        TS effectiveTime = clinicalDocument.getEffectiveTime();

        return periodMapper.mapPeriod(effectiveTime);
    }

    private void setServiceProvider(Encounter encounter, POCDMT000002UK01ClinicalDocument1 clinicalDocument1) {
        Organization serviceProviderOrganization = serviceProviderMapper.mapServiceProvider(clinicalDocument1.getCustodian());
        Reference serviceProvider = new Reference(serviceProviderOrganization);
        encounter.setServiceProvider(serviceProvider);
        encounter.setServiceProviderTarget(serviceProviderOrganization);
    }

    private void setSubject(Encounter encounter, POCDMT000002UK01ClinicalDocument1 clinicalDocument1) {
        Optional<Patient> patient = getPatient(clinicalDocument1);
        if (patient.isPresent()) {
            if (clinicalDocument1.sizeOfRecordTargetArray() == 1) {
                encounter.setSubject(new Reference(patient.get()));
                encounter.setSubjectTarget(patient.get());
            } else if (clinicalDocument1.sizeOfRecordTargetArray() > 1) {
                Group group = groupMapper.mapGroup(clinicalDocument1.getRecordTargetArray());
                encounter.setSubject(new Reference(group));
                encounter.setSubjectTarget(group);
            }
        }
    }

    private List<EncounterParticipantComponent> getEncounterParticipantComponents(POCDMT000002UK01ClinicalDocument1 clinicalDocument,
        List<PractitionerRole> practitionerRoles, Encounter encounter) {
        List<EncounterParticipantComponent> encounterParticipantComponents = stream(clinicalDocument
            .getParticipantArray())
            .map(participantMapper::mapEncounterParticipant)
            .collect(Collectors.toList());
        if (practitionerRoles.size() > 0) {
            practitionerRoles.stream()
                .map(it -> new EncounterParticipantComponent()
                    .setIndividual(it.getPractitioner())
                    .setIndividualTarget(it.getPractitionerTarget()))
                .forEach(encounterParticipantComponents::add);
        }
        if (clinicalDocument.sizeOfInformantArray() > 0) {
            stream(clinicalDocument.getInformantArray())
                .map(informantMapper::mapInformantIntoParticipantComponent)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .forEach(encounterParticipantComponents::add);

            for (POCDMT000002UK01Informant12 informant : clinicalDocument.getInformantArray()) {
                EncounterParticipantComponent encounterParticipantComponent =
                    participantMapper.mapEncounterRelatedPerson(informant, encounter);
                encounterParticipantComponents.add(encounterParticipantComponent);
            }
        }
        if (clinicalDocument.isSetDataEnterer()) {
            encounterParticipantComponents.add(dataEntererMapper
                .mapDataEntererIntoParticipantComponent(clinicalDocument.getDataEnterer()));
        }
        return encounterParticipantComponents;
    }

    private void setAppointment(Encounter encounter, POCDMT000002UK01ClinicalDocument1 clinicalDocument) {
        Reference patient = encounter.getSubject();
        appointmentService.retrieveAppointment(patient, clinicalDocument)
            .map(Reference::new).ifPresent(encounter::setAppointment);
    }

    private void setEncounterReasonAndType(Encounter encounter, POCDMT000002UK01ClinicalDocument1 clinicalDocument) {
        if (clinicalDocument.getComponent().isSetStructuredBody()) {
            for (POCDMT000002UK01Component3 component3 : clinicalDocument.getComponent().getStructuredBody().getComponentArray()) {
                POCDMT000002UK01Section section = component3.getSection();
                for (POCDMT000002UK01Entry entry : section.getEntryArray()) {
                    if (entry.isSetEncounter()) {
                        addEncounterText(entry.getEncounter(), encounter);
                    }
                }
            }
        }
    }

    private Optional<Patient> getPatient(POCDMT000002UK01ClinicalDocument1 clinicalDocument1) {
        Patient patient = new Patient();
        if (clinicalDocument1.sizeOfRecordTargetArray() > 0) {
            POCDMT000002UK01PatientRole patientRole = clinicalDocument1.getRecordTargetArray(0).getPatientRole();
            patient = patientMapper.mapPatient(patientRole);
        }
        return Optional.of(patient);
    }

    private void addEncounterText(POCDMT000002UK01Encounter encounterITK, Encounter encounter) {
        if (encounterITK.isSetText()) {
            String divString = nodeUtil.getNodeValueString(encounterITK.getText());
            Narrative narrative = new Narrative();
            narrative.setStatus(GENERATED);
            narrative.setDivAsString(Arrays.asList(DIV_START, divString, DIV_END)
                .stream().collect(Collectors.joining()));
            encounter.setText(narrative);
        }
    }
}
