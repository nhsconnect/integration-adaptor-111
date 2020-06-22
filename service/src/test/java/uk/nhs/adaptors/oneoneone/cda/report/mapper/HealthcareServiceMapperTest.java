package uk.nhs.adaptors.oneoneone.cda.report.mapper;

import org.apache.xmlbeans.XmlException;
import org.hl7.fhir.dstu3.model.ContactPoint;
import org.hl7.fhir.dstu3.model.HealthcareService;
import org.hl7.fhir.dstu3.model.Location;
import org.hl7.fhir.dstu3.model.Reference;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.nhs.adaptors.oneoneone.cda.report.service.OrganizationService;
import uk.nhs.connect.iucds.cda.ucr.ClinicalDocumentDocument1;
import uk.nhs.connect.iucds.cda.ucr.POCDMT000002UK01InformationRecipient;
import uk.nhs.connect.iucds.cda.ucr.POCDMT000002UK01IntendedRecipient;

import java.io.IOException;
import java.net.URL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class HealthcareServiceMapperTest {

    @Mock
    private LocationMapper locationMapper;

    @Mock
    private OrganizationService organizationService;

    @Mock
    private ContactPointMapper contactPointMapper;

    @Mock
    private ContactPoint contactPoint;

    @InjectMocks
    private HealthcareServiceMapper healthcareServiceMapper;

    private ClinicalDocumentDocument1 clinicalDocument;

    private Reference organizationRef = new Reference("Organization/1");
    private Location locationRef = new Location();

    @Before
    public void setup() throws IOException, XmlException {
        URL resource = getClass().getResource("/xml/example-clinical-doc.xml");
        clinicalDocument = ClinicalDocumentDocument1.Factory.parse(resource);

        POCDMT000002UK01IntendedRecipient intendedRecipient = clinicalDocument.getClinicalDocument()
                .getInformationRecipientArray(0).getIntendedRecipient();
        when(locationMapper.mapRecipientToLocation(intendedRecipient))
                .thenReturn(locationRef);
        when(organizationService.createOrganization(intendedRecipient.getReceivedOrganization()))
                .thenReturn(organizationRef);
        when(contactPointMapper.mapContactPoint(any())).thenReturn(contactPoint);
    }

    @Test
    public void testTransform() {
        POCDMT000002UK01InformationRecipient recipient =
                clinicalDocument.getClinicalDocument().getInformationRecipientArray(0);

        HealthcareService healthcareService = healthcareServiceMapper
                .transformRecipient(recipient);

        verify(organizationService)
                .createOrganization(recipient.getIntendedRecipient().getReceivedOrganization());
        verify(locationMapper).mapRecipientToLocation(recipient.getIntendedRecipient());

        assertEquals("name", "Thames Medical Practice", healthcareService.getName());
        assertEquals("active", true, healthcareService.getActive());
        assertTrue("providedBy", organizationRef.equalsDeep(healthcareService.getProvidedBy()));
    }

}