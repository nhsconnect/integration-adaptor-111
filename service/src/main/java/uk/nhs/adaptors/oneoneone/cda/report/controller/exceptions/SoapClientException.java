package uk.nhs.adaptors.oneoneone.cda.report.controller.exceptions;

import lombok.Getter;

@Getter
public class SoapClientException extends Exception {
    private final String reason;

    public SoapClientException(String message, String reason) {
        super(message);
        this.reason = reason;
    }
}
