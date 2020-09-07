package uk.nhs.adaptors.oneoneone.cda.report.controller.utils;

import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

import uk.nhs.adaptors.oneoneone.cda.report.controller.exceptions.SoapClientException;

public class ReportParserUtil {

    private static final String MESSAGE_ID_NODE = "//*[local-name()='MessageID']";
    private static final String ADDRESS_NODE = "//*[local-name()='Address']";
    private static final String DISTRIBUTION_ENVELOPE_NODE = "//*[local-name()='DistributionEnvelope']";
    private static final String ITK_PAYLOADS_NODE = "//*[local-name()='payloads']";
    private static final String HEADER_NODE = "//*[local-name()='header']";
    private static final String SOAP_HEADER_NODE = "//*[local-name()='Header']";

    public static Map<ReportElement, Element> parseReportXml(String reportXml) throws DocumentException, SoapClientException {

        Map<ReportElement, Element> reportElementsMap = new HashMap<>();

        SAXReader reader = new SAXReader();
        Document document = reader.read(new StringReader(reportXml));

        reportElementsMap.put(ReportElement.MESSAGE_ID, getElement(document, MESSAGE_ID_NODE));
        reportElementsMap.put(ReportElement.ADDRESS, getElement(document, ADDRESS_NODE));
        reportElementsMap.put(ReportElement.DISTRIBUTION_ENVELOPE, getElement(document, DISTRIBUTION_ENVELOPE_NODE));
        reportElementsMap.put(ReportElement.ITK_PAYLOADS, getElement(document, ITK_PAYLOADS_NODE));
        reportElementsMap.put(ReportElement.ITK_HEADER, getElement(document, HEADER_NODE));
        reportElementsMap.put(ReportElement.SOAP_HEADER, getElement(document, SOAP_HEADER_NODE));
        return reportElementsMap;
    }

    private static Element getElement(Document document, String xpath) {
        return (Element) document.selectSingleNode(xpath);
    }
}
