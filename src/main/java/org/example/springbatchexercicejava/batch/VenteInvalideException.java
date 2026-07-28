package org.example.springbatchexercicejava.batch;

public class VenteInvalideException extends Exception {

    public VenteInvalideException() {
    }

    public VenteInvalideException(String message) {
        super(message);
    }

    public VenteInvalideException(String message, Throwable cause) {
        super(message, cause);
    }

    public VenteInvalideException(Throwable cause) {
        super(cause);
    }

    public VenteInvalideException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
