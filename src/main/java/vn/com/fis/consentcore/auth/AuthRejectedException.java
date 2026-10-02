package vn.com.fis.consentcore.auth;

class AuthRejectedException extends RuntimeException {
    AuthRejectedException(String message) {
        super(message);
    }
}
