package vn.com.fis.consentcore.extension.api;

public interface ConsentExtension {
    String name();
    void execute(ExtensionPoint point, ExtensionContext context) throws Exception;
}
