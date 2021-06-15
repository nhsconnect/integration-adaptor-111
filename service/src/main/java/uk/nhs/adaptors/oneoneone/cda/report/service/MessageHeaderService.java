package uk.nhs.adaptors.oneoneone.cda.report.service;

import static java.util.stream.Collectors.toList;

import static org.hl7.fhir.dstu3.model.IdType.newRandomUuid;

import java.util.Arrays;
import java.util.Date;

import org.hl7.fhir.dstu3.model.CodeableConcept;
import org.hl7.fhir.dstu3.model.Coding;
import org.hl7.fhir.dstu3.model.MessageHeader;
import org.hl7.fhir.dstu3.model.MessageHeader.MessageDestinationComponent;
import org.hl7.fhir.dstu3.model.MessageHeader.MessageSourceComponent;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import uk.nhs.adaptors.oneoneone.cda.report.controller.utils.ItkReportHeader;
import uk.nhs.adaptors.oneoneone.config.SoapProperties;
import uk.nhs.connect.iucds.cda.ucr.CE;

@Component
@RequiredArgsConstructor
public class MessageHeaderService {

    private static final String EVENT_SYSTEM = "https://fhir.nhs.uk/STU3/CodeSystem/ITK-MessageEvent-2";
    private static final String EVENT_CODE = "ITK007C";
    private static final String NUMSAS_EVENT_CODE = "ITK010D";
    private  static final String[] EMERGENCY_DISPOSITION_CODES = {"DX80","DX85","DX86","DX87","DX97","DX98"};
    private static final String EVENT_DISPLAY_VALUE = "ITK GP Connect Send Document";
    private static final String MESSAGE_SOURCE_NAME = "NHS 111 Adaptor";

    private final SoapProperties soapProperties;

    public MessageHeader createMessageHeader(ItkReportHeader itkHeader, CE dischargeDispositionCode) {
        MessageHeader header = new MessageHeader();

        header.setIdElement(newRandomUuid());
        header.setId(header.getIdElement());
        header.setEvent(getEvent(dischargeDispositionCode));
        header.setSource(getSource());
        header.setTimestamp(new Date());
        header.setReason(new CodeableConcept().addCoding(
            new Coding()
                .setCode(itkHeader.getSpecVal())
                .setSystem(itkHeader.getSpecKey())));
        header.setDestination(itkHeader.getAddressList().stream()
            .map(it -> new MessageDestinationComponent()
                .setEndpoint(it))
            .collect(toList()));

        return header;
    }

    private MessageSourceComponent getSource() {
        MessageSourceComponent source = new MessageSourceComponent();
        source.setName(MESSAGE_SOURCE_NAME);
        source.setEndpoint(soapProperties.getSendTo());

        return source;
    }

    private Coding getEvent(CE dischargeDispositionCode) {
        Coding coding = new Coding();
        String code = dischargeDispositionCode.getCode().trim().toUpperCase();

        if (Arrays.stream(EMERGENCY_DISPOSITION_CODES).anyMatch(code::equals)) {
            coding.setSystem(EVENT_SYSTEM)
                    .setCode(NUMSAS_EVENT_CODE)
                    .setDisplay(EVENT_DISPLAY_VALUE);
        }
        else {
            coding.setSystem(EVENT_SYSTEM)
                    .setCode(EVENT_CODE)
                    .setDisplay(EVENT_DISPLAY_VALUE);
        }

        return coding;
    }
}
