package vn.com.fis.consentcore.audit.api;

public interface AuditWriter {
    void append(AuditRecord record);
}
