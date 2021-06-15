package uk.nhs.adaptors.oneoneone.cda.report.service;

import static java.util.stream.Collectors.toSet;

import static org.hl7.fhir.dstu3.model.Bundle.BundleType.MESSAGE;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.xmlbeans.XmlException;
import org.hl7.fhir.dstu3.model.*;
import org.springframework.stereotype.Component;

import lombok.AllArgsConstructor;
import uk.nhs.adaptors.oneoneone.cda.report.controller.utils.ItkReportHeader;
import uk.nhs.adaptors.oneoneone.cda.report.mapper.CarePlanMapper;
import uk.nhs.adaptors.oneoneone.cda.report.mapper.CompositionMapper;
import uk.nhs.adaptors.oneoneone.cda.report.mapper.ConditionMapper;
import uk.nhs.adaptors.oneoneone.cda.report.mapper.ConsentMapper;
import uk.nhs.adaptors.oneoneone.cda.report.mapper.EncounterMapper;
import uk.nhs.adaptors.oneoneone.cda.report.mapper.HealthcareServiceMapper;
import uk.nhs.adaptors.oneoneone.cda.report.mapper.ListMapper;
import uk.nhs.adaptors.oneoneone.cda.report.mapper.ObservationMapper;
import uk.nhs.adaptors.oneoneone.cda.report.mapper.PractitionerRoleMapper;
import uk.nhs.adaptors.oneoneone.cda.report.mapper.ReferralRequestMapper;
import uk.nhs.adaptors.oneoneone.cda.report.util.PathwayUtil;
import uk.nhs.connect.iucds.cda.ucr.CE;
import uk.nhs.connect.iucds.cda.ucr.POCDMT000002UK01ClinicalDocument1;

@Component
@AllArgsConstructor
public class EncounterReportBundleService {

    private static final String BUNDLE_IDENTIFIER_TYPE = "ClinicalDocument VersionNumber";
    private final EncounterMapper encounterMapper;
    private final CompositionMapper compositionMapper;
    private final ListMapper listMapper;
    private final CarePlanMapper carePlanMapper;
    private final ConsentMapper consentMapper;
    private final HealthcareServiceMapper healthcareServiceMapper;
    private final PathwayUtil pathwayUtil;
    private final MessageHeaderService messageHeaderService;
    private final ConditionMapper conditionMapper;
    private final ReferralRequestMapper referralRequestMapper;
    private final ObservationMapper observationMapper;
    private final PractitionerRoleMapper practitionerRoleMapper;

    private static void addEntry(Bundle bundle, Resource resource) {
        bundle.addEntry()
            .setFullUrl(resource.getIdElement().getValue())
            .setResource(resource);
    }

    public Bundle createEncounterBundle(POCDMT000002UK01ClinicalDocument1 clinicalDocument, ItkReportHeader header) throws XmlException {
        Bundle bundle = createBundle(clinicalDocument);

        List<HealthcareService> healthcareServiceList = healthcareServiceMapper.mapHealthcareService(clinicalDocument);
        List<PractitionerRole> authorPractitionerRoles = practitionerRoleMapper.mapAuthorRoles(clinicalDocument.getAuthorArray());
        List<PractitionerRole> practitionerRoles = new ArrayList<>(authorPractitionerRoles);
        practitionerRoleMapper.mapResponsibleParty(clinicalDocument).ifPresent(practitionerRoles::add);
        Encounter encounter = encounterMapper.mapEncounter(clinicalDocument, practitionerRoles);
        Consent consent = consentMapper.mapConsent(clinicalDocument, encounter);
        List<QuestionnaireResponse> questionnaireResponseList = pathwayUtil.getQuestionnaireResponses(clinicalDocument,
            encounter.getSubject(), new Reference(encounter));
        Condition condition = conditionMapper.mapCondition(clinicalDocument, encounter, questionnaireResponseList);
        List<CarePlan> carePlans = carePlanMapper.mapCarePlan(clinicalDocument, encounter, condition);
        ReferralRequest referralRequest = referralRequestMapper.mapReferralRequest(clinicalDocument,
            encounter, healthcareServiceList, new Reference(condition));
        Composition composition = compositionMapper.mapComposition(clinicalDocument, encounter, carePlans, questionnaireResponseList,
            referralRequest, authorPractitionerRoles);
        List<Observation> observations = observationMapper.mapObservations(clinicalDocument, encounter);

        CE dispositionCode = getDispositionCode(clinicalDocument);

        addEntry(bundle, messageHeaderService.createMessageHeader(header,dispositionCode ));
        addEncounter(bundle, encounter);
        addServiceProvider(bundle, encounter);
        addParticipants(bundle, encounter);
        addLocation(bundle, encounter);
        addSubject(bundle, encounter);
        addHealthcareService(bundle, healthcareServiceList);
        addIncomingReferral(bundle, referralRequest);
        addAppointment(bundle, encounter);
        addEntry(bundle, composition);
        addCarePlan(bundle, carePlans);
        addEntry(bundle, consent);
        addEntry(bundle, condition);
        addQuestionnaireResponses(bundle, questionnaireResponseList);
        addObservations(bundle, observations);
        addPractitionerRoles(bundle, practitionerRoles);

        ListResource listResource = getReferenceFromBundle(bundle, clinicalDocument, encounter);
        addEntry(bundle, listResource);

        return bundle;
    }
    private CE getDispositionCode(POCDMT000002UK01ClinicalDocument1 clinicalDocument) {
        CE dischargeCode = null;
        if (clinicalDocument.isSetComponentOf()) {
            if (clinicalDocument.getComponentOf().getEncompassingEncounter() != null) {
                if (clinicalDocument.getComponentOf().getEncompassingEncounter().isSetDischargeDispositionCode()) {
                    Coding coding = new Coding();
                    dischargeCode = clinicalDocument.getComponentOf().getEncompassingEncounter().getDischargeDispositionCode();
                }
            }
        }
        return dischargeCode;
    }
    private Bundle createBundle(POCDMT000002UK01ClinicalDocument1 clinicalDocument) {
        Bundle bundle = new Bundle();
        bundle.setType(MESSAGE);
        bundle.setIdentifier(new Identifier()
            .setType(new CodeableConcept().setText(BUNDLE_IDENTIFIER_TYPE))
            .setValue(clinicalDocument.getVersionNumber().getValue().toString()));
        return bundle;
    }

    private void addPractitionerRoles(Bundle bundle, List<PractitionerRole> authorPractitionerRoles) {
        authorPractitionerRoles.stream()
            .forEach(it -> {
                addEntry(bundle, it);
                addEntry(bundle, it.getOrganizationTarget());
            });
    }

    private ListResource getReferenceFromBundle(Bundle bundle, POCDMT000002UK01ClinicalDocument1 clinicalDocument, Encounter encounter) {
        Collection<Resource> resourcesCreated = bundle.getEntry().stream().map(it -> it.getResource()).collect(toSet());
        return listMapper.mapList(clinicalDocument, encounter, resourcesCreated);
    }

    private void addEncounter(Bundle bundle, Encounter encounter) {
        addEntry(bundle, encounter);
    }

    private void addServiceProvider(Bundle bundle, Encounter encounter) {
        addEntry(bundle, encounter.getServiceProviderTarget());
    }

    private void addParticipants(Bundle bundle, Encounter encounter) {
        List<Encounter.EncounterParticipantComponent> participantComponents = encounter.getParticipant();
        participantComponents.forEach(item -> addEntry(bundle, item.getIndividualTarget()));
    }

    private void addAppointment(Bundle bundle, Encounter encounter) {
        if (encounter.hasAppointment()) {
            Appointment appointment = encounter.getAppointmentTarget();
            addEntry(bundle, appointment);
            if (appointment.hasParticipant()) {
                for (Appointment.AppointmentParticipantComponent participant : appointment.getParticipant()) {
                    if (participant.hasActor()) {
                        Resource actorTarget = participant.getActorTarget();
                        if (!(actorTarget instanceof Patient)) {
                            addEntry(bundle, actorTarget);
                        }
                    }
                }
            }
        }
    }

    private void addLocation(Bundle bundle, Encounter encounter) {
        List<Encounter.EncounterLocationComponent> locationComponents = encounter.getLocation();
        for (Encounter.EncounterLocationComponent component : locationComponents) {
            if (component.hasLocation()) {
                Location location = component.getLocationTarget();
                addEntry(bundle, location);
                if (location.hasManagingOrganization()) {
                    addOrganization(bundle, location.getManagingOrganizationTarget());
                }
            }
        }
    }

    private void addOrganization(Bundle bundle, Organization organization) {
        addEntry(bundle, organization);
        if (organization.hasPartOf()) {
            addOrganization(bundle, organization.getPartOfTarget());
        }
    }

    private void addSubject(Bundle bundle, Encounter encounter) {
        if (encounter.getSubjectTarget() instanceof Patient) {
            Patient patient = (Patient) encounter.getSubjectTarget();
            addEntry(bundle, patient);

            if (patient.hasGeneralPractitioner()) {
                for (Reference gp : patient.getGeneralPractitioner()) {
                    Organization organization = (Organization) gp.getResource();
                    addEntry(bundle, organization);
                }
            }
        }
        if (encounter.getSubjectTarget() instanceof Group) {
            Group group = (Group) encounter.getSubjectTarget();
            addEntry(bundle, group);
            for (Group.GroupMemberComponent groupMemberComponent : group.getMember()) {
                bundle.addEntry()
                    .setFullUrl(groupMemberComponent.getIdElement().getValue())
                    .setResource(groupMemberComponent.getEntityTarget());
            }
        }
    }

    private void addIncomingReferral(Bundle bundle, ReferralRequest referralRequest) {
        addEntry(bundle, referralRequest);

        if (referralRequest.hasRequester()) {
            addEntry(bundle, referralRequest.getRequester().getOnBehalfOfTarget());
        }

        if (referralRequest.hasSupportingInfo()) {
            addEntry(bundle, (ProcedureRequest) referralRequest.getSupportingInfoFirstRep().getResource());
        }
    }

    private void addCarePlan(Bundle bundle, List<CarePlan> carePlans) {
        carePlans.stream().forEach(carePlan -> addEntry(bundle, carePlan));
    }

    private void addHealthcareService(Bundle bundle, List<HealthcareService> healthcareServiceList) {
        for (HealthcareService healthcareService : healthcareServiceList) {
            addEntry(bundle, healthcareService);
            if (healthcareService.hasLocation()) {
                addEntry(bundle, (Location) healthcareService.getLocationFirstRep().getResource());
            }
            if (healthcareService.hasProvidedBy()) {
                addEntry(bundle, healthcareService.getProvidedByTarget());
            }
        }
    }

    private void addQuestionnaireResponses(Bundle bundle, List<QuestionnaireResponse> questionnaireResponseList) {
        if (questionnaireResponseList != null) {
            if (questionnaireResponseList.size() > 0) {
                for (QuestionnaireResponse questionnaireResponse : questionnaireResponseList) {
                    addEntry(bundle, questionnaireResponse);
                    if (questionnaireResponse.hasQuestionnaire()) {
                        addEntry(bundle, (Questionnaire) questionnaireResponse.getQuestionnaire().getResource());
                    }
                }
            }
        }
    }

    private void addObservations(Bundle bundle, List<Observation> observations) {
        observations.forEach(observation -> addEntry(bundle, observation));
    }
}
