package tomocomd;

import java.io.Serial;

public class AExOpDCSException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;
    private final ExceptionType type;


    public AExOpDCSException(
            ExceptionType type,
            Throwable cause,
            String message) {
        super(type.formatMessage(message), cause);
        this.type = type;
    }

    public enum ExceptionType {
        CSV_FILE_WRITING_EXCEPTION("Error writing csv file"),
        CSV_FILE_LOADING_EXCEPTION("Error reading csv file");

        private final String message;

        ExceptionType(String message) {
            this.message = message;
        }

        public String formatMessage(String message) {
            return this.message + ": " + message;
        }

        public AExOpDCSException get(String message, Throwable cause) {
            return new AExOpDCSException(this, cause, message);
        }
    }
}
