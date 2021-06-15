package uk.nhs.adaptors.oneoneone.cda.report.validation;

import static java.util.Arrays.asList;

import static uk.nhs.adaptors.oneoneone.cda.report.controller.utils.ReportElement.DISTRIBUTION_ENVELOPE;
import static uk.nhs.adaptors.oneoneone.cda.report.controller.utils.ReportElement.ITK_HEADER;
import static uk.nhs.adaptors.oneoneone.cda.report.controller.utils.ReportElement.ITK_PAYLOADS;
import static uk.nhs.adaptors.oneoneone.cda.report.controller.utils.ReportElement.MESSAGE_ID;
import static uk.nhs.adaptors.oneoneone.cda.report.controller.utils.ReportElement.SOAP_HEADER;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.dom4j.Attribute;
import org.dom4j.Element;
import org.dom4j.Node;
import org.springframework.stereotype.Component;

import uk.nhs.adaptors.oneoneone.cda.report.controller.exceptions.SoapClientException;
import uk.nhs.adaptors.oneoneone.cda.report.controller.utils.ReportElement;

@Component
public class ItkValidator {
    private static final String SOAP_ACTION_XPATH = "//*[local-name()='Action']";
    private static final String ITK_MANIFEST_XPATH = "//*[local-name()='manifest']";
    private static final String ITK_AUDIT_IDENTITY_XPATH = "//*[local-name()='auditIdentity']";
    private static final String ITK_MANIFEST_ITEM_XPATH = "//*[local-name()='manifestitem']";
    private static final String ITK_PAYLOAD_XPATH = "//*[local-name()='payload']";
    private static final String SOAP_VALIDATION_FAILED_MSG = "Soap validation failed";
    private static final List<String> PROFILE_IDS = asList("urn:nhs-en:profile:nhs111CDADocument-v2-0",
        "urn:nhs-en:profile:nullificationDocument-v5-0", "urn:nhs-en:profile:IntegratedUrgentCareCDADocument-v3-1",
        "urn:nhs-en:profile:nhs111CDADocument-v3-1");
    private static final String AUDIT_IDENTITY_ID_XPATH = "//*[local-name()='id']";

    public void checkItkConformance(Map<ReportElement, Element> report) throws SoapClientException {
        checkMessageIdExists(report.get(MESSAGE_ID));
        checkDistributionEnvelopeExists(report.get(DISTRIBUTION_ENVELOPE));
        Element itkHeader = report.get(ITK_HEADER);
        checkTrackingIdExists(itkHeader);
        checkSoapAndItkService(report.get(SOAP_HEADER), itkHeader);
        checkPayloadAndManifest(itkHeader, report.get(ITK_PAYLOADS));
    }

    private void checkPayloadAndManifest(Element itkHeader, Element itkPayloads) throws SoapClientException {
        Element itkManifest = (Element) itkHeader.selectSingleNode(ITK_MANIFEST_XPATH);
        String manifestCount = itkManifest.attribute("count").getValue();
        String payloadCount = itkPayloads.attribute("count").getValue();

        if (!StringUtils.equals(manifestCount, payloadCount)) {
            throw new SoapClientException(SOAP_VALIDATION_FAILED_MSG, "Manifest count and payload count don't match");
        }

        List<Node> manifestItems = itkManifest.selectNodes(ITK_MANIFEST_ITEM_XPATH);
        checkManifestItemCount(manifestItems, manifestCount);
        List<Node> payloadItems = itkPayloads.selectNodes(ITK_PAYLOAD_XPATH);
        checkPayloadCount(payloadItems, payloadCount);
        checkPayloadsAndManifestsIds(manifestItems, payloadItems);
        checkManifestProfileId(manifestItems);
    }

    private void checkManifestProfileId(List<Node> manifestItems) throws SoapClientException {
        for (Node node : manifestItems) {
            Attribute profileId = ((Element) node).attribute("profileid");

            if (profileId == null) {
                throw new SoapClientException(SOAP_VALIDATION_FAILED_MSG, "Manifest profile Id missing");
            }

            if (!PROFILE_IDS.contains(profileId.getValue())) {
                throw new SoapClientException(SOAP_VALIDATION_FAILED_MSG, "Invalid manifest profile Id: " + profileId.getValue());
            }
        }
    }

    private void checkPayloadsAndManifestsIds(List<Node> manifestItems, List<Node> payloadItems) throws SoapClientException {
        for (int i = 0; i < manifestItems.size(); i++) {
            String manifestItemId = ((Element) manifestItems.get(i)).attribute("id").getValue();
            String payloadId = ((Element) payloadItems.get(i)).attribute("id").getValue();

            if (!StringUtils.equals(manifestItemId, payloadId)) {
                throw new SoapClientException(SOAP_VALIDATION_FAILED_MSG, "Manifest and payload items IDs don't match");
            }
        }
    }

    private void checkManifestItemCount(List<Node> manifestItems, String manifestCount) throws SoapClientException {
        if (!StringUtils.equals(String.valueOf(manifestItems.size()), manifestCount)) {
            throw new SoapClientException(SOAP_VALIDATION_FAILED_MSG, "Manifest count attribute and manifest items size don't match");
        }
    }

    private void checkPayloadCount(List<Node> payloadItems, String payloadCount) throws SoapClientException {

        if (!StringUtils.equals(String.valueOf(payloadItems.size()), payloadCount)) {
            throw new SoapClientException(SOAP_VALIDATION_FAILED_MSG, "Payload count attribute and payload items size don't match");
        }
    }

    private void checkSoapAndItkService(Element soapHeader, Element itkHeader) throws SoapClientException {
        Node actionNode = soapHeader.selectSingleNode(SOAP_ACTION_XPATH);
        if (actionNode == null) {
            throw new SoapClientException(SOAP_VALIDATION_FAILED_MSG, "Action node missing");
        }

        String soapAction = actionNode.getText();
        String itkService = itkHeader.attribute("service").getValue();

        if (!StringUtils.equals(soapAction, itkService)) {
            throw new SoapClientException(SOAP_VALIDATION_FAILED_MSG, "Soap Action is not equal to ITK service");
        }
    }

    private void checkTrackingIdExists(Element element) throws SoapClientException {
        Attribute trackingid = element.attribute("trackingid");
        if (trackingid == null) {
            throw new SoapClientException(SOAP_VALIDATION_FAILED_MSG, "Itk TrackingId missing");
        }
    }

    private void checkDistributionEnvelopeExists(Element element) throws SoapClientException {
        if (element == null) {
            throw new SoapClientException(SOAP_VALIDATION_FAILED_MSG, "DistributionEnvelope missing");
        }
    }

    private void checkMessageIdExists(Element element) throws SoapClientException {
        if (element == null) {
            throw new SoapClientException(SOAP_VALIDATION_FAILED_MSG, "MessageId missing");
        }
    }
}
