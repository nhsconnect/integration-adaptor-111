package uk.nhs.adaptors.oneoneone.cda.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Arrays;

import org.hl7.fhir.dstu3.model.Coding;
import org.hl7.fhir.dstu3.model.MessageHeader;
import org.hl7.fhir.dstu3.model.MessageHeader.MessageSourceComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import uk.nhs.adaptors.oneoneone.cda.report.controller.utils.ItkReportHeader;
import uk.nhs.adaptors.oneoneone.config.SoapProperties;

@ExtendWith(MockitoExtension.class)
public class MessageHeaderServiceTest {

    private static final String EVENT_SYSTEM = "https://fhir.nhs.uk/STU3/CodeSystem/ITK-MessageEvent-2";
    private static final String EVENT_CODE = "ITK007C";
    private static final String EVENT_DISPLAY_VALUE = "ITK GP Connect Send Document";
    private static final String MESSAGE_SOURCE_NAME = "NHS 111 Adaptor";
    private static final String ENDPOINT = "https://gp.endpoint.com";
    private static final String SPECIFICATION_KEY = "urn:nhs-itk:ns:201005:interaction";
    private static final String SPECIFICATION_VALUE = "urn:nhs-itk:interaction:primaryEmergencyDepartmentRecipientNHS111CDADocument-v2-0";
    private static final String ADDRESS = "the_address";

    @Mock
    private SoapProperties soapProperties;

    @InjectMocks
    private MessageHeaderService messageHeaderService;

    @BeforeEach
    public void setUp() {
        when(soapProperties.getSendTo()).thenReturn(ENDPOINT);
    }

    @Test
    public void shouldCreateMessageHeader() {
        ItkReportHeader itkReportHeader = new ItkReportHeader();
        itkReportHeader.setSpecKey(SPECIFICATION_KEY);
        itkReportHeader.setSpecVal(SPECIFICATION_VALUE);
        itkReportHeader.setAddressList(Arrays.asList(ADDRESS));
        MessageHeader messageHeader = messageHeaderService.createMessageHeader(itkReportHeader,null);

        assertThat(messageHeader.getId()).isNotEmpty();
        Coding event = messageHeader.getEvent();
        assertThat(event.getSystem()).isEqualTo(EVENT_SYSTEM);
        assertThat(event.getCode()).isEqualTo(EVENT_CODE);
        assertThat(event.getDisplay()).isEqualTo(EVENT_DISPLAY_VALUE);
        MessageSourceComponent source = messageHeader.getSource();
        assertThat(source.getName()).isEqualTo(MESSAGE_SOURCE_NAME);
        assertThat(source.getEndpoint()).isEqualTo(ENDPOINT);
        assertThat(messageHeader.getReason().getCodingFirstRep().getSystem()).isEqualTo(SPECIFICATION_KEY);
        assertThat(messageHeader.getReason().getCodingFirstRep().getCode()).isEqualTo(SPECIFICATION_VALUE);
        assertThat(messageHeader.getDestinationFirstRep().getEndpoint()).isEqualTo(ADDRESS);
    }
}
